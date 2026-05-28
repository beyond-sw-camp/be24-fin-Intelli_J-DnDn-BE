package org.example.dndn.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WorkerSyncStepListener implements StepExecutionListener {

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("[Step 시작] step={} jobParams={}",
                stepExecution.getStepName(),
                stepExecution.getJobParameters());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info("[Step 완료] step={} status={} read={} write={} skip={} commit={} rollback={} duration={}ms",
                stepExecution.getStepName(),
                stepExecution.getStatus(),
                stepExecution.getReadCount(),
                stepExecution.getWriteCount(),
                stepExecution.getSkipCount(),
                stepExecution.getCommitCount(),
                stepExecution.getRollbackCount(),
                java.time.Duration.between(stepExecution.getStartTime(), stepExecution.getLastUpdated()).toMillis());

        if (!stepExecution.getFailureExceptions().isEmpty()) {
            stepExecution.getFailureExceptions()
                    .forEach(e -> log.error("[Step 실패 원인] {}", e.getMessage(), e));
        }

        return stepExecution.getExitStatus();
    }
}
