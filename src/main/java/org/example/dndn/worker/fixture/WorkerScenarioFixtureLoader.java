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

import static org.example.dndn.common.model.BaseResponseStatus.FAIL;

@Component
@RequiredArgsConstructor
public class WorkerScenarioFixtureLoader {

    private final ObjectMapper objectMapper;

    /** 예: classpath:data/demo/workforce-sync-demo.json */
    @Value("classpath:data/workforce-sync-demo.json")
    private Resource demoWorkforceFile;

    public List<WorkerScenarioFixtureRow> loadWorkers() {
        try (InputStream in = demoWorkforceFile.getInputStream()) {
            WorkforceScenarioFile root = objectMapper.readValue(in, WorkforceScenarioFile.class);
            return root.getWorkers() == null ? List.of() : root.getWorkers();
        } catch (IOException e) {
            throw new BaseException(FAIL);
        }
    }
}