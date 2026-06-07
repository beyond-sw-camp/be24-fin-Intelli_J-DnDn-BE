package org.example.dndncore.project.service;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.auth.security.AuthAccessService;
import org.example.dndncore.common.exception.BaseException;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.model.dto.ProjectDto;
import org.example.dndncore.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.dndncore.common.model.BaseResponseStatus.PROJECT_DUPLICATE_SITE_CODE;
import static org.example.dndncore.common.model.BaseResponseStatus.PROJECT_INVALID_SITE_CODE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private static final Pattern SITE_CODE_IN_NAME = Pattern.compile("^\\s*\\[([^\\]]+)]");
    private static final Pattern SITE_CODE_FORMAT = Pattern.compile("^[A-Z]{2}-[A-Z]$");

    private final ProjectRepository projectRepository;
    private final AuthAccessService authAccessService;

    @Transactional
    public Long create(ProjectDto.Req dto) {
        String siteCode = extractSiteCode(dto.getName());
        validateSiteCode(siteCode);
        assertSiteCodeAvailable(siteCode, null);
        return projectRepository.save(dto.toEntity()).getIdx();
    }

    public ProjectDto.Res read(Long projectId) {
        authAccessService.assertProjectAccess(projectId);
        return ProjectDto.Res.from(findProject(projectId));
    }

    public List<ProjectDto.Res> list() {
        return projectRepository.findAll().stream()
                .filter(project -> authAccessService.canAccessProjectId(project.getIdx()))
                .map(ProjectDto.Res::from)
                .toList();
    }

    @Transactional
    public void update(Long projectId, ProjectDto.Req dto) {
        authAccessService.assertProjectWriteAccess(projectId);
        String siteCode = extractSiteCode(dto.getName());
        validateSiteCode(siteCode);
        assertSiteCodeAvailable(siteCode, projectId);
        findProject(projectId).update(
                dto.getName(),
                dto.getLocation(),
                dto.getStartDate(),
                dto.getEndDate()
        );
    }

    @Transactional
    public void deactivate(Long projectId) {
        findProject(projectId).deactivate();
    }

    @Transactional
    public void activate(Long projectId) {
        findProject(projectId).activate();
    }

    @Transactional
    public void delete(Long projectId) {
        authAccessService.assertProjectWriteAccess(projectId);
        projectRepository.delete(findProject(projectId));
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("현장을 찾을 수 없습니다."));
    }

    private String extractSiteCode(String projectName) {
        if (projectName == null || projectName.isBlank()) {
            return "";
        }
        Matcher matcher = SITE_CODE_IN_NAME.matcher(projectName.trim());
        return matcher.find() ? matcher.group(1).trim().toUpperCase() : "";
    }

    private void validateSiteCode(String siteCode) {
        if (siteCode.isBlank() || !SITE_CODE_FORMAT.matcher(siteCode).matches()) {
            throw new BaseException(PROJECT_INVALID_SITE_CODE);
        }
    }

    private void assertSiteCodeAvailable(String siteCode, Long excludeProjectId) {
        boolean duplicate = projectRepository.findAll().stream()
                .filter(project -> excludeProjectId == null || !excludeProjectId.equals(project.getIdx()))
                .map(project -> extractSiteCode(project.getName()))
                .anyMatch(existing -> !existing.isBlank() && existing.equalsIgnoreCase(siteCode));
        if (duplicate) {
            throw new BaseException(PROJECT_DUPLICATE_SITE_CODE);
        }
    }
}
