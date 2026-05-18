package org.example.dndncore.project.controller;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.project.service.TradeProcessService;
import org.example.dndncore.project.model.dto.TradeProcessDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/trade-process")
@RequiredArgsConstructor
public class TradeProcessController {

    private final TradeProcessService tradeProcessService;

    // 공정 등록 (지금은 수동/시드 데이터, 나중에 AI 추출)
    @PostMapping
    public ResponseEntity<?> create(@RequestBody TradeProcessDto.Req dto) {
        Long newIdx = tradeProcessService.create(dto);
        return ResponseEntity.ok(BaseResponse.success(newIdx));
    }

    // 공정 단일 조회
    @GetMapping("/{tpId}")
    public ResponseEntity<?> read(@PathVariable("tpId") Long tpId) {
        return ResponseEntity.ok(BaseResponse.success(tradeProcessService.read(tpId)));
    }

    // 현장별 공정 목록 (공종 필터 선택)
    // GET /trade-process?projectId=1&tradeName=골조
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "tradeName", required = false) String tradeName,
            @RequestParam(value = "includeAllTrades", defaultValue = "false") boolean includeAllTrades) {
        return ResponseEntity.ok(BaseResponse.success(
                tradeProcessService.listByProject(projectId, tradeName, includeAllTrades)));
    }

    /**
     * 계정 생성 공종 드롭다운 전용.
     * GET /trade-process/milestone-trades?projectId={projectId}
     * isMilestone=true 이고 '준공','착공' 제외한 공종명 목록(List<String>) 반환.
     */
    @GetMapping("/milestone-trades")
    public ResponseEntity<?> getMilestoneTradeNames(@RequestParam("projectId") Long projectId) {
        return ResponseEntity.ok(BaseResponse.success(
                tradeProcessService.listMilestoneTradeNamesByProject(projectId)));
    }

    // 공정표별 공정 목록
    // GET /trade-process/by-schedule/1
    @GetMapping("/by-schedule/{scheduleId}")
    public ResponseEntity<?> listBySchedule(
            @PathVariable("scheduleId") Long scheduleId,
            @RequestParam(value = "includeAllTrades", defaultValue = "false") boolean includeAllTrades) {
        return ResponseEntity.ok(BaseResponse.success(
                tradeProcessService.listBySchedule(scheduleId, includeAllTrades)));
    }

    @PutMapping("/{tpId}")
    public ResponseEntity<?> update(
            @PathVariable("tpId") Long tpId,
            @RequestBody TradeProcessDto.Req dto) {
        tradeProcessService.update(tpId, dto);
        return ResponseEntity.ok(BaseResponse.success("공정이 수정되었습니다."));
    }

    @DeleteMapping("/{tpId}")
    public ResponseEntity<?> delete(@PathVariable("tpId") Long tpId) {
        tradeProcessService.delete(tpId);
        return ResponseEntity.ok(BaseResponse.success("공정이 삭제되었습니다."));
    }
}
