package org.example.dndncore.ai.extractor;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.project.model.dto.TradeProcessDto;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ScheduleDocumentExtractor {

    private final OpenAiScheduleExtractor openAiScheduleExtractor;

    public List<TradeProcessDto.Req> extract(File file, Long masterScheduleId) {
        validateFile(file);

        return openAiScheduleExtractor.extractSchedule(file, masterScheduleId);
    }

    private void validateFile(File file) {
        if (file == null || !file.exists()) {
            throw new RuntimeException("분석할 파일이 존재하지 않습니다.");
        }

        String fileName = file.getName().toLowerCase();

        boolean supported =
                fileName.endsWith(".xlsx") ||
                        fileName.endsWith(".xls") ||
                        fileName.endsWith(".pdf") ||
                        fileName.endsWith(".png") ||
                        fileName.endsWith(".jpg") ||
                        fileName.endsWith(".jpeg");

        if (!supported) {
            throw new RuntimeException("지원하지 않는 파일 형식입니다. 엑셀, PDF, 이미지만 업로드할 수 있습니다.");
        }
    }
}