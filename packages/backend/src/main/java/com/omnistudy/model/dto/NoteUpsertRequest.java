package com.omnistudy.model.dto;

import java.util.List;

public record NoteUpsertRequest(
        String title, String markdown, String courseName, String chapterName,
        String sourceTitle, String sourceUrl, Integer sourceTimestamp,
        List<String> tags, List<String> linkedNoteIds,
        String contentStatus, String masteryStatus, Boolean inbox, String nextReviewAt
) {}
