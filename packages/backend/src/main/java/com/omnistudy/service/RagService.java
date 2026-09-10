package com.omnistudy.service;

import com.omnistudy.model.entity.Note;
import com.omnistudy.model.entity.NoteSummaryChunk;
import com.omnistudy.model.entity.RetrievalChunk;
import com.omnistudy.repository.NoteRepository;
import com.omnistudy.repository.RetrievalChunkRepository;
import com.omnistudy.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RagService {
    private static final int EMBEDDING_DIMENSIONS = 384;
    private final RetrievalChunkRepository repository;
    private final NoteRepository noteRepository;
    private final SessionRepository sessionRepository;

    @Transactional
    public void indexNote(Note note) {
        if (note.getId() == null || note.getUserId() == null) return;
        repository.deleteByNoteId(note.getId());
        List<String> chunks = chunkMarkdown(note.getMarkdown());
        for (int i = 0; i < chunks.size(); i++) {
            String content = chunks.get(i);
            save(RetrievalChunk.builder()
                    .userId(note.getUserId()).sessionId(note.getSessionId()).noteId(note.getId())
                    .contentType("note").title(note.getTitle() + (chunks.size() > 1 ? " · " + (i + 1) : ""))
                    .content(content).contentHash(hash(note.getId() + ":" + i + ":" + content))
                    .embedding(embed(content)).build());
        }
    }

    @Transactional
    public void indexSummary(NoteSummaryChunk summary) {
        sessionRepository.findById(summary.getSessionId()).ifPresent(session -> {
            String content = summary.getSummaryJson().toString();
            save(RetrievalChunk.builder()
                    .userId(session.getUserId()).sessionId(summary.getSessionId()).part(summary.getPart())
                    .startTime(asDouble(summary.getTStart())).endTime(asDouble(summary.getTEnd()))
                    .contentType("subtitle_summary")
                    .title("%s · 第%d集 %s".formatted(
                            value(session.getVideoTitle(), "课程摘要"), summary.getPart(), formatTime(summary.getTStart())))
                    .content(content).contentHash(hash("summary:" + summary.getContentHash()))
                    .embedding(embed(content)).build());
        });
    }

    /** PostgreSQL lexical candidates and portable semantic vectors are fused with reciprocal rank fusion. */
    @Transactional
    public List<RagHit> search(UUID userId, UUID sessionId, String query, int limit) {
        ensureIndexed(userId);
        int safeLimit = Math.max(1, Math.min(limit, 10));
        List<RetrievalChunk> lexical = query == null || query.isBlank() ? List.of()
                : repository.lexicalCandidates(userId, sessionId, query.trim(), PageRequest.of(0, 40));
        Float[] queryVector = embed(value(query, "学习内容"));
        List<RetrievalChunk> semantic = new ArrayList<>(repository.candidates(userId, sessionId, PageRequest.of(0, 400)));
        semantic.sort(Comparator.comparingDouble((RetrievalChunk c) -> cosine(queryVector, c.getEmbedding())).reversed());

        Map<UUID, Double> score = new HashMap<>();
        Map<UUID, RetrievalChunk> chunks = new HashMap<>();
        addRanks(lexical, score, chunks, 1.2);
        addRanks(semantic.stream().limit(40).toList(), score, chunks, 1.0);
        return score.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(safeLimit)
                .map(entry -> toHit(chunks.get(entry.getKey()), entry.getValue()))
                .toList();
    }

    private void addRanks(List<RetrievalChunk> ranked, Map<UUID, Double> scores,
                          Map<UUID, RetrievalChunk> chunks, double weight) {
        for (int i = 0; i < ranked.size(); i++) {
            RetrievalChunk chunk = ranked.get(i);
            chunks.put(chunk.getId(), chunk);
            scores.merge(chunk.getId(), weight / (60.0 + i + 1), Double::sum);
        }
    }

    private RagHit toHit(RetrievalChunk chunk, double score) {
        return new RagHit(chunk.getId(), chunk.getTitle(), truncate(chunk.getContent(), 700),
                chunk.getContentType(), chunk.getSessionId(), chunk.getNoteId(), chunk.getPart(),
                chunk.getStartTime(), chunk.getEndTime(), score);
    }

    private void ensureIndexed(UUID userId) {
        if (repository.countByUserId(userId) > 0) return;
        noteRepository.findByUserIdOrderByUpdatedAtDesc(userId).forEach(this::indexNote);
    }

    private void save(RetrievalChunk chunk) {
        if (repository.findByUserIdAndContentHash(chunk.getUserId(), chunk.getContentHash()).isEmpty()) {
            repository.save(chunk);
        }
    }

    private List<String> chunkMarkdown(String markdown) {
        String text = value(markdown, "").trim();
        if (text.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : text.split("(?=\\n#{1,3}\\s)|\\n{2,}")) {
            if (!current.isEmpty() && current.length() + block.length() > 1200) {
                result.add(current.toString().trim()); current.setLength(0);
            }
            current.append(block).append("\n");
        }
        if (!current.isEmpty()) result.add(current.toString().trim());
        return result;
    }

    /** Deterministic character n-gram hashing keeps offline/dev retrieval functional without an embedding API. */
    Float[] embed(String input) {
        String normalized = value(input, "").toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        Float[] vector = new Float[EMBEDDING_DIMENSIONS];
        Arrays.fill(vector, 0f);
        for (int i = 0; i < normalized.length(); i++) {
            for (int width = 1; width <= 3 && i + width <= normalized.length(); width++) {
                String gram = normalized.substring(i, i + width);
                int hash = gram.hashCode();
                int index = Math.floorMod(hash, EMBEDDING_DIMENSIONS);
                vector[index] += (hash & 1) == 0 ? 1f : -1f;
            }
        }
        double norm = Math.sqrt(Arrays.stream(vector).mapToDouble(v -> v * v).sum());
        if (norm > 0) for (int i = 0; i < vector.length; i++) vector[i] = (float) (vector[i] / norm);
        return vector;
    }

    private double cosine(Float[] left, Float[] right) {
        if (left == null || right == null || left.length != right.length) return 0;
        double value = 0;
        for (int i = 0; i < left.length; i++) value += left[i] * right[i];
        return value;
    }

    private String hash(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private Double asDouble(Float value) { return value == null ? null : value.doubleValue(); }
    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max) + "…"; }
    private String formatTime(Float value) { int s = Math.max(0, Math.round(value == null ? 0 : value)); return "%d:%02d".formatted(s / 60, s % 60); }

    public record RagHit(UUID id, String title, String content, String contentType, UUID sessionId,
                         UUID noteId, Integer part, Double startTime, Double endTime, double score) {}
}
