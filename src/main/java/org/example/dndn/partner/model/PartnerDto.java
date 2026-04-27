package org.example.dndn.partner.model;

import lombok.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PartnerDto {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Req {
        private String name;
        private String bizNumber;
        private String repName;
        private String contact;
        private String trade;
        private Long unitPrice;
        private LocalDate startDate;
        private LocalDate endDate;

        public Partner toEntity() {
            return Partner.builder()
                    .name(this.name)
                    .bizNumber(this.bizNumber)
                    .repName(this.repName)
                    .contact(this.contact)
                    .repName(this.repName)
                    .trade(this.trade)
                    .unitPrice(this.unitPrice)
                    .startDate(this.startDate)
                    .endDate(this.endDate)
                    .build();
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class EvalReq {
        private Integer qualityScore;
        private Integer safetyScore;
        private Integer scheduleScore;
        private Integer commScore;
        private String summary;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Res {
        private Long idx;
        private String name;
        private String bizNumber;
        private String repName;
        private String contact;
        private String trade;
        private Long unitPrice;
        private String period;
        private String status;
        private EvalRes evaluation;
        private List<FileRes> contractFiles;

        public static Res from(Partner entity, LocalDate today) {
            String period = "";

            if (entity.getStartDate() != null && entity.getEndDate() != null) {
                period = entity.getStartDate().format(DATE_FORMATTER) + " ~ "
                        + entity.getEndDate().format(DATE_FORMATTER);
            }

            List<FileRes> fileDto = new ArrayList<>();

            if (entity.getContractFiles() != null) {
                fileDto = entity.getContractFiles().stream()
                        .map(FileRes::from)
                        .toList();
            }

            return Res.builder()
                    .idx(entity.getIdx())
                    .name(entity.getName())
                    .bizNumber(entity.getBizNumber())
                    .repName(entity.getRepName())
                    .contact(entity.getContact())
                    .trade(entity.getTrade())
                    .unitPrice(entity.getUnitPrice())
                    .period(period)
                    .status(entity.resolveStatus(today).getLabel())
                    .evaluation(EvalRes.from(entity.getEvaluation()))
                    .contractFiles(fileDto)
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class partnerRes {
        private Long idx;
        private String name;
        private String repName;
        private String trade;
        private Long unitPrice;
        private String period;
        private String status;
        private String evaluation;

        public static partnerRes from(Partner entity, LocalDate today) {
            String period = "";

            if (entity.getStartDate() != null && entity.getEndDate() != null) {
                period = entity.getStartDate().format(DATE_FORMATTER) + " ~ "
                        + entity.getEndDate().format(DATE_FORMATTER);
            }

            String evaluation = "-";

            if (entity.getEvaluation() != null && entity.getEvaluation().getTotalScore() != null) {
                evaluation = entity.getEvaluation().getGrade() + " · "
                        + entity.getEvaluation().getTotalScore() + "점";
            }

            return partnerRes.builder()
                    .idx(entity.getIdx())
                    .name(entity.getName())
                    .repName(entity.getRepName())
                    .trade(entity.getTrade())
                    .unitPrice(entity.getUnitPrice())
                    .period(period)
                    .status(entity.resolveStatus(today).getLabel())
                    .evaluation(evaluation)
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EvalRes {
        private String grade;
        private Integer totalScore;
        private String lastEvaluatedAt;
        private String summary;
        private List<EvalItem> items;

        public static EvalRes from(PartnerEvaluation entity) {
            if (entity == null) {
                return null;
            }

            List<EvalItem> itemDto = new ArrayList<>();

            itemDto.add(EvalItem.builder()
                    .label("품질")
                    .score(entity.getQualityScore())
                    .build());

            itemDto.add(EvalItem.builder()
                    .label("안전")
                    .score(entity.getSafetyScore())
                    .build());

            itemDto.add(EvalItem.builder()
                    .label("일정")
                    .score(entity.getScheduleScore())
                    .build());

            itemDto.add(EvalItem.builder()
                    .label("소통")
                    .score(entity.getCommScore())
                    .build());

            String lastEvaluatedAt = entity.getLastEvaluatedAt() != null
                    ? entity.getLastEvaluatedAt().format(DATE_FORMATTER)
                    : "";

            return EvalRes.builder()
                    .grade(entity.getGrade())
                    .totalScore(entity.getTotalScore())
                    .lastEvaluatedAt(lastEvaluatedAt)
                    .summary(entity.getSummary())
                    .items(itemDto)
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EvalItem {
        private String label;
        private Integer score;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FileRes {
        private Long idx;
        private String fileName;
        private String fileUrl;
        private String fileSize;

        public static FileRes from(PartnerContractFile entity) {
            return FileRes.builder()
                    .idx(entity.getIdx())
                    .fileName(entity.getFileName())
                    .fileUrl(entity.getFileUrl())
                    .fileSize(formatSize(entity.getFileSize()))
                    .build();
        }

        private static String formatSize(Long bytes) {
            if (bytes == null) {
                return "";
            }

            if (bytes < 1024) {
                return bytes + "B";
            }

            if (bytes < 1024 * 1024) {
                return String.format("%.1fKB", bytes / 1024.0);
            }

            return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
        }
    }
}