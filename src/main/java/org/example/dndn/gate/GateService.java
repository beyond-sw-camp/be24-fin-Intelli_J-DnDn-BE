package org.example.dndn.gate;

import lombok.RequiredArgsConstructor;
import org.example.dndn.gate.model.Gate;
import org.example.dndn.gate.model.GateDto;
import org.example.dndn.gate.model.GateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GateService {

    private final GateRepository gateRepository;
    private final GateMachineRepository gateMachineRepository;

    // 게이트 등록 (기본 기계 2대 OFF 상태로 자동 추가 - 프론트 기존 동작 유지)
    @Transactional
    public Long create(GateDto.CreateReq dto) {
        Gate gate = dto.toEntity();
        gate.attachMachine();
        gate.attachMachine();
        Gate savedGate = gateRepository.save(gate);

        return savedGate.getIdx();
    }

    // 게이트 단일 조회
    public GateDto.Res read(Long gateId) {
        Gate gate = findGate(gateId);

        return GateDto.Res.from(gate);
    }

    // 게이트 목록 조회
    public List<GateDto.Res> list() {
        return gateRepository.findAll().stream()
                .map(GateDto.Res::from)
                .toList();
    }

    // 게이트 정보 수정 (이름/좌표/차량/인원 통합)
    @Transactional
    public void update(Long gateId, GateDto.UpdateReq dto) {
        Gate gate = findGate(gateId);

        gate.updateInfo(
                dto.getName(),
                dto.getX(),
                dto.getY(),
                dto.getVehicles(),
                dto.getManpower()
        );
    }

    // 게이트 좌표 수정 (드래그 종료 시)
    @Transactional
    public void updatePosition(Long gateId, GateDto.PositionReq dto) {
        Gate gate = findGate(gateId);

        gate.updatePosition(dto.getX(), dto.getY());
    }

    // 진입 차량 수 수정
    @Transactional
    public void updateVehicles(Long gateId, GateDto.VehiclesReq dto) {
        Gate gate = findGate(gateId);

        gate.updateVehicles(dto.getVehicles());
    }

    // 배치 인원 수정
    @Transactional
    public void updateManpower(Long gateId, GateDto.ManpowerReq dto) {
        Gate gate = findGate(gateId);

        gate.updateManpower(dto.getManpower());
    }

    // 세척 기계 추가
    @Transactional
    public Long addMachine(Long gateId) {
        Gate gate = findGate(gateId);

        GateMachine machine = gate.attachMachine();
        // cascade 의존하지 않고 즉시 idx 할당받기 위해 명시적 save
        GateMachine savedMachine = gateMachineRepository.save(machine);

        return savedMachine.getIdx();
    }

    // 세척 기계 ON/OFF 토글
    @Transactional
    public void toggleMachine(Long gateId, Long machineId) {
        Gate gate = findGate(gateId);
        GateMachine machine = findMachineInGate(gate, machineId);

        machine.toggle();
    }

    // 세척 기계 삭제
    @Transactional
    public void removeMachine(Long gateId, Long machineId) {
        Gate gate = findGate(gateId);
        GateMachine machine = findMachineInGate(gate, machineId);

        gate.detachMachine(machine);
    }

    // 게이트 삭제
    @Transactional
    public void delete(Long gateId) {
        Gate gate = findGate(gateId);

        gateRepository.delete(gate);
    }

    private Gate findGate(Long gateId) {
        return gateRepository.findById(gateId)
                .orElseThrow(() -> new RuntimeException("게이트를 찾을 수 없습니다."));
    }

    private GateMachine findMachineInGate(Gate gate, Long machineId) {
        return gate.getMachines().stream()
                .filter(machine -> machine.getIdx().equals(machineId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("게이트에 속한 세척 기계를 찾을 수 없습니다."));
    }
}