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
import org.example.dndncore.project.model.dto.MasterScheduleDto;
import org.example.dndncore.project.service.MasterScheduleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/master-schedule")
@RequiredArgsConstructor
@Tag(name = "Master Schedule", description = "마스터 공정표 API")
public class MasterScheduleController {

    private final MasterScheduleService masterScheduleService;

    // feat : 공정표 등록 API
    @PostMapping
    @Operation(summary = "공정표 등록", description = "공정표 메타데이터를 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 성공",
                    content = @Content(schema = @Schema(implementation = Long.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> create(@RequestBody MasterScheduleDto.Req dto) {
        Long newIdx = masterScheduleService.create(dto);
        return ResponseEntity.ok(BaseResponse.success(newIdx));
    }

    // feat : 공정표 단일 조회 API
    @GetMapping("/{scheduleId}")
    @Operation(summary = "공정표 단건 조회", description = "공정표 ID로 단건 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = MasterScheduleDto.Res.class))),
            @ApiResponse(responseCode = "404", description = "대상을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> read(@PathVariable("scheduleId") Long scheduleId) {
        return ResponseEntity.ok(BaseResponse.success(masterScheduleService.read(scheduleId)));
    }

    // feat : 현장별 공정표 목록 조회 API
    @GetMapping
    @Operation(summary = "공정표 목록 조회", description = "현장별 공정표 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MasterScheduleDto.Res.class)))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> list(
            @Parameter(description = "프로젝트 ID", required = true, example = "1")
            @RequestParam("projectId") Long projectId,
            @Parameter(description = "문서 타입", example = "MASTER")
            @RequestParam(value = "docType", required = false) String docType) {
        return ResponseEntity.ok(BaseResponse.success(
                masterScheduleService.listByProject(projectId, docType)));
    }

    @DeleteMapping("/{scheduleId}")
    @Operation(summary = "공정표 삭제", description = "공정표를 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404", description = "대상을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> delete(@PathVariable("scheduleId") Long scheduleId) {
        masterScheduleService.delete(scheduleId);
        return ResponseEntity.ok(BaseResponse.success("공정표가 삭제되었습니다."));
    }

    // feat : 공정표 파일 업로드 API
    @PostMapping("/upload")
    @Operation(summary = "공정표 업로드 및 추출", description = "공정표 파일을 업로드하고 공정 데이터를 추출합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> uploadAndExtract(
            @Parameter(description = "프로젝트 ID", required = true, example = "1")
            @RequestParam("projectId") Long projectId,
            @Parameter(description = "문서 타입", required = true, example = "MASTER")
            @RequestParam("docType") String docType,
            @Parameter(description = "업로드 파일", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(BaseResponse.success(
                masterScheduleService.uploadAndExtract(projectId, docType, file)
        ));
    }
}