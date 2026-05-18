package org.example.dndncore.project.service;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.auth.security.AuthAccessService;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.model.dto.ProjectDto;
import org.example.dndncore.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final AuthAccessService authAccessService;

    @Transactional
    public Long create(ProjectDto.Req dto) {
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
        authAccessService.assertProjectAccess(projectId);
        findProject(projectId).update(
                dto.getName(),
                dto.getLocation(),
                dto.getStartDate(),
                dto.getEndDate()
        );
    }

    @Transactional
    public void delete(Long projectId) {
        authAccessService.assertProjectAccess(projectId);
        projectRepository.delete(findProject(projectId));
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("현장을 찾을 수 없습니다."));
    }
}
