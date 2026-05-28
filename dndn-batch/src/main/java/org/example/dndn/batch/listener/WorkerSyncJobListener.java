package org.example.dndn.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class WorkerSyncJobListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("[배치 시작] job={} params={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        long ms = (jobExecution.getEndTime() != null)
                ? Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis()
                : -1;
        log.info("[배치 종료] status={} duration={}ms", jobExecution.getStatus(), ms);
        if (jobExecution.getStatus().isUnsuccessful()) {
            jobExecution.getAllFailureExceptions()
                    .forEach(e -> log.error("[배치 실패] {}", e.getMessage()));
        }
    }
}
