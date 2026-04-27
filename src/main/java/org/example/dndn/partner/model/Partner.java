package org.example.dndn.partner.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "partner")
public class Partner extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    private String name;          // 협력사명
    private String bizNumber;     // 사업자 번호
    private String repName;       // 대표자명
    private String contact;       // 연락처
    private String trade;         // 담당 공종
    private Long unitPrice;       // 계약 단가(원)

    private LocalDate startDate;  // 계약 시작일
    private LocalDate endDate;    // 계약 종료일

    @OneToMany(mappedBy = "partner", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PartnerContractFile> contractFiles = new ArrayList<>();

    @OneToOne(mappedBy = "partner", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private PartnerEvaluation evaluation;

    /**
     * 계약 상태 동적 계산
     * - 만료 예정: 종료일 30일 이내
     * - 계약 종료: 종료일 경과
     * - 계약 유지: 그 외
     */
    public PartnerStatus resolveStatus(LocalDate today) {
        if (endDate == null) {
            return PartnerStatus.ACTIVE;
        }
        if (today.isAfter(endDate)) {
            return PartnerStatus.ENDED;
        }
        if (!today.plusDays(30).isBefore(endDate)) {
            return PartnerStatus.EXPIRING;
        }
        return PartnerStatus.ACTIVE;
    }

    /**
     * 협력사 기본 정보 수정
     */
    public void updateInfo(String name, String bizNumber, String repName, String contact,
                           String trade, Long unitPrice, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.bizNumber = bizNumber;
        this.repName = repName;
        this.contact = contact;
        this.trade = trade;
        this.unitPrice = unitPrice;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * 평가 등록/연결 (다른 도메인 로직으로 생성된 PartnerEvaluation 연결)
     */
    public void attachEvaluation(PartnerEvaluation evaluation) {
        this.evaluation = evaluation;
        evaluation.bindPartner(this);
    }
}