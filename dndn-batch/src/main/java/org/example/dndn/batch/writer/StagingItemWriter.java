package org.example.dndn.batch.writer;

import lombok.extern.slf4j.Slf4j;
import org.example.dndn.batch.model.WorkerApiRow;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Step3(dataFetchSlaveStep) 용 Writer — WorkerApiRow → tmp_worker_sync_stage INSERT.
 *
 * <p>ApiWorkerReader가 반환한 WorkerApiRow를 스테이징 테이블에 batchUpdate로 INSERT한다.
 * 운영 테이블(attendance_record, worker_document, worker)은 전혀 건드리지 않는다.</p>
 *
 * <p>TempTableTasklet(Step2)에서 TRUNCATE가 완료된 상태이므로 INSERT만 수행해도 안전하다.</p>
 *
 * <p>{@code @StepScope} Bean: 파티션마다 독립 인스턴스 → siteCode 스레드 안전.</p>
 */
@Slf4j
public class StagingItemWriter implements ItemWriter<WorkerApiRow> {

    private static final String INSERT_SQL =
            "INSERT INTO tmp_worker_sync_stage (site_code, worker_id, worker_name, raw_json) " +
            "VALUES (?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final String siteCode;

    public StagingItemWriter(JdbcTemplate jdbcTemplate, String siteCode) {
        this.jdbcTemplate = jdbcTemplate;
        this.siteCode = siteCode;
    }

    @Override
    public void write(Chunk<? extends WorkerApiRow> chunk) {
        List<? extends WorkerApiRow> items = chunk.getItems();

        jdbcTemplate.batchUpdate(INSERT_SQL, items, items.size(),
                (ps, row) -> {
                    ps.setString(1, row.getSiteCode());
                    ps.setString(2, row.getWorkerId());
                    ps.setString(3, row.getWorkerName());
                    ps.setString(4, row.getRawJson());
                });

        log.info("[StagingWriter] siteCode={} chunk {}건 스테이징 INSERT 완료",
                siteCode, items.size());
    }
}
