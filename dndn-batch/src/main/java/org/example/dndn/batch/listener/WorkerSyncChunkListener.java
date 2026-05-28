package org.example.dndn.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WorkerSyncChunkListener implements ChunkListener {

    // 멀티스레드: @Component는 싱글톤 → 스레드별 독립 타이머가 필요
    private final ThreadLocal<Long> chunkStartMs = ThreadLocal.withInitial(() -> 0L);

    @Override
    public void beforeChunk(ChunkContext context) {
        chunkStartMs.set(System.currentTimeMillis());
        long chunkNum = context.getStepContext().getStepExecution().getCommitCount() + 1;
        log.info("[Chunk {}] 시작 thread={}", chunkNum, Thread.currentThread().getName());
    }

    @Override
    public void afterChunk(ChunkContext context) {
        long elapsed = System.currentTimeMillis() - chunkStartMs.get();
        chunkStartMs.remove();
        long chunkNum = context.getStepContext().getStepExecution().getCommitCount();
        log.info("[Chunk {}] 완료 duration={}ms thread={}", chunkNum, elapsed, Thread.currentThread().getName());
    }

    @Override
    public void afterChunkError(ChunkContext context) {
        long elapsed = System.currentTimeMillis() - chunkStartMs.get();
        chunkStartMs.remove();
        long chunkNum = context.getStepContext().getStepExecution().getCommitCount() + 1;
        Throwable t = (Throwable) context.getAttribute(ChunkListener.ROLLBACK_EXCEPTION_KEY);
        log.error("[Chunk {}] 오류 발생 duration={}ms thread={} error={}",
                chunkNum, elapsed, Thread.currentThread().getName(),
                t != null ? t.getMessage() : "unknown", t);
    }
}
