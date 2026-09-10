package com.omnistudy.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record NoteWorkspaceResponse(
        String id, String sessionId, String title, String markdown,
        String courseName, String chapterName, String sourceTitle, String sourceUrl,
        Integer sourceTimestamp, List<String> tags, List<String> linkedNoteIds,
        String contentStatus, String masteryStatus, boolean inbox,
        String nextReviewAt, JsonNode studyMaterials, String createdAt, String updatedAt
) {}
