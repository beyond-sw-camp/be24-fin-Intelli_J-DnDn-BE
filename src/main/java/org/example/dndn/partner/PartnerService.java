package org.example.dndn.partner;

import lombok.RequiredArgsConstructor;
import org.example.dndn.partner.model.Partner;
import org.example.dndn.partner.model.PartnerDto;
import org.example.dndn.partner.model.PartnerEvaluation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerService {

    private final PartnerRepository partnerRepository;

    // 협력사 등록
    @Transactional
    public Long create(PartnerDto.Req dto) {
        Partner partner = dto.toEntity();
        Partner savedPartner = partnerRepository.save(partner);

        return savedPartner.getIdx();
    }

    // 협력사 단일 조회
    public PartnerDto.Res read(Long partnerId) {
        Partner partner = findPartner(partnerId);

        return PartnerDto.Res.from(partner, LocalDate.now());
    }

    // 협력사 목록 조회
    public List<PartnerDto.partnerRes> list() {
        LocalDate today = LocalDate.now();

        return partnerRepository.findAll().stream()
                .map(partner -> PartnerDto.partnerRes.from(partner, today))
                .toList();
    }

    // 협력사 정보 수정
    @Transactional
    public void update(Long partnerId, PartnerDto.Req dto) {
        Partner partner = findPartner(partnerId);

        partner.updateInfo(
                dto.getName(),
                dto.getBizNumber(),
                dto.getRepName(),
                dto.getContact(),
                dto.getTrade(),
                dto.getUnitPrice(),
                dto.getStartDate(),
                dto.getEndDate()
        );
    }

    // 협력사 평가 등록/수정
    @Transactional
    public void evaluate(Long partnerId, PartnerDto.EvalReq dto) {
        Partner partner = findPartner(partnerId);

        PartnerEvaluation evaluation = partner.getEvaluation();

        if (evaluation == null) {
            evaluation = PartnerEvaluation.builder().build();
            partner.attachEvaluation(evaluation);
        }

        evaluation.update(
                dto.getQualityScore(),
                dto.getSafetyScore(),
                dto.getScheduleScore(),
                dto.getCommScore(),
                dto.getSummary(),
                LocalDate.now()
        );
    }

    // 협력사 삭제
    @Transactional
    public void delete(Long partnerId) {
        Partner partner = findPartner(partnerId);

        partnerRepository.delete(partner);
    }

    private Partner findPartner(Long partnerId) {
        return partnerRepository.findById(partnerId)
                .orElseThrow(() -> new RuntimeException("협력사를 찾을 수 없습니다."));
    }
}