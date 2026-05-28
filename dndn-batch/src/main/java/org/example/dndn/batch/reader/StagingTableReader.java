package org.example.dndn.batch.reader;

import lombok.extern.slf4j.Slf4j;
import org.example.dndn.batch.model.WorkerApiRow;
import org.springframework.batch.item.ItemReader;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Iterator;
import java.util.List;

/**
 * Step6(workerSyncSlaveStep) 용 Reader — tmp_worker_sync_stage SELECT.
 *
 * <p>스테이징 테이블에서 현장(siteCode) 별 데이터를 전체 로드한 후 Row를 1건씩 반환한다.
 * chunk(50) 설정으로 50건이 쌓이면 WorkerSyncItemWriter가 호출된다.</p>
 *
 * <p>StagingValidationTasklet(Step4)에서 품질 검증이 완료된 데이터만 존재하므로
 * 추가 방어 로직 없이 읽어온다.</p>
 *
 * <p>{@code @StepScope} Bean: 파티션마다 독립 인스턴스 → synchronized 불필요.</p>
 */
@Slf4j
public class StagingTableReader implements ItemReader<WorkerApiRow> {

    private static final String SELECT_SQL =
            "SELECT site_code, worker_id, worker_name, raw_json " +
            "FROM tmp_worker_sync_stage WHERE site_code = ?";

    private final JdbcTemplate jdbcTemplate;
    private final String siteCode;

    private Iterator<WorkerApiRow> iterator;

    public StagingTableReader(JdbcTemplate jdbcTemplate, String siteCode) {
        this.jdbcTemplate = jdbcTemplate;
        this.siteCode = siteCode;
    }

    @Override
    public WorkerApiRow read() {
        if (iterator == null) {
            List<WorkerApiRow> rows = jdbcTemplate.query(SELECT_SQL,
                    (rs, rowNum) -> new WorkerApiRow(
                            rs.getString("site_code"),
                            rs.getString("worker_id"),
                            rs.getString("worker_name"),
                            rs.getString("raw_json")
                    ),
                    siteCode);

            int chunks = rows.isEmpty() ? 0 : (rows.size() + 49) / 50;
            log.info("[StagingReader] siteCode={} 스테이징 데이터 {}건 로드 ({}청크 예정)",
                    siteCode, rows.size(), chunks);
            iterator = rows.iterator();
        }

        return iterator.hasNext() ? iterator.next() : null;
    }
}
