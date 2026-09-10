package com.omnistudy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnistudy.model.dto.NoteGenerateRequest;
import com.omnistudy.model.entity.Note;
import com.omnistudy.model.entity.AiJob;
import com.omnistudy.model.entity.KnowledgePoint;
import com.omnistudy.model.entity.NoteSummaryChunk;
import com.omnistudy.model.entity.StudySession;
import com.omnistudy.ai.AiTask;
import com.omnistudy.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NoteServiceCostTest {
    private final MeteredAiService ai = mock(MeteredAiService.class);
    private final SessionRepository sessions = mock(SessionRepository.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final AttemptRepository attempts = mock(AttemptRepository.class);
    private final AiJobRepository aiJobs = mock(AiJobRepository.class);
    private final NoteRepository notes = mock(NoteRepository.class);
    private final SubtitleChunkRepository subtitles = mock(SubtitleChunkRepository.class);
    private final NoteSummaryChunkRepository summaries = mock(NoteSummaryChunkRepository.class);
    private final SummaryStorageService summaryStorage = mock(SummaryStorageService.class);
    private final KnowledgeService knowledge = mock(KnowledgeService.class);
    private final RagService rag = mock(RagService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NoteService service = new NoteService(ai, sessions, questions, attempts, aiJobs, notes,
            subtitles, summaries, summaryStorage, knowledge, rag, objectMapper);

    @Test
    void periodicSyncSkipsModelWhenThereIsNoNewSubtitleContent() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudySession session = StudySession.builder().id(sessionId).userId(userId).build();
        Note note = Note.builder().sessionId(sessionId).userId(userId)
                .contentJson(objectMapper.createObjectNode().put("topic", "existing"))
                .status("generating").build();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(notes.findBySessionId(sessionId)).thenReturn(Optional.of(note));
        when(subtitles.findBySessionIdOrderByTStartAsc(eq(sessionId), any(Pageable.class)))
                .thenReturn(List.of());

        service.generatePrepared(new NoteGenerateRequest(sessionId.toString()), userId, false);

        verifyNoInteractions(ai);
        verify(notes).save(note);
        assertEquals("done", note.getStatus());
    }

    @Test
    void sessionStatusExposesLatestJobFailure() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudySession session = StudySession.builder().id(sessionId).userId(userId).build();
        Note note = Note.builder().id(UUID.randomUUID()).sessionId(sessionId).userId(userId)
                .status("failed").build();
        AiJob job = AiJob.builder().lastError("上游模型拒绝请求\n请检查模型名称").build();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(notes.findBySessionId(sessionId)).thenReturn(Optional.of(note));
        when(aiJobs.findFirstByUserIdAndOperationKeyOrderByCreatedAtDesc(
                userId, "NOTE_GENERATION:" + sessionId)).thenReturn(Optional.of(job));

        var response = service.getBySession(sessionId.toString(), userId);

        assertEquals("failed", response.status());
        assertEquals("上游模型拒绝请求 请检查模型名称", response.error());
    }

    @Test
    void generatedNoteReplacesModelWeaknessGuessWithAttemptBackedWeakPoints() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudySession session = StudySession.builder().id(sessionId).userId(userId).videoTitle("操作系统").build();
        Note note = Note.builder().sessionId(sessionId).userId(userId).status("generating").build();
        NoteSummaryChunk summary = NoteSummaryChunk.builder().sessionId(sessionId).part(1)
                .tStart(0f).tEnd(60f).summaryJson(objectMapper.createObjectNode().put("summary", "分页存储"))
                .build();
        KnowledgePoint recordedWeakPoint = KnowledgePoint.builder().name("页表地址转换").build();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(notes.findBySessionId(sessionId)).thenReturn(Optional.of(note));
        when(subtitles.findBySessionIdOrderByTStartAsc(eq(sessionId), any(Pageable.class))).thenReturn(List.of());
        when(summaries.listForSession(sessionId)).thenReturn(List.of(summary));
        when(knowledge.weakPointsForSession(userId, sessionId)).thenReturn(List.of(recordedWeakPoint));
        when(ai.generateForOperation(eq(userId), eq(AiTask.NOTE_FINALIZE), any(), anyString()))
                .thenReturn("{\"topic\":\"操作系统\",\"summary\":\"总结\",\"keyPoints\":[],\"myWeakPoints\":[\"模型猜测\"]}");

        service.generatePrepared(new NoteGenerateRequest(sessionId.toString()), userId, true);

        assertEquals("页表地址转换", note.getContentJson().path("myWeakPoints").get(0).asText());
        assertEquals(1, note.getContentJson().path("myWeakPoints").size());
        assertEquals("attempts", note.getContentJson().path("weakPointsSource").asText());
        org.assertj.core.api.Assertions.assertThat(note.getMarkdown()).contains("页表地址转换").doesNotContain("模型猜测");
    }
}
