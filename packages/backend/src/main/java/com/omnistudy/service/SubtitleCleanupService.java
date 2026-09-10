package com.omnistudy.service;

import com.omnistudy.repository.SubtitleChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.maintenance", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SubtitleCleanupService {
    private final SubtitleChunkRepository subtitleChunkRepository;

    /** 原始字幕只是失败重试缓冲；即使客户端永久离线，也最多保留24小时。 */
    @Scheduled(cron = "0 20 * * * *")
    @Transactional
    public void purgeStaleRawSubtitles() {
        int deleted = subtitleChunkRepository.deleteOlderThan(OffsetDateTime.now().minusHours(24));
        if (deleted > 0) log.info("清理过期临时字幕 {} 条", deleted);
    }
}
