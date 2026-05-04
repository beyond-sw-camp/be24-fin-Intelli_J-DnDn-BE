package org.example.dndn.worker.fixture;

import org.example.dndn.common.exception.BaseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.example.dndn.common.model.BaseResponseStatus.FAIL;

@Component
@RequiredArgsConstructor
public class WorkerScenarioFixtureLoader {

    private final ObjectMapper objectMapper;

    /** 예: classpath:data/workforce-sync-demo.json */
    @Value("classpath:data/workforce-sync-demo.json")
    private Resource demoWorkforceFile;

    public List<WorkerScenarioFixtureRow> loadWorkersFiltered(String siteCode) {
        List<WorkerScenarioFixtureRow> all = loadAll();
        String key = Objects.requireNonNull(siteCode, "siteCode").trim();
        return all.stream()
                .filter(row -> matchesSite(row, key))
                .collect(Collectors.toList());
    }

    private List<WorkerScenarioFixtureRow> loadAll() {
        try (InputStream in = demoWorkforceFile.getInputStream()) {
            WorkforceScenarioFile root = objectMapper.readValue(in, WorkforceScenarioFile.class);
            return root.getWorkers() == null ? List.of() : root.getWorkers();
        } catch (IOException e) {
            throw new BaseException(FAIL);
            // throw new BaseException("DEMO_FIXTURE_READ_FAIL", "시연용 인력 JSON 을 읽지 못했습니다.");
        }
    }

    /** 시연 JSON 에 `siteCode` 가 없으면 표시용 `site` 문자열과 대소문자 무시 비교. */
    private boolean matchesSite(WorkerScenarioFixtureRow row, String siteCode) {
        if (row.getSiteCode() != null && !row.getSiteCode().isBlank()) {
            return row.getSiteCode().trim().equalsIgnoreCase(siteCode);
        }
        return row.getSite() != null && row.getSite().trim().equalsIgnoreCase(siteCode);
    }
}