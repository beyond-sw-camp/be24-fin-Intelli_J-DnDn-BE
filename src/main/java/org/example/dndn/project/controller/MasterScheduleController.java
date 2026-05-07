package org.example.dndn.project.controller;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.project.service.MasterScheduleService;
import org.example.dndn.project.model.dto.MasterScheduleDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/master-schedule")
@RequiredArgsConstructor
public class MasterScheduleController {

    private final MasterScheduleService masterScheduleService;

    // 공정표 등록 (파일 메타데이터만)
    @PostMapping
    public ResponseEntity<?> create(@RequestBody MasterScheduleDto.Req dto) {
        Long newIdx = masterScheduleService.create(dto);
        return ResponseEntity.ok(BaseResponse.success(newIdx));
    }

    // 공정표 단일 조회
    @GetMapping("/{scheduleId}")
    public ResponseEntity<?> read(@PathVariable("scheduleId") Long scheduleId) {
        return ResponseEntity.ok(BaseResponse.success(masterScheduleService.read(scheduleId)));
    }

    // 현장별 공정표 목록 (docType 필터 선택)
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