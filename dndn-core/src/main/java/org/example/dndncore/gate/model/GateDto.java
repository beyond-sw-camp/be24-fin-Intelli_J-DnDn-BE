package org.example.dndncore.gate.model;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.example.dndncore.project.model.entity.Project;

import java.time.LocalDateTime;
import java.util.List;

public class GateDto {

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CreateReq {
        private String name;
        private Double x;
        private Double y;

        public Gate toEntity() {
            return toEntity(null);
        }

        public Gate toEntity(Project project) {
            String resolvedName = (this.name == null || this.name.isBlank()) ? "Gate" : this.name;

            return Gate.builder()
                    .project(project)
                    .name(resolvedName)
                    .x(clamp(this.x))
                    .y(clamp(this.y))
                    .vehicles(0)
                    .manpower(2)
                    .build();
        }

        private Double clamp(Double v) {
            if (v == null) return 0.0;
            return Math.max(0.0, Math.min(100.0, v));
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class UpdateReq {
        private String name;
        private Double x;
        private Double y;
        private Integer vehicles;
        private Integer manpower;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PositionReq {
        private Double x;
        private Double y;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class VehiclesReq {
        private Integer vehicles;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ManpowerReq {
        private Integer manpower;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Res {
        private Long idx;
        private Long projectId;
        private String name;
        private Double x;
        private Double y;
        private Integer vehicles;
        private Integer manpower;

        // 백엔드 derived 값 (프론트는 얇은 표시 레이어로만 사용)
        private Integer capacity;
        private Integer activeMachineCount;
        private String congestion;       // SMOOTH / BUSY / CRITICAL
        private String congestionLabel;  // 원활 / 혼잡 / 매우 혼잡
        private Boolean inefficient;
        private String noticeType;       // HUMAN_WASH_MODE / INEFFICIENT / CRITICAL_GUIDE / OPTIMAL
        private String noticeMessage;

        private List<MachineRes> machines;

        public static Res from(Gate entity) {
            List<MachineRes> machineDtos = entity.getSortedMachines().stream()
                    .map(MachineRes::from)
                    .toList();

            GateCongestion congestion = entity.resolveCongestion();
            GateNotice notice = entity.resolveNotice();

            String congestionCode = congestion == null ? null : congestion.name();
            String congestionLabel = congestion == null ? null : congestion.getLabel();
            String noticeCode = notice == null ? null : notice.name();
            String noticeMessage = notice == null ? null : notice.getMessage();
            Long projectId = entity.getProject() == null ? null : entity.getProject().getIdx();

            return Res.builder()
                    .idx(entity.getIdx())
                    .projectId(projectId)
                    .name(entity.getName())
                    .x(entity.getX())
                    .y(entity.getY())
                    .vehicles(entity.getVehicles())
                    .manpower(entity.getManpower())
                    .capacity(entity.getCapacity())
                    .activeMachineCount(entity.getActiveMachineCount())
                    .congestion(congestionCode)
                    .congestionLabel(congestionLabel)
                    .inefficient(entity.isInefficient())
                    .noticeType(noticeCode)
                    .noticeMessage(noticeMessage)
                    .machines(machineDtos)
                    .build();
        }
    }


    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class BlueprintReq {
        @NotBlank(message = "도면 데이터는 필수입니다.")
        private String dataUrl;
        private String originalFileName;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BlueprintRes {
        private Long projectId;
        private String dataUrl;
        private String originalFileName;
        private LocalDateTime updatedAt;
        private boolean exists;

        public static BlueprintRes empty(Long projectId) {
            return BlueprintRes.builder()
                    .projectId(projectId)
                    .exists(false)
                    .build();
        }

        public static BlueprintRes from(GateBlueprint entity) {
            Long projectId = entity.getProject() == null ? null : entity.getProject().getIdx();

            return BlueprintRes.builder()
                    .projectId(projectId)
                    .dataUrl(entity.getDataUrl())
                    .originalFileName(entity.getOriginalFileName())
                    .updatedAt(entity.getUpdatedAt())
                    .exists(true)
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MachineRes {
        private Long idx;
        private boolean active;

        public static MachineRes from(GateMachine entity) {
            return MachineRes.builder()
                    .idx(entity.getIdx())
                    .active(entity.isActive())
                    .build();
        }
    }
}
