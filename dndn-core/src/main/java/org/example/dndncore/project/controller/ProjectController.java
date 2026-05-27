package org.example.dndncore.project.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.project.model.dto.ProjectDto;
import org.example.dndncore.project.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/project")
@RequiredArgsConstructor
@Tag(name = "Project", description = "프로젝트 관리 API")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @Operation(summary = "프로젝트 생성", description = "신규 프로젝트를 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "생성 성공",
                    content = @Content(schema = @Schema(implementation = Long.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> create(@RequestBody ProjectDto.Req dto) {
        Long newIdx = projectService.create(dto);
        return ResponseEntity.ok(BaseResponse.success(newIdx));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "프로젝트 단건 조회", description = "프로젝트 ID로 단건 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ProjectDto.Res.class))),
            @ApiResponse(responseCode = "404", description = "대상을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> read(@PathVariable("projectId") Long projectId) {
        return ResponseEntity.ok(BaseResponse.success(projectService.read(projectId)));
    }

    @GetMapping
    @Operation(summary = "프로젝트 목록 조회", description = "프로젝트 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProjectDto.Res.class)))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(BaseResponse.success(projectService.list()));
    }

    @PutMapping("/{projectId}")
    @Operation(summary = "프로젝트 수정", description = "프로젝트 정보를 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "대상을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> update(
            @Parameter(description = "프로젝트 ID", required = true, example = "1")
            @PathVariable("projectId") Long projectId,
            @RequestBody ProjectDto.Req dto) {
        projectService.update(projectId, dto);
        return ResponseEntity.ok(BaseResponse.success("현장이 수정되었습니다."));
    }

    @PatchMapping("/{projectId}/deactivate")
    @Operation(summary = "프로젝트 비활성화", description = "프로젝트를 운영 종료 상태로 변경합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404", description = "대상을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> deactivate(@PathVariable("projectId") Long projectId) {
        projectService.deactivate(projectId);
        return ResponseEntity.ok(BaseResponse.success("현장이 운영 종료 처리되었습니다."));
    }

    @PatchMapping("/{projectId}/activate")
    @Operation(summary = "프로젝트 활성화", description = "프로젝트를 운영 중 상태로 변경합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404", description = "대상을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> activate(@PathVariable("projectId") Long projectId) {
        projectService.activate(projectId);
        return ResponseEntity.ok(BaseResponse.success("현장이 운영 중으로 변경되었습니다."));
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "프로젝트 삭제", description = "프로젝트를 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404", description = "대상을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> delete(@PathVariable("projectId") Long projectId) {
        projectService.delete(projectId);
        return ResponseEntity.ok(BaseResponse.success("현장이 삭제되었습니다."));
    }
}