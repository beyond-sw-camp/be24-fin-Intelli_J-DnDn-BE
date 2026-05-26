package org.example.dndncore.kafka.dto;

import lombok.Getter;
import org.example.dndncore.project.model.dto.TradeProcessDto;

import java.util.List;

@Getter
public class KafkaConsumerDto {
    private Long uploaderIdx;
    private Long projectIdx;
    private List<TradeProcessDto.Req> reqList;
}
