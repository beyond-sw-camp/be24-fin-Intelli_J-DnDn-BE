package org.example.dndn.worker.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;
import org.example.dndn.worker.model.enums.AffiliationKind;
import org.example.dndn.worker.model.enums.AttendanceStatus;
import org.example.dndn.worker.model.enums.JobRank;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "worker")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Worker extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    /** 시연 픽스처·향후 연동 시 공통으로 쓰는 외부 식별자/사번 등 (sync 시 dedup key) */
    @Column(unique = true, length = 50)
    private String externalCode;

    /** 이름 */
    @Column(nullable = false, length = 30)
    private String name;

    /** 본인 연락처 */
    @Column(length = 20)
    private String phone;

    /** 비상 연락처 (번호) */
    @Column(length = 20)
    private String emergencyPhone;

    /** 비상 연락 관계 (예: 배우자, 자녀, 부모) */
    @Column(length = 20)
    private String emergencyRelation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private JobRank jobRank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AffiliationKind affiliationKind;

    /** 협력사명. 본사(DIRECT) 인 경우 null. (예: 태양건설, 대한건설, 미래건설) */
    @Column(length = 50)
    private String partnerCompany;

    /** 소속 부서/직종 라벨. 본사: 직영, 협력사: 목공/철근/용접/타일/인력 등 */
    @Column(length = 30)
    private String subLabel;

    /** 투입 현장 (예: 강남구 재건축 A공구) */
    @Column(length = 100)
    private String site;

    /** 혈액형 */
    @Column(length = 5)
    private String bloodType;

    /** 등록일 */
    private LocalDate registeredAt;

    /** 프로필 사진 URL */
    @Column(length = 500)
    private String profileImageUrl;

    /** 당월 누적 공수 캐시값 (실시간 합계는 AttendanceRecord 로 산출) */
    @Column(precision = 5, scale = 1)
    private BigDecimal monthTotalMan;

    public void updateFromSync(Worker incoming) {
        this.name = incoming.name;
        this.phone = incoming.phone;
        this.emergencyPhone = incoming.emergencyPhone;
        this.emergencyRelation = incoming.emergencyRelation;
        this.jobRank = incoming.jobRank;
        this.affiliationKind = incoming.affiliationKind;
        this.partnerCompany = incoming.partnerCompany;
        this.subLabel = incoming.subLabel;
        this.site = incoming.site;
        this.bloodType = incoming.bloodType;
        this.profileImageUrl = incoming.profileImageUrl;
    }
}
