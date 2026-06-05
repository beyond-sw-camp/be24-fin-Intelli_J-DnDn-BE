package org.example.dndncore.gate;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.gate.model.GateDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/gate")
@RequiredArgsConstructor
public class GateController {

    private final GateService gateService;

    // 게이트 등록
    @PostMapping
    public ResponseEntity<?> create(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestBody GateDto.CreateReq dto) {
        Long newIdx = gateService.create(projectId, dto);
        return ResponseEntity.ok(BaseResponse.success(newIdx));
    }

    // 게이트 단일 조회
    @GetMapping("/{gateId}")
    public ResponseEntity<?> read(@PathVariable("gateId") Long gateId) {
        GateDto.Res dto = gateService.read(gateId);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // 게이트 목록 조회
    @GetMapping
    public ResponseEntity<?> list(@RequestParam(value = "projectId", required = false) Long projectId) {
        List<GateDto.Res> dtos = gateService.list(projectId);
        return ResponseEntity.ok(BaseResponse.success(dtos));
    }

    // 공사현장별 도면 조회
    @GetMapping("/blueprint")
    public ResponseEntity<?> readBlueprint(@RequestParam("projectId") Long projectId) {
        GateDto.BlueprintRes dto = gateService.readBlueprint(projectId);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // 공사현장별 도면 저장
    @PutMapping("/blueprint")
    public ResponseEntity<?> saveBlueprint(
            @RequestParam("projectId") Long projectId,
            @Valid @RequestBody GateDto.BlueprintReq dto) {
        GateDto.BlueprintRes saved = gateService.saveBlueprint(projectId, dto);
        return ResponseEntity.ok(BaseResponse.success(saved));
    }

    // 게이트 정보 수정 (전체 필드)
    @PutMapping("/{gateId}")
    public ResponseEntity<?> update(
            @PathVariable("gateId") Long gateId,
            @RequestBody GateDto.UpdateReq dto) {
        gateService.update(gateId, dto);
        return ResponseEntity.ok(BaseResponse.success("게이트 정보가 수정되었습니다."));
    }

    // 게이트 좌표 수정 (드래그 종료)
    @PatchMapping("/{gateId}/position")
    public ResponseEntity<?> updatePosition(
            @PathVariable("gateId") Long gateId,
            @RequestBody GateDto.PositionReq dto) {
        gateService.updatePosition(gateId, dto);
        return ResponseEntity.ok(BaseResponse.success("게이트 좌표가 수정되었습니다."));
    }

    // 진입 차량 수 수정
    @PatchMapping("/{gateId}/vehicles")
    public ResponseEntity<?> updateVehicles(
            @PathVariable("gateId") Long gateId,
            @RequestBody GateDto.VehiclesReq dto) {
        gateService.updateVehicles(gateId, dto);
        return ResponseEntity.ok(BaseResponse.success("진입 차량 수가 수정되었습니다."));
    }

    // 배치 인원 수정
    @PatchMapping("/{gateId}/manpower")
    public ResponseEntity<?> updateManpower(
            @PathVariable("gateId") Long gateId,
            @RequestBody GateDto.ManpowerReq dto) {
        gateService.updateManpower(gateId, dto);
        return ResponseEntity.ok(BaseResponse.success("배치 인원이 수정되었습니다."));
    }

    // 세척 기계 추가
    @PostMapping("/{gateId}/machine")
    public ResponseEntity<?> addMachine(@PathVariable("gateId") Long gateId) {
        Long newMachineIdx = gateService.addMachine(gateId);
        return ResponseEntity.ok(BaseResponse.success(newMachineIdx));
    }

    // 세척 기계 ON/OFF 토글
    @PatchMapping("/{gateId}/machine/{machineId}")
    public ResponseEntity<?> toggleMachine(
            @PathVariable("gateId") Long gateId,
            @PathVariable("machineId") Long machineId) {
        gateService.toggleMachine(gateId, machineId);
        return ResponseEntity.ok(BaseResponse.success("세척 기계 상태가 변경되었습니다."));
    }

    // 세척 기계 삭제
    @DeleteMapping("/{gateId}/machine/{machineId}")
    public ResponseEntity<?> removeMachine(
            @PathVariable("gateId") Long gateId,
            @PathVariable("machineId") Long machineId) {
        gateService.removeMachine(gateId, machineId);
        return ResponseEntity.ok(BaseResponse.success("세척 기계가 삭제되었습니다."));
    }

    // 게이트 삭제
    @DeleteMapping("/{gateId}")
    public ResponseEntity<?> delete(@PathVariable("gateId") Long gateId) {
        gateService.delete(gateId);
        return ResponseEntity.ok(BaseResponse.success("게이트가 삭제되었습니다."));
    }
}
