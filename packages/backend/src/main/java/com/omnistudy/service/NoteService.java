package com.omnistudy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.omnistudy.ai.AiRequest;
import com.omnistudy.ai.AiTask;
import com.omnistudy.ai.AiOutputTruncatedException;
import com.omnistudy.model.dto.NoteContentDto;
import com.omnistudy.model.dto.NoteGenerateRequest;
import com.omnistudy.model.dto.NoteResponse;
import com.omnistudy.model.dto.NoteUpsertRequest;
import com.omnistudy.model.dto.NoteWorkspaceResponse;
import com.omnistudy.model.dto.SubtitleCaptureRequest;
import com.omnistudy.model.entity.Note;
import com.omnistudy.model.entity.SubtitleChunk;
import com.omnistudy.model.entity.NoteSummaryChunk;
import com.omnistudy.model.entity.StudySession;
import com.omnistudy.model.entity.KnowledgePoint;
import com.omnistudy.model.entity.Question;
import com.omnistudy.model.prompt.PromptTemplates;
import com.omnistudy.repository.AttemptRepository;
import com.omnistudy.repository.AiJobRepository;
import com.omnistudy.repository.NoteRepository;
import com.omnistudy.repository.QuestionRepository;
import com.omnistudy.repository.SessionRepository;
import com.omnistudy.repository.SubtitleChunkRepository;
import com.omnistudy.repository.NoteSummaryChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.data.domain.PageRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteService {

    private final MeteredAiService meteredAiService;
    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final AttemptRepository attemptRepository;
    private final AiJobRepository aiJobRepository;
    private final NoteRepository noteRepository;
    private final SubtitleChunkRepository subtitleChunkRepository;
    private final NoteSummaryChunkRepository noteSummaryChunkRepository;
    private final SummaryStorageService summaryStorageService;
    private final KnowledgeService knowledgeService;
    private final RagService ragService;
    private final ObjectMapper objectMapper;

    /**
     * 异步生成笔记，返回 jobId。客户端通过轮询或 SSE 监听状态变化。
     */
    @Transactional
    public void prepareGeneration(NoteGenerateRequest request, UUID userId) {
        UUID sessionId = UUID.fromString(request.sessionId());
        StudySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.getUserId().equals(userId)) throw new IllegalArgumentException("无权访问");

        Note note = noteRepository.findBySessionId(sessionId).orElseGet(() -> Note.builder()
                .sessionId(sessionId)
                .userId(userId)
                .title(session.getVideoTitle() == null ? "课程笔记" : session.getVideoTitle())
                .studyMaterials(emptyMaterials())
                .createdAt(OffsetDateTime.now())
                .build());
        note.setStatus("generating");
        note.setUpdatedAt(OffsetDateTime.now());
        noteRepository.save(note);
    }

    public void generatePrepared(NoteGenerateRequest request, UUID userId, boolean finalize) {
        UUID sessionId = UUID.fromString(request.sessionId());
        StudySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问");
        }

        Note note = noteRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("笔记生成任务尚未初始化"));

        try {
            String topic = session.getVideoTitle() != null ? session.getVideoTitle() : "未命名课程";

            // 每次最多处理 200 条原始字幕，避免单次模型输入无限增长。
            NoteSummaryChunk newSummary = summarizePendingSubtitles(sessionId, userId);

            // 周期同步没有新增教学内容时不再重复请求模型。首次生成和视频结束
            // 仍允许使用已有摘要恢复/生成完整笔记。
            if (!finalize && note.getContentJson() != null
                    && (newSummary == null || !hasTeachingContent(newSummary.getSummaryJson()))) {
                note.setStatus("done");
                note.setUpdatedAt(OffsetDateTime.now());
                noteRepository.save(note);
                return;
            }

            String chunksText = finalize || note.getContentJson() == null
                    ? buildContextText(sessionId)
                    : buildIncrementalContext(note, newSummary);
            if (chunksText.isBlank()) {
                // 让本次异步任务有明确的完成时间，前端可以结束轮询并解释
                // 当前没有足够字幕，而不是统一误报为 AI 超时。
                note.setMarkdown("当前还没有捕获到可整理的字幕。请确认视频字幕已开启并播放一小段后重试。");
                note.setContentStatus("draft");
                note.setGeneratedAt(OffsetDateTime.now());
                note.setUpdatedAt(OffsetDateTime.now());
                note.setStatus("done");
                noteRepository.save(note);
                return;
            }

            String userPrompt = finalize || note.getContentJson() == null
                    ? PromptTemplates.buildNoteUserPrompt(topic, chunksText)
                    : PromptTemplates.buildIncrementalNoteUserPrompt(topic, chunksText);

            AiTask noteTask = finalize ? AiTask.NOTE_FINALIZE : AiTask.NOTE_CHUNK_SUMMARY;
            String raw;
            try {
                raw = meteredAiService.generateForOperation(userId, noteTask, AiRequest.text(
                        "你是一位教学笔记整理助手，擅长把课程内容压缩为精炼、可检索的结构化笔记。",
                        userPrompt), noteOperationKey(sessionId));
            } catch (AiOutputTruncatedException truncated) {
                log.warn("笔记输出达到 token 上限，使用紧凑格式自动重试一次");
                raw = generateCompactNote(userId, sessionId, noteTask, userPrompt);
            }

            log.debug("Note raw response: {}", raw);
            JsonNode node;
            try {
                node = objectMapper.readTree(stripFence(raw));
            } catch (JsonProcessingException firstParseError) {
                log.warn("笔记 JSON 不完整，使用紧凑格式自动重试一次: {}", firstParseError.getOriginalMessage());
                raw = generateCompactNote(userId, sessionId, noteTask, userPrompt);
                log.debug("Note compact retry raw response: {}", raw);
                node = objectMapper.readTree(stripFence(raw));
            }

            // 薄弱点只来自真实题目与作答记录，不接受模型推测的 myWeakPoints。
            applyRecordedWeakPoints(node, userId, sessionId);

            note.setContentJson(node);
            note.setTitle(node.path("topic").asText(topic));
            note.setMarkdown(toMarkdown(node, topic));
            note.setCourseName(node.path("courseName").asText(topic));
            note.setChapterName(joinText(node.path("chapters")));
            note.setSourceTitle(topic);
            note.setSourceUrl(session.getVideoUrl());
            note.setTags(node.path("tags").isArray() ? node.path("tags") : objectMapper.createArrayNode());
            note.setContentStatus(finalize ? "organized" : "organizing");
            note.setInbox(false);
            note.setStatus("done");
            note.setGeneratedAt(OffsetDateTime.now());
            note.setUpdatedAt(OffsetDateTime.now());
            if (finalize) {
                var materials = emptyMaterials();
                if (node.path("flashcards").isArray()) materials.set("flashcards", node.path("flashcards"));
                if (node.path("questions").isArray()) materials.set("questions", node.path("questions"));
                note.setStudyMaterials(materials);
                note.setNextReviewAt(OffsetDateTime.now().plusDays(1));
            }
            noteRepository.save(note);
            ragService.indexNote(note);
            syncKnowledgePoints(note, node.path("keyPoints"));
            if (finalize) syncReviewQuestions(note);
        } catch (Exception e) {
            log.error("生成笔记失败", e);
            if (e instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("生成笔记失败", e);
        }
    }

    @Transactional
    public void markGenerationFailed(UUID sessionId) {
        noteRepository.findBySessionId(sessionId).ifPresent(note -> {
            note.setStatus("failed");
            note.setUpdatedAt(OffsetDateTime.now());
            noteRepository.save(note);
        });
    }

    @Transactional
    public void markGenerationPending(UUID sessionId) {
        noteRepository.findBySessionId(sessionId).ifPresent(note -> {
            note.setStatus("generating");
            note.setUpdatedAt(OffsetDateTime.now());
            noteRepository.save(note);
        });
    }

    @Transactional
    public void captureSubtitles(UUID sessionId, SubtitleCaptureRequest request, UUID userId) {
        StudySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.getUserId().equals(userId)) throw new IllegalArgumentException("无权访问");
        if (request == null || request.chunks() == null) return;
        request.chunks().stream()
                .filter(c -> c != null && c.tStart() != null && c.text() != null && !c.text().isBlank())
                .filter(c -> !subtitleChunkRepository.existsCapturedChunk(sessionId, c.tStart(), c.text().trim()))
                .limit(200)
                .forEach(c -> subtitleChunkRepository.save(SubtitleChunk.builder()
                        .sessionId(sessionId).part(request.part() == null ? 1 : request.part())
                        .tStart(c.tStart()).tEnd(c.tEnd()).text(c.text().trim()).build()));
    }

    @Transactional
    public List<NoteWorkspaceResponse> list(UUID userId, String query, boolean inbox, boolean review) {
        List<Note> notes;
        if (review) notes = noteRepository.findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(userId, OffsetDateTime.now());
        else if (inbox) notes = noteRepository.findByUserIdAndInboxTrueOrderByUpdatedAtDesc(userId);
        else if (query != null && !query.isBlank()) notes = noteRepository.search(userId, query.trim());
        else notes = noteRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        return notes.stream().map(this::reconcileLegacyWeakPoints).map(this::workspaceResponse).toList();
    }

    @Transactional
    public NoteWorkspaceResponse get(UUID id, UUID userId) {
        return workspaceResponse(reconcileLegacyWeakPoints(owned(id, userId)));
    }

    @Transactional
    public NoteWorkspaceResponse create(NoteUpsertRequest request, UUID userId) {
        Note note = Note.builder()
                .userId(userId).status("done").createdAt(OffsetDateTime.now())
                .studyMaterials(emptyMaterials()).build();
        apply(note, request);
        note = noteRepository.save(note);
        ragService.indexNote(note);
        syncKnowledgePointsFromMarkdown(note);
        return workspaceResponse(note);
    }

    @Transactional
    public NoteWorkspaceResponse update(UUID id, NoteUpsertRequest request, UUID userId) {
        Note note = owned(id, userId);
        apply(note, request);
        note = noteRepository.save(note);
        ragService.indexNote(note);
        syncKnowledgePointsFromMarkdown(note);
        return workspaceResponse(note);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        noteRepository.delete(owned(id, userId));
    }

    @Transactional
    public NoteWorkspaceResponse generateStudyMaterial(UUID id, String kind, UUID userId) {
        Note note = owned(id, userId);
        String normalizedKind = "questions".equals(kind) ? "questions" : "flashcards";
        String shape = "flashcards".equals(normalizedKind)
                ? "{\"flashcards\":[{\"front\":\"问题\",\"back\":\"答案\"}]}"
                : "{\"questions\":[{\"knowledgePoint\":\"知识点\",\"question\":\"题目\",\"options\":[{\"id\":\"A\",\"text\":\"选项\"},{\"id\":\"B\",\"text\":\"选项\"}],\"correctOptionId\":\"A\",\"answer\":\"解析\",\"difficulty\":1}]}";
        String prompt = "根据下面笔记生成 3 条学习材料。只返回合法 JSON，不要 Markdown 代码围栏，格式严格为 " + shape
                + "\n\n标题：" + note.getTitle() + "\n正文：\n" + note.getMarkdown();
        try {
            JsonNode generated = objectMapper.readTree(stripFence(meteredAiService.generate(userId,
                    AiTask.STUDY_MATERIAL,
                    AiRequest.text("你是学习材料设计师，内容必须忠于笔记。练习题必须是可判分的二选一题，两个选项同层级且只有一个正确答案。", prompt))));
            var materials = note.getStudyMaterials() != null && note.getStudyMaterials().isObject()
                    ? (com.fasterxml.jackson.databind.node.ObjectNode) note.getStudyMaterials().deepCopy()
                    : emptyMaterials();
            materials.set(normalizedKind, generated.path(normalizedKind));
            note.setStudyMaterials(materials);
            note.setNextReviewAt(OffsetDateTime.now());
            note.setUpdatedAt(OffsetDateTime.now());
            noteRepository.save(note);
            syncKnowledgePointsFromMarkdown(note);
            if ("questions".equals(normalizedKind)) syncReviewQuestions(note);
            return workspaceResponse(note);
        } catch (Exception e) {
            throw new IllegalArgumentException("学习材料生成失败，请稍后重试", e);
        }
    }

    private Note owned(UUID id, UUID userId) {
        return noteRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("笔记不存在或无权访问"));
    }

    private void apply(Note note, NoteUpsertRequest r) {
        note.setTitle(blankDefault(r.title(), "未命名笔记"));
        note.setMarkdown(r.markdown() == null ? "" : r.markdown());
        note.setCourseName(r.courseName());
        note.setChapterName(r.chapterName());
        note.setSourceTitle(r.sourceTitle());
        note.setSourceUrl(r.sourceUrl());
        note.setSourceTimestamp(r.sourceTimestamp());
        note.setTags(objectMapper.valueToTree(r.tags() == null ? Collections.emptyList() : r.tags()));
        note.setLinkedNoteIds(r.linkedNoteIds() == null ? new java.util.ArrayList<>() : r.linkedNoteIds().stream()
                .filter(v -> v != null && !v.isBlank()).map(UUID::fromString).distinct().toList());
        note.setContentStatus(allowed(r.contentStatus(), List.of("draft", "organizing", "organized"), "draft"));
        note.setMasteryStatus(allowed(r.masteryStatus(), List.of("unlearned", "learning", "practicing", "mastered", "review"), "unlearned"));
        note.setInbox(Boolean.TRUE.equals(r.inbox()));
        if (r.nextReviewAt() != null && !r.nextReviewAt().isBlank()) note.setNextReviewAt(OffsetDateTime.parse(r.nextReviewAt()));
        if ("mastered".equals(note.getMasteryStatus()) && note.getNextReviewAt() == null) note.setNextReviewAt(OffsetDateTime.now().plusDays(7));
        note.setUpdatedAt(OffsetDateTime.now());
        if (note.getStudyMaterials() == null) note.setStudyMaterials(emptyMaterials());
    }

    private NoteWorkspaceResponse workspaceResponse(Note n) {
        List<String> tags = n.getTags() == null || !n.getTags().isArray() ? List.of()
                : objectMapper.convertValue(n.getTags(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        return new NoteWorkspaceResponse(n.getId().toString(), n.getSessionId() == null ? null : n.getSessionId().toString(),
                n.getTitle(), n.getMarkdown(), n.getCourseName(), n.getChapterName(), n.getSourceTitle(), n.getSourceUrl(),
                n.getSourceTimestamp(), tags, n.getLinkedNoteIds() == null ? List.of() : n.getLinkedNoteIds().stream().map(UUID::toString).toList(),
                n.getContentStatus(), n.getMasteryStatus(), Boolean.TRUE.equals(n.getInbox()),
                n.getNextReviewAt() == null ? null : n.getNextReviewAt().toString(),
                n.getStudyMaterials() == null ? emptyMaterials() : n.getStudyMaterials(),
                n.getCreatedAt() == null ? null : n.getCreatedAt().toString(), n.getUpdatedAt() == null ? null : n.getUpdatedAt().toString());
    }

    private com.fasterxml.jackson.databind.node.ObjectNode emptyMaterials() {
        var node = objectMapper.createObjectNode();
        node.putArray("flashcards"); node.putArray("questions");
        return node;
    }

    private String toMarkdown(JsonNode n, String fallbackTitle) {
        StringBuilder md = new StringBuilder("# ").append(n.path("topic").asText(fallbackTitle)).append("\n\n");
        if (!n.path("summary").asText().isBlank()) md.append(n.path("summary").asText()).append("\n\n");
        if (n.path("sections").isArray()) {
            n.path("sections").forEach(section -> {
                md.append("## ").append(section.path("title").asText("课程内容"));
                if (!section.path("time").asText().isBlank()) md.append(" · ").append(section.path("time").asText());
                md.append("\n");
                if (!section.path("summary").asText().isBlank()) md.append(section.path("summary").asText()).append("\n");
                section.path("keyPoints").forEach(v -> md.append("- ").append(v.asText()).append("\n"));
                md.append("\n");
            });
        }
        appendList(md, "关键概念", n.path("keyPoints"));
        appendList(md, "公式", n.path("formulas"));
        appendList(md, "代码引用", n.path("codeReferences"));
        appendList(md, "我的薄弱点", n.path("myWeakPoints"));
        return md.toString().trim();
    }

    private void appendList(StringBuilder md, String title, JsonNode values) {
        if (!values.isArray() || values.isEmpty()) return;
        md.append("## ").append(title).append("\n");
        values.forEach(v -> md.append("- ").append(v.asText()).append("\n"));
        md.append("\n");
    }

    private String generateCompactNote(UUID userId, UUID sessionId, AiTask noteTask, String userPrompt) {
        String retryPrompt = userPrompt + """

                上一次输出因过长而产生了不完整 JSON。请重新输出一份更紧凑但完整的 JSON：
                - summary 不超过 300 字；sections 最多 8 个，每个 summary 不超过 120 字；
                - keyPoints 最多 12 个；formulas、codeReferences 各最多 6 个；
                - flashcards 和 questions 各最多 3 个；
                - 所有字符串必须正确转义，必须闭合全部引号、数组和对象；
                - 只返回一个合法 JSON 对象，不要代码围栏或额外说明。
                """;
        return meteredAiService.generateForOperation(userId, noteTask, AiRequest.text(
                "你是教学笔记整理助手。输出必须是简洁、完整、可解析的 JSON 对象。",
                retryPrompt), noteOperationKey(sessionId));
    }

    private void syncKnowledgePoints(Note note, JsonNode values) {
        if (values == null || !values.isArray()) return;
        values.forEach(value -> {
            String name = value.asText("").trim();
            if (!name.isBlank()) knowledgeService.upsert(note.getUserId(), name, note.getId());
        });
    }

    private void applyRecordedWeakPoints(JsonNode node, UUID userId, UUID sessionId) {
        if (!(node instanceof com.fasterxml.jackson.databind.node.ObjectNode object)) {
            throw new IllegalArgumentException("笔记模型必须返回 JSON 对象");
        }
        var weakPoints = object.putArray("myWeakPoints");
        knowledgeService.weakPointsForSession(userId, sessionId).stream()
                .map(KnowledgePoint::getName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .forEach(weakPoints::add);
        object.put("weakPointsSource", "attempts");
    }

    private Note reconcileLegacyWeakPoints(Note note) {
        if (note.getSessionId() == null || note.getContentJson() == null
                || "attempts".equals(note.getContentJson().path("weakPointsSource").asText())) {
            return note;
        }
        JsonNode refreshed = note.getContentJson().deepCopy();
        applyRecordedWeakPoints(refreshed, note.getUserId(), note.getSessionId());
        note.setContentJson(refreshed);
        note.setMarkdown(toMarkdown(refreshed, note.getTitle()));
        note.setUpdatedAt(OffsetDateTime.now());
        Note saved = noteRepository.save(note);
        ragService.indexNote(saved);
        return saved;
    }

    /** 每次作答后立即用同一套答题记录刷新笔记中的薄弱点。 */
    @Transactional
    public void refreshRecordedWeakPoints(UUID sessionId, UUID userId) {
        if (sessionId == null) return;
        noteRepository.findBySessionId(sessionId).ifPresent(note -> {
            if (!note.getUserId().equals(userId) || note.getContentJson() == null) return;
            JsonNode refreshed = note.getContentJson().deepCopy();
            applyRecordedWeakPoints(refreshed, userId, sessionId);
            note.setContentJson(refreshed);
            note.setMarkdown(toMarkdown(refreshed, note.getTitle()));
            note.setUpdatedAt(OffsetDateTime.now());
            noteRepository.save(note);
            ragService.indexNote(note);
        });
    }

    /** 手工笔记也能进入知识图谱：只读取“关键概念/知识点”标题下的列表。 */
    private void syncKnowledgePointsFromMarkdown(Note note) {
        boolean inKnowledgeSection = false;
        for (String line : blankDefault(note.getMarkdown(), "").split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                String heading = trimmed.replaceFirst("^#+\\s*", "");
                inKnowledgeSection = heading.contains("关键概念") || heading.contains("知识点");
                continue;
            }
            if (inKnowledgeSection && trimmed.matches("^[-*]\\s+.+")) {
                knowledgeService.upsert(note.getUserId(), trimmed.replaceFirst("^[-*]\\s+", ""), note.getId());
            }
        }
    }

    /** 将笔记 JSON 中的练习材料注册为统一 Question，返回的 id 会写回材料供前端作答。 */
    private void syncReviewQuestions(Note note) {
        JsonNode questions = note.getStudyMaterials() == null ? null : note.getStudyMaterials().path("questions");
        if (questions == null || !questions.isArray()) return;
        var registered = objectMapper.createArrayNode();
        questions.forEach(rawQuestion -> {
            String text = rawQuestion.path("question").asText("").trim();
            String correctOptionId = rawQuestion.path("correctOptionId").asText("");
            JsonNode options = rawQuestion.path("options");
            if (text.isBlank() || correctOptionId.isBlank() || !options.isArray() || options.size() != 2) return;

            String pointName = rawQuestion.path("knowledgePoint").asText("").trim();
            if (pointName.isBlank()) pointName = firstKeyPoint(note);
            KnowledgePoint point = knowledgeService.upsert(note.getUserId(), pointName.isBlank() ? note.getTitle() : pointName, note.getId());

            var prompt = objectMapper.createObjectNode();
            prompt.put("coreConcept", point.getName());
            prompt.put("question", text);
            prompt.put("questionType", "practice");
            prompt.put("correctOptionId", correctOptionId);
            prompt.put("explanation", rawQuestion.path("answer").asText("请回顾对应知识点。"));
            prompt.set("options", options.deepCopy());
            Question saved = questionRepository.save(Question.builder()
                    .userId(note.getUserId()).sessionId(note.getSessionId()).noteId(note.getId())
                    .knowledgePointId(point.getId()).origin("note_review").t(0f).part(1)
                    .difficulty(Math.max(1, rawQuestion.path("difficulty").asInt(1)))
                    .promptJson(prompt).build());

            var material = rawQuestion.deepCopy();
            if (material instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
                object.put("id", saved.getId().toString());
                object.put("knowledgePointId", point.getId().toString());
                registered.add(object);
            }
        });
        if (note.getStudyMaterials() instanceof com.fasterxml.jackson.databind.node.ObjectNode materials) {
            materials.set("questions", registered);
            noteRepository.save(note);
        }
    }

    private String firstKeyPoint(Note note) {
        JsonNode values = note.getContentJson() == null ? null : note.getContentJson().path("keyPoints");
        return values != null && values.isArray() && !values.isEmpty() ? values.get(0).asText("") : "";
    }

    private String blankDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private String allowed(String value, List<String> allowed, String fallback) { return allowed.contains(value) ? value : fallback; }
    private String stripFence(String value) { return value.replaceFirst("(?s)^\\s*```(?:json)?\\s*", "").replaceFirst("(?s)\\s*```\\s*$", "").trim(); }
    private String joinText(JsonNode values) {
        if (!values.isArray()) return null;
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .map(JsonNode::asText).filter(v -> !v.isBlank()).limit(5).collect(java.util.stream.Collectors.joining(" / "));
    }

    @Transactional
    public NoteResponse getBySession(String sessionId, UUID userId) {
        UUID parsedSessionId = UUID.fromString(sessionId);
        StudySession session = sessionRepository.findById(parsedSessionId).orElseThrow();
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问");
        }
        Note note = noteRepository.findBySessionId(parsedSessionId).map(this::reconcileLegacyWeakPoints).orElse(null);
        if (note == null) {
            return new NoteResponse(null, sessionId, null, null, "generating", "draft", null);
        }

        NoteContentDto content = null;
        if (note.getContentJson() != null) {
            content = objectMapper.convertValue(note.getContentJson(), NoteContentDto.class);
        }
        String jobError = "failed".equals(note.getStatus())
                ? aiJobRepository.findFirstByUserIdAndOperationKeyOrderByCreatedAtDesc(
                        userId, "NOTE_GENERATION:" + sessionId)
                    .map(job -> truncateJobError(job.getLastError()))
                    .orElse("AI 笔记任务执行失败，请重试")
                : null;
        return new NoteResponse(
                note.getId().toString(),
                sessionId,
                content,
                note.getGeneratedAt() != null ? note.getGeneratedAt().toString() : null,
                note.getStatus(),
                note.getContentStatus(),
                jobError
        );
    }

    private String truncateJobError(String value) {
        if (value == null || value.isBlank()) return "AI 笔记任务执行失败，请重试";
        String singleLine = value.replaceAll("[\\r\\n]+", " ").trim();
        return singleLine.length() <= 500 ? singleLine : singleLine.substring(0, 500);
    }

    private String buildContextText(UUID sessionId) {
        var summaries = noteSummaryChunkRepository.listForSession(sessionId);
        var questions = questionRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        var chunks = new StringBuilder();
        if (!summaries.isEmpty()) {
            chunks.append("# 阶段摘要（不是原始字幕）\n");
            int remaining = 48_000; // 字符级硬上限，避免最终提示词无限增长。
            for (var summary : summaries) {
                String line = "[第%d集 %s-%s] %s\n".formatted(summary.getPart(), formatTime(summary.getTStart()),
                        formatTime(summary.getTEnd()), summary.getSummaryJson().toString());
                if (line.length() > remaining) break;
                chunks.append(line);
                remaining -= line.length();
            }
            chunks.append("\n# 学习问答记录\n");
        }
        for (var q : questions) {
            if (chunks.length() >= 56_000) break;
            chunks.append("## 题目：").append(q.getPromptJson().path("question").asText()).append("\n");
            var attempts = attemptRepository.findByQuestionIdOrderByAttemptNumAsc(q.getId());
            for (var a : attempts) {
                chunks.append("学生回答：").append(a.getAnswerText()).append("\n");
                chunks.append("评估：").append(a.getDecided()).append(" - ")
                        .append(a.getEvaluationJson().path("feedback").asText()).append("\n\n");
            }
        }
        return chunks.toString();
    }

    private String buildIncrementalContext(Note note, NoteSummaryChunk summary) {
        String previous = note.getContentJson() == null ? "{}" : note.getContentJson().toString();
        if (previous.length() > 24_000) previous = previous.substring(0, 24_000);
        String window = summary == null ? "{}" : summary.getSummaryJson().toString();
        return "# 已有笔记 JSON\n" + previous
                + "\n\n# 本次新增摘要\n[第" + (summary == null ? 1 : summary.getPart()) + "集 "
                + (summary == null ? "0:00" : formatTime(summary.getTStart())) + "-"
                + (summary == null ? "0:00" : formatTime(summary.getTEnd())) + "] " + window;
    }

    private NoteSummaryChunk summarizePendingSubtitles(UUID sessionId, UUID userId) throws Exception {
        List<SubtitleChunk> raw = subtitleChunkRepository.findBySessionIdOrderByTStartAsc(sessionId, PageRequest.of(0, 200));
        if (raw.isEmpty()) return null;

        // 约 12k 字符一批；更大的待处理队列会留到下一轮，既限 Token 又保留重试能力。
        var selected = new java.util.ArrayList<SubtitleChunk>();
        var transcript = new StringBuilder();
        int part = raw.get(0).getPart();
        for (var subtitle : raw) {
            if (subtitle.getPart() != part) break;
            String line = "[%s] %s\n".formatted(formatTime(subtitle.getTStart()), subtitle.getText());
            if (!selected.isEmpty() && transcript.length() + line.length() > 12_000) break;
            transcript.append(line);
            selected.add(subtitle);
        }
        if (selected.isEmpty()) return null;

        String hash = sha256(sessionId + ":" + part + ":" + transcript);
        if (noteSummaryChunkRepository.findBySessionIdAndContentHash(sessionId, hash).isPresent()) {
            subtitleChunkRepository.deleteAllByIdInBatch(selected.stream().map(SubtitleChunk::getId).toList());
            return null;
        }

        String rawSummary = meteredAiService.generateForOperation(userId, AiTask.NOTE_CHUNK_SUMMARY, AiRequest.text(
                "你是课程增量摘要助手。只总结输入的新字幕块，忠于原文并输出严格 JSON。",
                PromptTemplates.buildChunkSummaryPrompt(transcript.toString())), noteOperationKey(sessionId));
        JsonNode summaryJson = objectMapper.readTree(stripFence(rawSummary));
        NoteSummaryChunk summary = NoteSummaryChunk.builder()
                .sessionId(sessionId).part(part)
                .tStart(selected.get(0).getTStart()).tEnd(selected.get(selected.size() - 1).getTEnd())
                .contentHash(hash).summaryJson(summaryJson).build();
        summaryStorageService.saveSummaryAndDeleteRaw(summary, selected.stream().map(SubtitleChunk::getId).toList());
        ragService.indexSummary(summary);
        return summary;
    }

    private boolean hasTeachingContent(JsonNode summary) {
        if (summary == null || summary.isMissingNode() || summary.isNull()) return false;
        if (!summary.path("summary").asText("").isBlank()) return true;
        return !summary.path("keyPoints").isEmpty()
                || !summary.path("formulas").isEmpty()
                || !summary.path("codeReferences").isEmpty();
    }

    private String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private String formatTime(Float seconds) {
        int total = Math.max(0, Math.round(seconds == null ? 0 : seconds));
        return "%d:%02d".formatted(total / 60, total % 60);
    }

    private String noteOperationKey(UUID sessionId) {
        return "NOTE_SESSION:" + sessionId;
    }
}
