package org.example.dndn.partner.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PartnerStatus {
    ACTIVE("계약 중"),
    EXPIRING("만료 예정"),
    ENDED("계약 종료");

    private final String label;
}