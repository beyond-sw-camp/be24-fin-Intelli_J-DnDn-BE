package org.example.dndncore.ai.extractor;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.ai.dto.WorkPlanAiDto;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WorkPlanDocumentExtractor {

    private final OpenAiWorkPlanExtractor openAiWorkPlanExtractor;
    private final WorkPlanExcelFallbackParser workPlanExcelFallbackParser;

    public List<WorkPlanAiDto.Item> extract(
            File file,
            String planType,
            Integer year,
            Integer month,
            String selectedTradeName
    ) {
        validateFile(file);

        List<WorkPlanAiDto.Item> excelItems = workPlanExcelFallbackParser.extract(file, selectedTradeName);
        if (!excelItems.isEmpty()) {
            return excelItems;
        }

        List<WorkPlanAiDto.Item> aiItems = openAiWorkPlanExtractor.extractWorkPlan(
                file,
                planType,
                year,
                month,
                selectedTradeName
        );

        if (aiItems != null && !aiItems.isEmpty()) {
            return aiItems;
        }

        return List.of();
    }

    private void validateFile(File file) {
        if (file == null || !file.exists()) {
            throw new RuntimeException("분석할 작업 계획서 파일이 존재하지 않습니다.");
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
