package org.example.dndn.batch.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndn.domain.project.repository.ProjectRepository;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 현장(site) 단위 파티셔너.
 * project 테이블에서 활성 현장 코드를 추출해 현장 1개당 ExecutionContext 1개를 생성한다.
 * 슬레이브 Step은 자신의 ExecutionContext에서 siteCode를 꺼내 독립적으로 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteCodePartitioner implements Partitioner {

    private static final Pattern SITE_CODE_PATTERN = Pattern.compile("^\\s*\\[([^\\]]+)]");

    private final ProjectRepository projectRepository;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        List<String> siteCodes = projectRepository.findAllByActiveTrue().stream()
                .map(p -> {
                    Matcher m = SITE_CODE_PATTERN.matcher(p.getName());
                    return m.find() ? m.group(1).trim() : null;
                })
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();

        log.info("[Partitioner] 파티션 생성: {}개 현장 (스레드={})", siteCodes.size(), gridSize);

        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();
        for (String siteCode : siteCodes) {
            ExecutionContext ctx = new ExecutionContext();
            ctx.putString("siteCode", siteCode);
            partitions.put("partition-" + siteCode, ctx);
            log.debug("[Partitioner] partition-{} 생성", siteCode);
        }
        return partitions;
    }
}
