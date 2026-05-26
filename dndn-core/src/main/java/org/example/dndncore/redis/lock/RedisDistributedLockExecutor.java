package org.example.dndncore.redis.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDistributedLockExecutor {

    private final RedissonClient redissonClient;

    public boolean execute(
            String lockKey,
            long waitTimeSeconds,
            long leaseTimeSeconds,
            Runnable task
    ) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;

        try {
            locked = lock.tryLock(waitTimeSeconds, leaseTimeSeconds, TimeUnit.SECONDS);
            if (!locked) {
                long ttl = lock.remainTimeToLive();
                log.info("[Redis 분산락] 락 획득 실패 - key={}, ttlMs={}", lockKey, ttl);
                return false;
            }

            log.info("[Redis 분산락] 락 획득 성공 - key={}, leaseTimeSeconds={}", lockKey, leaseTimeSeconds);
            task.run();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Redis 분산락] 락 대기 중 인터럽트 발생 - key={}", lockKey);
            return false;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[Redis 분산락] 락 해제 - key={}", lockKey);
            }
        }
    }
}
