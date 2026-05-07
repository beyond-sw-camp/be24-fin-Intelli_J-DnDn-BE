package org.example.dndn.project.controller;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.project.service.ProjectService;
import org.example.dndn.project.model.dto.ProjectDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ProjectDto.Req dto) {
        Long newIdx = projectService.create(dto);
        return ResponseEntity.ok(BaseResponse.success(newIdx));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<?> read(@PathVariable("projectId") Long projectId) {
        return ResponseEntity.ok(BaseResponse.success(projectService.read(projectId)));
    }

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(BaseResponse.success(projectService.list()));
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<?> update(
            @PathVariable("projectId") Long projectId,
            @RequestBody ProjectDto.Req dto) {
        projectService.update(projectId, dto);
        return ResponseEntity.ok(BaseResponse.success("현장이 수정되었습니다."));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<?> delete(@PathVariable("projectId") Long projectId) {
        projectService.delete(projectId);
        return ResponseEntity.ok(BaseResponse.success("현장이 삭제되었습니다."));
    }
}