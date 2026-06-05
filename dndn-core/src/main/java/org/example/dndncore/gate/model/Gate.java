package org.example.dndncore.gate.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;
import org.example.dndncore.project.model.entity.Project;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_idx")
    private Project project;

    private String name;        // 게이트명

    private Double x;           // X 좌표 (0~100, 도면 비율 기준)
    private Double y;           // Y 좌표 (0~100, 도면 비율 기준)

    private Integer vehicles;   // 현재 진입 중장비 대수
    private Integer manpower;   // 배치 인원

    @OneToMany(mappedBy = "gate", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GateMachine> machines = new ArrayList<>();

    public void bindProject(Project project) {
        this.project = project;
    }

    public boolean belongsToProject(Long projectId) {
        if (projectId == null) return true;
        return project != null && project.getIdx() != null && project.getIdx().equals(projectId);
    }

    public void updateInfo(String name, Double x, Double y, Integer vehicles, Integer manpower) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (x != null) this.x = clampPercent(x);
        if (y != null) this.y = clampPercent(y);
        if (vehicles != null) this.vehicles = Math.max(0, vehicles);
        if (manpower != null) this.manpower = Math.max(2, manpower);
    }

    public void updatePosition(Double x, Double y) {
        this.x = clampPercent(x);
        this.y = clampPercent(y);
    }

    public void updateVehicles(Integer vehicles) {
        this.vehicles = Math.max(0, vehicles == null ? 0 : vehicles);
    }
    public void updateManpower(Integer manpower) {
        this.manpower = Math.max(2, manpower == null ? 2 : manpower);
    }
    public GateMachine attachMachine() {
        GateMachine machine = GateMachine.builder()
                .active(false)
                .build();
        machine.bindGate(this);
        this.machines.add(machine);
        return machine;
    }
    public void detachMachine(GateMachine machine) {
        this.machines.remove(machine);
    }
    public int getActiveMachineCount() {
        if (machines == null) return 0;
        return (int) machines.stream().filter(GateMachine::isActive).count();
    }

    public int getCapacity() {
        int safeManpower = manpower == null ? 0 : manpower;
        int activeMachines = getActiveMachineCount();

        if (activeMachines > 0) {
            return activeMachines * 5 + ((safeManpower - 2) / 2) * 3;
        }
        return (safeManpower / 2) * 3;
    }

    public GateCongestion resolveCongestion() {
        int safeVehicles = vehicles == null ? 0 : vehicles;
        int capacity = getCapacity();

        if (safeVehicles <= capacity) return GateCongestion.SMOOTH;
        if (safeVehicles <= capacity + 3) return GateCongestion.BUSY;
        return GateCongestion.CRITICAL;
    }

    public boolean isInefficient() {
        int safeVehicles = vehicles == null ? 0 : vehicles;
        return machines != null
                && !machines.isEmpty()
                && safeVehicles <= 5
                && getActiveMachineCount() == 2;
    }

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
