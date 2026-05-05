package org.example.dndn.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.example.dndn.project.model.enums.DocType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 공정표 PDF → OpenAI Vision → TradeProcess 호환 JSON
 *
 * 기존과 달라진 점:
 *  - 단일 docType 파일 1개를 분석 (기존: 3종류 묶음 분석)
 *  - 반환 JSON이 TradeProcessDto.Req 필드와 일치
 *  - docType별 프롬프트가 다름
 */
@Service
public class PdfAnalysisService {

    @Value("${openai.api.key}")
    private String openAiApiKey;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 단일 파일 분석 — docType에 따라 프롬프트가 달라짐
     *
     * @param file    업로드된 PDF 파일
     * @param docType 문서 종류 (MASTER / MILESTONE / WEIGHT / TRADE_PLAN)
     * @return JSON 배열 — 각 원소가 TradeProcessDto.Req에 매핑됨
     */
    public JsonNode analyzeSingleDocument(MultipartFile file, DocType docType) throws Exception {
        List<String> base64Images = convertPdfToBase64Images(file);
        return callOpenAi(base64Images, docType);
    }

    // ─────────────────────────────────────────────────────────────
    // PDF → base64 이미지 변환
    // ─────────────────────────────────────────────────────────────

    private List<String> convertPdfToBase64Images(MultipartFile file) throws Exception {
        List<String> images = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 100);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "PNG", baos);
                images.add(Base64.getEncoder().encodeToString(baos.toByteArray()));
            }
        }
        return images;
    }

    // ─────────────────────────────────────────────────────────────
    // OpenAI Vision API 호출
    // ─────────────────────────────────────────────────────────────

    private JsonNode callOpenAi(List<String> base64Images, DocType docType) throws Exception {
        List<Object> contentList = new ArrayList<>();

        // 이미지 추가
        for (String b64 : base64Images) {
            contentList.add(objectMapper.createObjectNode()
                    .put("type", "image_url")
                    .set("image_url", objectMapper.createObjectNode()
                            .put("url", "data:image/png;base64," + b64)
                            .put("detail", "high")));
        }

        // docType별 프롬프트 추가
        contentList.add(objectMapper.createObjectNode()
                .put("type", "text")
                .put("text", buildPrompt(docType)));

        String requestBody = objectMapper.writeValueAsString(
                objectMapper.createObjectNode()
                        .put("model", "gpt-4o")
                        .put("max_tokens", 4000)
                        .set("messages", objectMapper.createArrayNode()
                                .add(objectMapper.createObjectNode()
                                        .put("role", "user")
                                        .set("content", objectMapper.valueToTree(contentList))))
        );

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + openAiApiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);

            String content = root
                    .path("choices").get(0)
                    .path("message")
                    .path("content")
                    .asText();

            // 마크다운 코드블록 제거
            content = content
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            return objectMapper.readTree(content);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // docType별 프롬프트
    // ─────────────────────────────────────────────────────────────

    private String buildPrompt(DocType docType) {
        String commonFooter = """

                값이 없거나 읽기 어려우면 null을 사용하세요.
                반드시 JSON 배열만 반환하고 마크다운 코드블록 없이 순수 JSON만 출력하세요.
                날짜는 반드시 "YYYY-MM-DD" 형식으로 출력하세요.
                """;

        return switch (docType) {
            case MASTER -> """
                    당신은 한국 건설현장 마스터 공정표 분석 전문가입니다.
                    위 이미지는 마스터 공정표입니다. 전체 공사의 공종별 작업 목록과 일정을 추출하세요.

                    공종(tradeName)은 반드시 아래 중 하나만 사용하세요:
                    형틀, 전기, 방수, 골조, 설비, 철근, 토공, 마감, 기타

                    각 행/작업을 하나의 배열 원소로 추출하세요:
                    [
                      {
                        "tradeName": "공종명",
                        "processName": "작업명/공정명",
                        "plannedStart": "YYYY-MM-DD",
                        "plannedEnd": "YYYY-MM-DD",
                        "weightPct": 보할율(소수, 예: 12.5),
                        "isMilestone": false,
                        "partnerCompany": null
                      }
                    ]
                    """ + commonFooter;

            case MILESTONE -> """
                    당신은 한국 건설현장 마일스톤 공정표 분석 전문가입니다.
                    위 이미지는 마일스톤 공정표입니다. 주요 마일스톤(착공, 골조완료, 준공 등) 항목을 추출하세요.

                    공종(tradeName)은 반드시 아래 중 하나만 사용하세요:
                    형틀, 전기, 방수, 골조, 설비, 철근, 토공, 마감, 기타

                    각 마일스톤을 하나의 배열 원소로 추출하세요:
                    [
                      {
                        "tradeName": "공종명",
                        "processName": "마일스톤명 (예: 지하층 골조 완료)",
                        "plannedStart": "YYYY-MM-DD",
                        "plannedEnd": "YYYY-MM-DD",
                        "weightPct": null,
                        "isMilestone": true,
                        "partnerCompany": null
                      }
                    ]
                    """ + commonFooter;

            case WEIGHT -> """
                    당신은 한국 건설현장 보할 공정표 분석 전문가입니다.
                    위 이미지는 보할 공정표입니다. 공종별 보할율(공사 비중 %)과 일정을 추출하세요.

                    공종(tradeName)은 반드시 아래 중 하나만 사용하세요:
                    형틀, 전기, 방수, 골조, 설비, 철근, 토공, 마감, 기타

                    각 행을 하나의 배열 원소로 추출하세요:
                    [
                      {
                        "tradeName": "공종명",
                        "processName": "공정명",
                        "plannedStart": "YYYY-MM-DD",
                        "plannedEnd": "YYYY-MM-DD",
                        "weightPct": 보할율(소수, 예: 8.0),
                        "isMilestone": false,
                        "partnerCompany": null
                      }
                    ]
                    """ + commonFooter;

            case TRADE_PLAN -> """
                    당신은 한국 건설현장 공종별 시공계획서 분석 전문가입니다.
                    위 이미지는 공종별 시공계획서입니다. 협력사명, 공종, 공정명, 일정을 추출하세요.

                    공종(tradeName)은 반드시 아래 중 하나만 사용하세요:
                    형틀, 전기, 방수, 골조, 설비, 철근, 토공, 마감, 기타

                    각 공정을 하나의 배열 원소로 추출하세요:
                    [
                      {
                        "tradeName": "공종명",
                        "processName": "공정명",
                        "plannedStart": "YYYY-MM-DD",
                        "plannedEnd": "YYYY-MM-DD",
                        "weightPct": null,
                        "isMilestone": false,
                        "partnerCompany": "협력사명 (문서에 없으면 null)"
                      }
                    ]
                    """ + commonFooter;
        };
    }
}
