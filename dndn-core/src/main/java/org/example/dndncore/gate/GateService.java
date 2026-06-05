package org.example.dndncore.gate;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.auth.security.AuthAccessService;
import org.example.dndncore.gate.model.Gate;
import org.example.dndncore.gate.model.GateBlueprint;
import org.example.dndncore.gate.model.GateDto;
import org.example.dndncore.gate.model.GateMachine;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GateService {

    private final GateRepository gateRepository;
    private final GateMachineRepository gateMachineRepository;
    private final GateBlueprintRepository gateBlueprintRepository;
    private final ProjectRepository projectRepository;
    private final AuthAccessService authAccessService;

    // 게이트 등록
    @Transactional
    public Long create(Long projectId, GateDto.CreateReq dto) {
        Project project = null;
        if (projectId != null) {
            project = findProject(projectId);
            authAccessService.assertProjectWriteAccess(project.getIdx());
        }

        Gate gate = dto.toEntity(project);
        gate.attachMachine();
        gate.attachMachine();
        Gate savedGate = gateRepository.save(gate);

        return savedGate.getIdx();
    }

    // 기존 호출부 호환용
    @Transactional
    public Long create(GateDto.CreateReq dto) {
        return create(null, dto);
    }

    // 게이트 단일 조회
    public GateDto.Res read(Long gateId) {
        Gate gate = findGate(gateId);
        assertGateProjectAccess(gate);

        return GateDto.Res.from(gate);
    }

    // 게이트 목록 조회
    public List<GateDto.Res> list(Long projectId) {
        if (projectId == null) {
            return gateRepository.findAll().stream()
                    .filter(this::canAccessGate)
                    .map(GateDto.Res::from)
                    .toList();
        }

        Project project = findProject(projectId);
        authAccessService.assertProjectAccess(project.getIdx());

        return gateRepository.findByProject_IdxOrderByIdxAsc(project.getIdx()).stream()
                .map(GateDto.Res::from)
                .toList();
    }

    // 기존 호출부 호환용
    public List<GateDto.Res> list() {
        return list(null);
    }

    // 공사현장별 도면 조회
    public GateDto.BlueprintRes readBlueprint(Long projectId) {
        Project project = findProject(projectId);
        authAccessService.assertProjectAccess(project.getIdx());

        return gateBlueprintRepository.findByProject_Idx(project.getIdx())
                .map(GateDto.BlueprintRes::from)
                .orElseGet(() -> GateDto.BlueprintRes.empty(project.getIdx()));
    }

    // 공사현장별 도면 저장
    @Transactional
    public GateDto.BlueprintRes saveBlueprint(Long projectId, GateDto.BlueprintReq dto) {
        Project project = findProject(projectId);
        authAccessService.assertProjectWriteAccess(project.getIdx());

        GateBlueprint blueprint = gateBlueprintRepository.findByProject_Idx(project.getIdx())
                .map(existing -> {
                    existing.updateDataUrl(dto.getDataUrl(), dto.getOriginalFileName());
                    return existing;
                })
                .orElseGet(() -> GateBlueprint.create(project, dto.getDataUrl(), dto.getOriginalFileName()));

        GateBlueprint saved = gateBlueprintRepository.save(blueprint);
        return GateDto.BlueprintRes.from(saved);
    }

    // 게이트 정보 수정
    @Transactional
    public void update(Long gateId, GateDto.UpdateReq dto) {
        Gate gate = findWritableGate(gateId);

        gate.updateInfo(
                dto.getName(),
                dto.getX(),
                dto.getY(),
                dto.getVehicles(),
                dto.getManpower()
        );
    }

    // 게이트 좌표 수정
    @Transactional
    public void updatePosition(Long gateId, GateDto.PositionReq dto) {
        Gate gate = findWritableGate(gateId);

        gate.updatePosition(dto.getX(), dto.getY());
    }

    // 진입 차량 수 수정
    @Transactional
    public void updateVehicles(Long gateId, GateDto.VehiclesReq dto) {
        Gate gate = findWritableGate(gateId);

        gate.updateVehicles(dto.getVehicles());
    }

    // 배치 인원 수정
    @Transactional
    public void updateManpower(Long gateId, GateDto.ManpowerReq dto) {
        Gate gate = findWritableGate(gateId);

        gate.updateManpower(dto.getManpower());
    }

    // 세척 기계 추가
    @Transactional
    public Long addMachine(Long gateId) {
        Gate gate = findWritableGate(gateId);

        GateMachine machine = gate.attachMachine();
        GateMachine savedMachine = gateMachineRepository.save(machine);

        return savedMachine.getIdx();
    }

    // 세척 기계 ON/OFF 토글
    @Transactional
    public void toggleMachine(Long gateId, Long machineId) {
        Gate gate = findWritableGate(gateId);
        GateMachine machine = findMachineInGate(gate, machineId);

        machine.toggle();
    }

    // 세척 기계 삭제
    @Transactional
    public void removeMachine(Long gateId, Long machineId) {
        Gate gate = findWritableGate(gateId);
        GateMachine machine = findMachineInGate(gate, machineId);

        gate.detachMachine(machine);
    }

    // 게이트 삭제
    @Transactional
    public void delete(Long gateId) {
        Gate gate = findWritableGate(gateId);

        gateRepository.delete(gate);
    }

    private Project findProject(Long projectId) {
        if (projectId == null) {
            throw new RuntimeException("현장 ID는 필수입니다.");
        }

        return projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("현장을 찾을 수 없습니다."));
    }

    private Gate findGate(Long gateId) {
        return gateRepository.findById(gateId)
                .orElseThrow(() -> new RuntimeException("게이트를 찾을 수 없습니다."));
    }

    private Gate findWritableGate(Long gateId) {
        Gate gate = findGate(gateId);
        assertGateProjectWriteAccess(gate);
        return gate;
    }

    private boolean canAccessGate(Gate gate) {
        Long projectId = gate != null && gate.getProject() != null ? gate.getProject().getIdx() : null;
        return authAccessService.canAccessProjectId(projectId);
    }

    private void assertGateProjectAccess(Gate gate) {
        if (gate == null || gate.getProject() == null) {
            return;
        }
        authAccessService.assertProjectAccess(gate.getProject().getIdx());
    }

    private void assertGateProjectWriteAccess(Gate gate) {
        if (gate == null || gate.getProject() == null) {
            return;
        }
        authAccessService.assertProjectWriteAccess(gate.getProject().getIdx());
    }

    private GateMachine findMachineInGate(Gate gate, Long machineId) {
        return gate.getMachines().stream()
                .filter(machine -> machine.getIdx().equals(machineId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("게이트에 속한 세척 기계를 찾을 수 없습니다."));
    }
}
