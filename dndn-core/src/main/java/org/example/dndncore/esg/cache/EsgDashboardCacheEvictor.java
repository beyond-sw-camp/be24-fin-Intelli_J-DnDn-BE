package org.example.dndncore.esg.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.redis.cache.RedisCacheNames;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EsgDashboardCacheEvictor {

    private static final String CACHE_KEY_PREFIX = RedisCacheNames.ESG_DASHBOARD + "::";

    private final RedissonClient redissonClient;

    public long evictProjectDate(Long projectId, LocalDate reportDate) {
        if (projectId == null || reportDate == null) {
            return 0L;
        }

        long deletedCount = 0L;
        deletedCount += deleteByPattern(projectDatePattern(projectId, reportDate));
        deletedCount += deleteByPattern(autoProjectDatePattern(reportDate));

        if (deletedCount > 0) {
            log.info(
                    "[ESG 캐시 무효화] 현장/일자 캐시 제거 - projectId={}, reportDate={}, deleted={}",
                    projectId,
                    reportDate,
                    deletedCount
            );
        }

        return deletedCount;
    }

    public long evictDate(LocalDate reportDate) {
        if (reportDate == null) {
            return 0L;
        }

        long deletedCount = deleteByPattern(datePattern(reportDate));
        if (deletedCount > 0) {
            log.info(
                    "[ESG 캐시 무효화] 일자 전체 캐시 제거 - reportDate={}, deleted={}",
                    reportDate,
                    deletedCount
            );
        }

        return deletedCount;
    }

    private String projectDatePattern(Long projectId, LocalDate reportDate) {
        return CACHE_KEY_PREFIX
                + "user:*:project:" + projectId
                + ":date:" + reportDate;
    }

    private String autoProjectDatePattern(LocalDate reportDate) {
        return CACHE_KEY_PREFIX
                + "user:*:project:auto"
                + ":date:" + reportDate;
    }

    private String datePattern(LocalDate reportDate) {
        return CACHE_KEY_PREFIX
                + "user:*:project:*"
                + ":date:" + reportDate;
    }

    private long deleteByPattern(String pattern) {
        try {
            List<String> keys = new ArrayList<>();
            redissonClient.getKeys()
                    .getKeysByPattern(pattern)
                    .forEach(keys::add);

            if (keys.isEmpty()) {
                return 0L;
            }

            return redissonClient.getKeys().delete(keys.toArray(String[]::new));
        } catch (Exception exception) {
            log.warn("[ESG 캐시 무효화] Redis 키 제거 실패 - pattern={}", pattern, exception);
            return 0L;
        }
    }
}
