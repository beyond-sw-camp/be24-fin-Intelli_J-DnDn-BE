package org.example.dndn.gate.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "gate_machine")
public class GateMachine extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    private boolean active;     // 가동 여부 (ON/OFF)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_idx")
    private Gate gate;

    /**
     * 가동 상태 토글
     */
    public void toggle() {
        this.active = !this.active;
    }

    /**
     * 부모 게이트 연결 (Gate.attachMachine 내부 호출 전용)
     */
    void bindGate(Gate gate) {
        this.gate = gate;
    }
}