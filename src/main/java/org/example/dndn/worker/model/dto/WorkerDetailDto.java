package org.example.dndn.worker.model.dto;

import lombok.*;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.model.enums.AffiliationKind;
import org.example.dndn.worker.model.enums.JobRank;

import java.math.BigDecimal;
import java.time.LocalDate;

public class WorkerDetailDto {

    // MANAGEMENT_004 작업자 상세 프로필 조회 응답.
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProfileRes {
        private Long idx;
        private String name;
        private AffiliationKind affiliationKind;
        private String partnerCompany;
        private JobRank jobRank;
        private String site;
        private String phone;
        private String emergencyPhone;
        private String emergencyRelation;
        private String bloodType;
        private LocalDate registeredAt;
        private String profileImageUrl;
        private BigDecimal monthTotalMan;

        public static ProfileRes from(Worker w) {
            return ProfileRes.builder()
                    .idx(w.getIdx())
                    .name(w.getName())
                    .affiliationKind(w.getAffiliationKind())
                    .partnerCompany(w.getPartnerCompany())
                    .jobRank(w.getJobRank())
                    .site(w.getSite())
                    .phone(w.getPhone())
                    .emergencyPhone(w.getEmergencyPhone())
                    .emergencyRelation(w.getEmergencyRelation())
                    .bloodType(w.getBloodType())
                    .registeredAt(w.getRegisteredAt())
                    .profileImageUrl(w.getProfileImageUrl())
                    .monthTotalMan(w.getMonthTotalMan())
                    .build();
        }
    }
}
