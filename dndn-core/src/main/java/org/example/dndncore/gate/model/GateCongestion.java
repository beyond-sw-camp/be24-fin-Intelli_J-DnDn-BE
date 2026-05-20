package org.example.dndncore.gate.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GateCongestion {
    SMOOTH("원활"),
    BUSY("혼잡"),
    CRITICAL("매우 혼잡");

    private final String label;
}
