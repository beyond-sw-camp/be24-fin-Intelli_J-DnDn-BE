package org.example.dndncore.analysis;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.model.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    // 공정 진척률 비교
    // GET /analysis/progress?projectId=1
    @GetMapping("/progress")
    public ResponseEntity<?> progress(@RequestParam("projectId") Long projectId) {
        return ResponseEntity.ok(BaseResponse.success(
                analysisService.getProgressList(projectId)));
    }

//    // 지연 위험 작업 목록
//    // GET /analysis/delay-risks?projectId=1
//    @GetMapping("/delay-risks")
//    public ResponseEntity<?> delayRisks(@RequestParam("projectId") Long projectId) {
//        return ResponseEntity.ok(BaseResponse.success(
//                analysisService.getDelayRisks(projectId)));
//    }

    // 세부 작업 지연 위험 목록
    @GetMapping("/delay-risk-tasks")
    public ResponseEntity<?> delayRiskTasks(
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "tradeProcessId", required = false) Long tradeProcessId
    ) {
        return ResponseEntity.ok(BaseResponse.success(
                analysisService.getDelayRiskTasks(projectId, tradeProcessId)
        ));
    }
}