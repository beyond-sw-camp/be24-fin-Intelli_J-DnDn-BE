package org.example.dndn.batch.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.dndn.batch.model.WorkerApiRow;
import org.example.dndn.domain.worker.fixture.WorkerFixtureGenerator;
import org.example.dndn.domain.worker.fixture.WorkerScenarioFixtureRow;
import org.springframework.batch.item.ItemReader;

import java.util.Iterator;
import java.util.List;

/**
 * Step3(dataFetchSlaveStep) 용 Reader — API 호출 → WorkerApiRow 변환.
 *
 * <p><b>현재 구현</b>: WorkerFixtureGenerator를 사용해 픽스처를 생성하고,
 * 각 WorkerScenarioFixtureRow를 JSON으로 직렬화해 WorkerApiRow.rawJson에 저장한다.
 * 이 rawJson이 스테이징 테이블에 적재되고, Step6에서 역직렬화되어 운영 테이블에 반영된다.</p>
 *
 * <p><b>실제 API 전환 시</b>: 이 클래스의 {@code initRows()} 구현만 교체하면 된다.
 * 외부 API 응답 JSON을 그대로 rawJson에 저장하고, WorkerSyncItemWriter의
 * 역직렬화 로직을 새 스키마에 맞게 수정한다.</p>
 *
 * <p>{@code @StepScope} Bean: 파티션마다 독립 인스턴스 → synchronized 불필요.</p>
 */
@Slf4j
public class ApiWorkerReader implements ItemReader<WorkerApiRow> {

    private final String siteCode;
    private final WorkerFixtureGenerator workerFixtureGenerator;
    private final ObjectMapper objectMapper;

    private Iterator<WorkerApiRow> iterator;

    public ApiWorkerReader(String siteCode,
                           WorkerFixtureGenerator workerFixtureGenerator,
                           ObjectMapper objectMapper) {
        this.siteCode = siteCode;
        this.workerFixtureGenerator = workerFixtureGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkerApiRow read() throws Exception {
        if (iterator == null) {
            iterator = initRows().iterator();
        }
        return iterator.hasNext() ? iterator.next() : null;
    }

    /**
     * 픽스처에서 데이터를 생성해 WorkerApiRow 리스트로 변환.
     *
     * <p>실제 API 전환 시 이 메서드를 HTTP 클라이언트 호출로 교체한다.</p>
     */
    private List<WorkerApiRow> initRows() {
        List<WorkerScenarioFixtureRow> fixtures = workerFixtureGenerator.generate(siteCode);
        List<WorkerApiRow> rows = fixtures.stream()
                .map(this::toApiRow)
                .toList();

        int chunks = rows.isEmpty() ? 0 : (rows.size() + 49) / 50;
        log.info("[ApiReader] siteCode={} 픽스처 {}명 로드 → 스테이징 변환 완료 ({}청크 예정)",
                siteCode, rows.size(), chunks);
        return rows;
    }

    /** WorkerScenarioFixtureRow → WorkerApiRow (JSON 직렬화 포함) */
    private WorkerApiRow toApiRow(WorkerScenarioFixtureRow row) {
        try {
            String json = objectMapper.writeValueAsString(row);
            return new WorkerApiRow(
                    row.getSiteCode(),
                    row.getExternalCode(),
                    row.getName(),
                    json
            );
        } catch (Exception e) {
            log.error("[ApiReader] JSON 직렬화 실패: siteCode={}, workerId={}",
                    siteCode, row.getExternalCode(), e);
            throw new RuntimeException(
                    "WorkerScenarioFixtureRow JSON 직렬화 실패 (workerId=" + row.getExternalCode() + ")", e);
        }
    }
}
