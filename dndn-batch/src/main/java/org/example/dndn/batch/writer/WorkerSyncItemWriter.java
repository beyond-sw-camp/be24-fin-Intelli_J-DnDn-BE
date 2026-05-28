package org.example.dndn.batch.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.dndn.batch.model.WorkerApiRow;
import org.example.dndn.batch.service.WorkerSyncService;
import org.example.dndn.domain.worker.fixture.WorkerScenarioFixtureRow;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Step6(workerSyncSlaveStep) 용 chunk Writer — 스테이징 → 운영 테이블 반영.
 *
 * <p>StagingTableReader가 50건을 반환하면 Spring Batch가 이 Writer를 호출한다.
 * WorkerApiRow.rawJson을 WorkerScenarioFixtureRow로 역직렬화한 후
 * WorkerSyncService.syncChunk()에 위임한다.</p>
 *
 * <p><b>실제 API 전환 시</b>: rawJson 형식이 변경되면 역직렬화 대상 타입(WorkerScenarioFixtureRow)
 * 또는 변환 로직만 수정하면 된다. syncChunk() 시그니처는 그대로 유지된다.</p>
 *
 * <p>{@code @StepScope} Bean: siteCode(파티션별)·syncDate를 주입받는다.</p>
 */
@Slf4j
public class WorkerSyncItemWriter implements ItemWriter<WorkerApiRow> {

    private final WorkerSyncService workerSyncService;
    private final ObjectMapper objectMapper;
    private final String siteCode;
    private final LocalDate syncDate;

    public WorkerSyncItemWriter(WorkerSyncService workerSyncService,
                                ObjectMapper objectMapper,
                                String siteCode,
                                LocalDate syncDate) {
        this.workerSyncService = workerSyncService;
        this.objectMapper = objectMapper;
        this.siteCode = siteCode;
        this.syncDate = syncDate;
    }

    @Override
    public void write(Chunk<? extends WorkerApiRow> chunk) throws Exception {
        List<? extends WorkerApiRow> apiRows = chunk.getItems();
        log.info("[Writer] siteCode={} chunk {}명 처리 시작 (rawJson 역직렬화 후 운영 반영)",
                siteCode, apiRows.size());

        // WorkerApiRow.rawJson → WorkerScenarioFixtureRow 역직렬화
        List<WorkerScenarioFixtureRow> fixtureRows = new ArrayList<>(apiRows.size());
        for (WorkerApiRow apiRow : apiRows) {
            try {
                WorkerScenarioFixtureRow fixtureRow =
                        objectMapper.readValue(apiRow.getRawJson(), WorkerScenarioFixtureRow.class);
                fixtureRows.add(fixtureRow);
            } catch (Exception e) {
                log.error("[Writer] rawJson 역직렬화 실패: siteCode={}, workerId={}",
                        siteCode, apiRow.getWorkerId(), e);
                throw new RuntimeException(
                        "rawJson 역직렬화 실패 (siteCode=" + siteCode +
                        ", workerId=" + apiRow.getWorkerId() + ")", e);
            }
        }

        WorkerSyncService.SyncResult result =
                workerSyncService.syncChunk(siteCode, fixtureRows, syncDate);

        log.info("[Writer] siteCode={} chunk {}명 완료 (created={} updated={})",
                siteCode, apiRows.size(), result.created(), result.updated());
    }
}
