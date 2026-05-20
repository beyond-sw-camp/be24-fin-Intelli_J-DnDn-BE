package org.example.dndndocumentmanagement.model;

import java.util.Locale;

public enum DocumentType {
    ALL,
    WORK_ORDER,
    DAILY_REPORT,
    TRADE_PLAN,
    MASTER,
    MILESTONE,
    WEIGHT;

    public static DocumentType fromCode(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace("-", "_");
        return switch (normalized) {
            case "WORK_INSTRUCTION", "WORK_ORDER" -> WORK_ORDER;
            case "DAILY_REPORT", "REPORT" -> DAILY_REPORT;
            case "CONSTRUCTION_PLAN", "UPLOADED_DOCUMENT", "TRADE_PLAN" -> TRADE_PLAN;
            case "MASTER" -> MASTER;
            case "MILESTONE" -> MILESTONE;
            case "WEIGHT" -> WEIGHT;
            default -> ALL;
        };
    }
}
