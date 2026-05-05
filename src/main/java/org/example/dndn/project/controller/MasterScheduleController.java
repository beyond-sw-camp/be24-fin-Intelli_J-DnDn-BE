package org.example.dndn.project.controller;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.common.model.BaseResponseStatus;
import org.example.dndn.project.model.dto.MasterScheduleDto;
import org.example.dndn.project.service.MasterScheduleService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/master-schedule")
@RequiredArgsConstructor
public class MasterScheduleController {

    private final MasterScheduleService masterScheduleService;

    // ─────────────────────────────────────────────────────────────
    // 파일 메타만 저장 (S3 업로드 후 URL을 직접 전달하는 경우)
    // ─────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<?> create(@RequestBody MasterScheduleDto.Req dto) {
        Long newIdx = masterScheduleService.create(dto);
        return ResponseEntity.ok(BaseResponse.success(newIdx));
    }

    // ─────────────────────────────────────────────────────────────
    // PDF 업로드 → AI 분석 → MasterSchedule + TradeProcess 저장
    //
    // POST /master-schedule/upload-pdf
    // FormData: projectId, docType, fileUrl(선택), file(PDF)
    //
    // 기존 /work-plan/upload-pdf 와 대응되는 새 엔드포인트
    // ─────────────────────────────────────────────────────────────

    @PostMapping(value = "/upload-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPdf(
            @RequestParam("projectId") Long projectId,
            @RequestParam("docType") String docType,
            @RequestParam(value = "fileUrl", required = false) String fileUrl,
            @RequestParam("file") MultipartFile file) {
        try {
            MasterScheduleService.MasterScheduleUploadResult result =
                    masterScheduleService.uploadAndAnalyze(projectId, docType, file, fileUrl);
            return ResponseEntity.ok(BaseResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(BaseResponse.fail(BaseResponseStatus.valueOf("PDF 분석 실패: " + e.getMessage())));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 조회 / 삭제
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/{scheduleId}")
    public ResponseEntity<?> read(@PathVariable("scheduleId") Long scheduleId) {
        return ResponseEntity.ok(BaseResponse.success(masterScheduleService.read(scheduleId)));
    }

    // 현장별 공정표 목록 (docType 필터 선택)
    // GET /master-schedule?projectId=1&docType=마스터 공정표
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "docType", required = false) String docType) {
        return ResponseEntity.ok(BaseResponse.success(
                masterScheduleService.listByProject(projectId, docType)));
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<?> delete(@PathVariable("scheduleId") Long scheduleId) {
        masterScheduleService.delete(scheduleId);
        return ResponseEntity.ok(BaseResponse.success("공정표가 삭제되었습니다."));
    }
}
