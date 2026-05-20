package org.example.dndncore.gate.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;

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
    public void toggle() {
        this.active = !this.active;
    }
    void bindGate(Gate gate) {
        this.gate = gate;
    }
}