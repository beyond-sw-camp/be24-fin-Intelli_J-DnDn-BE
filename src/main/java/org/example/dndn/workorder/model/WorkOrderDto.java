package org.example.dndn.workorder.model;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

public class WorkOrderDto {

    // [WORKORDER_001] 1단계 : 지시서 작성 요청 DTO
    // feat : 작업 지시서 생성/수정 요청 (Request) DTO
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Req {
        private Long siteIdx;
        private Long partnerCompanyIdx;
        private Long workPlanId;
        private String tradeType;
        private String title;
        private String instructionContent;
        private LocalDate dueDate;
        private String statusCode;
        private Integer workerCount;
        private List<WorkOrderEquipmentDto> equipments;
    }

    // [WORKORDER_003] 3단계 : 지시서 목록 조회 응답 DTO
    // feat : 작업 지시서 조회 응답 (Response) DTO
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Res {
        private Long idx;
        private Long siteIdx;
        private Long partnerCompanyIdx;
        private Long workPlanId;
        private String tradeType;
        private String title;
        private String instructionContent;
        private LocalDate dueDate;
        private String statusCode;
        private Integer workerCount;
        private List<WorkOrderEquipmentDto> equipments;
    }

    // [GATE_EQUIP_001] 중장비 입출차 현황 테이블 표시용 응답 DTO
    // feat : 게이트별 중장비 현황 목록 - 게이트명 resolved 포함
    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GateEquipmentRes {
        private Long workOrderIdx;
        private String workOrderRef;       // WI-YYYY-XXX 형식 지시서 번호
        private String equipmentName;      // 장비명 (예: CAT 320 굴착기)
        private String equipmentType;      // 장비 유형 (예: 굴착기) - equipmentName 파싱
        private Integer equipmentCount;    // 투입 대수
        private Integer gateIdx;           // 배정 게이트 idx
        private String gateName;           // 배정 게이트명 (Gate 테이블 resolved)
        private Long partnerCompanyIdx;    // 협력사 idx
        private String statusCode;         // 원본 statusCode
        private String statusLabel;        // 한국어 상태 라벨 (작업중 / 대기 / 입차예정)
        private LocalDate dueDate;         // 작업 예정일

        /**
         * statusCode → 한국어 라벨 변환
         */
        public static String resolveStatusLabel(String statusCode) {
            if (statusCode == null) return "대기";
            return switch (statusCode.toUpperCase()) {
                case "APPROVED", "IN_PROGRESS" -> "작업중";
                case "PENDING" -> "입차예정";
                default -> "대기";
            };
        }

        /**
         * equipmentName에서 유형 파싱 (마지막 공백 이후 단어)
         * 예: "CAT 320 굴착기" → "굴착기"
         */
        public static String parseEquipmentType(String equipmentName) {
            if (equipmentName == null || equipmentName.isBlank()) return "";
            String[] parts = equipmentName.trim().split("\\s+");
            return parts[parts.length - 1];
        }
    }
}
