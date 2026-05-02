package org.example.dndn.worker.fixture;

import lombok.*;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.model.enums.AffiliationKind;
import org.example.dndn.worker.model.enums.JobRank;

/**
 * 시연용 픽스처 **한 명** 분량. REST Req/Res 가 아님.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerScenarioFixtureRow {

    private String externalCode;
    private String name;
    private String phone;
    private String emergencyPhone;
    private String emergencyRelation;
    private JobRank jobRank;
    private AffiliationKind affiliationKind;
    private String partnerCompany;
    private String subLabel;
    private String site;
    private String bloodType;
    private String profileImageUrl;

    public Worker toWorkerEntity() {
        return Worker.builder()
                .externalCode(this.externalCode)
                .name(this.name)
                .phone(this.phone)
                .emergencyPhone(this.emergencyPhone)
                .emergencyRelation(this.emergencyRelation)
                .jobRank(this.jobRank)
                .affiliationKind(this.affiliationKind)
                .partnerCompany(this.partnerCompany)
                .subLabel(this.subLabel)
                .site(this.site)
                .bloodType(this.bloodType)
                .profileImageUrl(this.profileImageUrl)
                .build();
    }
}