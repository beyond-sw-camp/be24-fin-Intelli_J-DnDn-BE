package org.example.dndn.gate.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "gate")
public class Gate extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    private String name;

    private Double x;
    private Double y;

    private Integer vehicles;   // 현재 진입 중장비 대수
    private Integer manpower;   // 배치 인원

    @OneToMany(mappedBy = "gate", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GateMachine> machines = new ArrayList<>();

    /**
     * 게이트 정보 일괄 수정
     */
    public void updateInfo(String name, Double x, Double y, Integer vehicles, Integer manpower) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (x != null) this.x = clampPercent(x);
        if (y != null) this.y = clampPercent(y);
        if (vehicles != null) this.vehicles = Math.max(0, vehicles);
        if (manpower != null) this.manpower = Math.max(2, manpower);
    }

    /**
     * 좌표 수정 (드래그 종료)
     */
    public void updatePosition(Double x, Double y) {
        this.x = clampPercent(x);
        this.y = clampPercent(y);
    }

    /**
     * 진입 차량 수 수정
     */
    public void updateVehicles(Integer vehicles) {
        this.vehicles = Math.max(0, vehicles == null ? 0 : vehicles);
    }

    /**
     * 배치 인원 수정 (최소 2명)
     */
    public void updateManpower(Integer manpower) {
        this.manpower = Math.max(2, manpower == null ? 2 : manpower);
    }

    /**
     * 세척 기계 추가
     */
    public GateMachine attachMachine() {
        GateMachine machine = GateMachine.builder()
                .active(false)
                .build();
        machine.bindGate(this);
        this.machines.add(machine);
        return machine;
    }

    /**
     * 세척 기계 제거
     */
    public void detachMachine(GateMachine machine) {
        this.machines.remove(machine);
    }

    /**
     * 활성화된 세척 기계 수
     */
    public int getActiveMachineCount() {
        if (machines == null) return 0;
        return (int) machines.stream().filter(GateMachine::isActive).count();
    }

    /**
     * 게이트 수용 능력
     * - 기계 가동 시: (활성 기계 수 × 5) + ((인원 - 2) / 2 × 3)
     * - 인력 세척 모드: (인원 / 2) × 3
     */
    public int getCapacity() {
        int safeManpower = manpower == null ? 0 : manpower;
        int activeMachines = getActiveMachineCount();

        if (activeMachines > 0) {
            return activeMachines * 5 + ((safeManpower - 2) / 2) * 3;
        }
        return (safeManpower / 2) * 3;
    }

    /**
     * 혼잡도 동적 계산
     */
    public GateCongestion resolveCongestion() {
        int safeVehicles = vehicles == null ? 0 : vehicles;
        int capacity = getCapacity();

        if (safeVehicles <= capacity) return GateCongestion.SMOOTH;
        if (safeVehicles <= capacity + 3) return GateCongestion.BUSY;
        return GateCongestion.CRITICAL;
    }

    /**
     * 비효율 가동 여부
     * - 기계 1대 이상 + 차량 5대 이하 + 활성 기계 2대
     */
    public boolean isInefficient() {
        int safeVehicles = vehicles == null ? 0 : vehicles;
        return machines != null
                && !machines.isEmpty()
                && safeVehicles <= 5
                && getActiveMachineCount() == 2;
    }

    /**
     * 운영 알림 동적 결정
     */
    public GateNotice resolveNotice() {
        int activeMachines = getActiveMachineCount();

        if (machines != null && !machines.isEmpty() && activeMachines == 0) {
            return GateNotice.HUMAN_WASH_MODE;
        }
        if (isInefficient()) {
            return GateNotice.INEFFICIENT;
        }
        if (resolveCongestion() == GateCongestion.CRITICAL) {
            return GateNotice.CRITICAL_GUIDE;
        }
        return GateNotice.OPTIMAL;
    }

    /**
     * 화면 표시 순서를 위해 idx 오름차순 정렬된 기계 목록
     */
    public List<GateMachine> getSortedMachines() {
        if (machines == null) return List.of();
        return machines.stream()
                .sorted(Comparator.comparing(
                        GateMachine::getIdx,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();
    }

    private Double clampPercent(Double value) {
        if (value == null) return 0.0;
        return Math.max(0.0, Math.min(100.0, value));
    }
}