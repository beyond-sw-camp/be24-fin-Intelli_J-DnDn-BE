package org.example.dndncore.batch;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.ClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BatchTriggerService {

    private static final String NAMESPACE = "batch";
    private static final String CRONJOB_NAME = "worker-sync-cronjob";

    public String triggerWorkerSync() {
        try {
            ApiClient client = ClientBuilder.cluster().build();
            Configuration.setDefaultApiClient(client);

            BatchV1Api batchApi = new BatchV1Api();

            V1CronJob cronJob = batchApi
                    .readNamespacedCronJob(CRONJOB_NAME, NAMESPACE, null);

            if (cronJob.getSpec() == null
                    || cronJob.getSpec().getJobTemplate() == null
                    || cronJob.getSpec().getJobTemplate().getSpec() == null) {
                throw new IllegalStateException("CronJob '" + CRONJOB_NAME + "' 의 jobTemplate.spec 이 비어 있습니다.");
            }

            String jobName = "worker-sync-manual-" + System.currentTimeMillis();

            V1Job job = new V1Job()
                    .apiVersion("batch/v1")
                    .kind("Job")
                    .metadata(new V1ObjectMeta()
                            .name(jobName)
                            .namespace(NAMESPACE)
                            .putLabelsItem("triggered-by", "api"))
                    .spec(cronJob.getSpec().getJobTemplate().getSpec());

            batchApi.createNamespacedJob(NAMESPACE, job, null, null, null, null);
            log.info("[배치 트리거] K8s Job 생성 완료: {}", jobName);
            return jobName;

        } catch (ApiException e) {
            log.error("[배치 트리거] K8s API 오류 code={} body={}", e.getCode(), e.getResponseBody());
            throw new RuntimeException("K8s Job 생성 실패 (code=" + e.getCode() + "): " + e.getResponseBody(), e);
        } catch (IllegalStateException e) {
            log.error("[배치 트리거] CronJob 스펙 오류: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[배치 트리거] 실패: {}", e.getMessage(), e);
            // K8s Pod 외부(로컬)에서 실행 시 cluster() 빌드 실패 — 명확한 메시지 제공
            if (e.getMessage() != null && e.getMessage().contains("cluster config")) {
                throw new RuntimeException("K8s 클러스터 환경에서만 동작합니다. 로컬에서는 dndn-batch workerSyncJob 또는 POST /management/sync/all 을 사용하세요.", e);
            }
            throw new RuntimeException("배치 트리거 실패: " + e.getMessage(), e);
        }
    }
}
