package com.omnistudy.service;

import com.omnistudy.model.entity.NoteSummaryChunk;
import com.omnistudy.repository.NoteSummaryChunkRepository;
import com.omnistudy.repository.SubtitleChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SummaryStorageService {
    private final NoteSummaryChunkRepository summaryRepository;
    private final SubtitleChunkRepository subtitleRepository;

    /** 摘要落库与原文删除必须原子完成，保存失败时原始字幕仍可重试。 */
    @Transactional
    public void saveSummaryAndDeleteRaw(NoteSummaryChunk summary, List<UUID> rawIds) {
        if (summaryRepository.findBySessionIdAndContentHash(summary.getSessionId(), summary.getContentHash()).isEmpty()) {
            summaryRepository.save(summary);
        }
        subtitleRepository.deleteAllByIdInBatch(rawIds);
    }
}
