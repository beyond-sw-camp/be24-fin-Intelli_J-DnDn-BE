package org.example.dndn.ai.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

public class WorkPlanAiDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExtractionResult {
        private List<Item> items;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private String tradeName;          // 예: 토공사
        private String tradeProcessName;   // 예: 터파기 및 토사반출
        private String name;               // 예: 터파기 1차 굴착 및 토사반출
        private String location;           // 예: 지하 굴착부 1구간
        private LocalDate startDate;        // 예: 2026-05-01
        private LocalDate endDate;          // 예: 2026-05-14
        private String note;                // 비고
    }
}