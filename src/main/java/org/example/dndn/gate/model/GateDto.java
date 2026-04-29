package org.example.dndn.gate.model;

import lombok.*;

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
            String resolvedName = (this.name == null || this.name.isBlank()) ? "Gate" : this.name;

            return Gate.builder()
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
        private String name;
        private Double x;
        private Double y;
        private Integer vehicles;
        private Integer manpower;

        private Integer capacity;
        private Integer activeMachineCount;
        private String congestion;
        private String congestionLabel;
        private Boolean inefficient;
        private String noticeType;
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

            return Res.builder()
                    .idx(entity.getIdx())
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
