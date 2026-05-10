package org.example.dndn.ai;

import lombok.RequiredArgsConstructor;
import org.example.dndn.ai.model.AiScheduleRecommendationDto;
import org.example.dndn.common.model.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/schedule-recommendations")
@RequiredArgsConstructor
public class AiScheduleRecommendationController {

    private final AiScheduleRecommendationService recommendationService;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody AiScheduleRecommendationDto.CreateReq req) {
        return ResponseEntity.ok(BaseResponse.success(recommendationService.create(req)));
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam("projectId") Long projectId) {
        return ResponseEntity.ok(BaseResponse.success(recommendationService.list(projectId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return ResponseEntity.ok(BaseResponse.success(recommendationService.get(id)));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<?> complete(
            @PathVariable Long id,
            @RequestBody AiScheduleRecommendationDto.CompleteReq req
    ) {
        return ResponseEntity.ok(BaseResponse.success(recommendationService.complete(id, req)));
    }

    @PostMapping("/{id}/fail")
    public ResponseEntity<?> fail(
            @PathVariable Long id,
            @RequestBody AiScheduleRecommendationDto.FailReq req
    ) {
        return ResponseEntity.ok(BaseResponse.success(recommendationService.fail(id, req)));
    }
}
