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
        private List<WorkOrderEquipmentDto> equipments; // 2단계에서 활용됨
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
}
