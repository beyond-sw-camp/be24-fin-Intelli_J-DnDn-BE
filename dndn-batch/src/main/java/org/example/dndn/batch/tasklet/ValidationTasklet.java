package org.example.dndn.batch.tasklet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndn.domain.project.repository.ProjectRepository;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ValidationTasklet implements Tasklet {

    private final ProjectRepository projectRepository;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        long activeSiteCount = projectRepository.countByActiveTrue();
        long totalSiteCount  = projectRepository.count();
        long inactiveSiteCount = totalSiteCount - activeSiteCount;

        if (activeSiteCount == 0) {
            log.warn("[검증] 활성화된 현장이 없습니다. 동기화 대상 없음. (전체={}, 비활성={})",
                    totalSiteCount, inactiveSiteCount);
        } else {
            log.info("[검증] 동기화 대상 현장 수={} (활성={}, 비활성={}, 전체={})",
                    activeSiteCount, activeSiteCount, inactiveSiteCount, totalSiteCount);
        }
        return RepeatStatus.FINISHED;
    }
}