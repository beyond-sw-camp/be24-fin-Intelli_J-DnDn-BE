package org.example.dndn.batch.config;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersIncrementer;

/**
 * 배치 실행 시마다 syncDate를 당일 날짜로 갱신하는 JobParametersIncrementer.
 *
 * <p>RunIdIncrementer는 이전 실행의 JobParameters를 그대로 복사한 뒤 run.id만 증가시킨다.
 * 따라서 최초 실행 시 설정된 syncDate가 BATCH_JOB_PARAMS에 저장되어
 * 이후 실행에도 과거 날짜가 재사용되는 문제가 생긴다.</p>
 *
 * <p>이 Incrementer는 run.id 증가 + syncDate를 매 실행마다 {@link BatchRosterDates#today()}로 덮어써서
 * 로그와 파라미터가 항상 한국 기준 당일 날짜를 표시하도록 보장한다.</p>
 */
public class SyncDateJobParametersIncrementer implements JobParametersIncrementer {

    @Override
    public JobParameters getNext(JobParameters parameters) {
        JobParameters params = (parameters == null) ? new JobParameters() : parameters;

        long nextRunId = params.getLong("run.id", 0L) + 1;

        return new JobParametersBuilder(params)
                .addLong("run.id", nextRunId, true)
                .addString("syncDate", BatchRosterDates.today().toString(), true)
                .toJobParameters();
    }
}
