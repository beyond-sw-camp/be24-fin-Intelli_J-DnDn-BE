package org.example.dndncore.ai.extractor;

import org.example.dndncore.ai.dto.WorkPlanAiDto;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class WorkPlanExcelFallbackParser {

    private static final String SPREADSHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String DRAWING_NS = "http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing";
    private static final String DRAWING_MAIN_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final Pattern SHEET_PATTERN = Pattern.compile("xl/worksheets/sheet\\d+\\.xml");
    private static final Pattern CELL_REF_PATTERN = Pattern.compile("([A-Z]+)(\\d+)");
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    public List<WorkPlanAiDto.Item> extract(File file, String selectedTradeName) {
        String fileName = file.getName().toLowerCase();
        if (!fileName.endsWith(".xlsx")) {
            return List.of();
        }

        try (ZipFile zipFile = new ZipFile(file)) {
            List<String> sharedStrings = readSharedStrings(zipFile);
            List<WorkPlanAiDto.Item> items = new ArrayList<>();

            zipFile.stream()
                    .filter(entry -> SHEET_PATTERN.matcher(entry.getName()).matches())
                    .forEach(entry -> items.addAll(readSheet(zipFile, entry, sharedStrings, selectedTradeName)));

            return items;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> readSharedStrings(ZipFile zipFile) throws Exception {
        ZipEntry entry = zipFile.getEntry("xl/sharedStrings.xml");
        if (entry == null) {
            return List.of();
        }

        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            Document document = parseXml(inputStream);
            NodeList stringItems = document.getElementsByTagNameNS(SPREADSHEET_NS, "si");
            List<String> sharedStrings = new ArrayList<>();

            for (int i = 0; i < stringItems.getLength(); i++) {
                Element stringItem = (Element) stringItems.item(i);
                NodeList texts = stringItem.getElementsByTagNameNS(SPREADSHEET_NS, "t");
                StringBuilder value = new StringBuilder();
                for (int j = 0; j < texts.getLength(); j++) {
                    value.append(texts.item(j).getTextContent());
                }
                sharedStrings.add(value.toString());
            }

            return sharedStrings;
        }
    }

    private List<WorkPlanAiDto.Item> readSheet(
            ZipFile zipFile,
            ZipEntry sheetEntry,
            List<String> sharedStrings,
            String selectedTradeName
    ) {
        try (InputStream inputStream = zipFile.getInputStream(sheetEntry)) {
            Document document = parseXml(inputStream);
            NodeList rows = document.getElementsByTagNameNS(SPREADSHEET_NS, "row");
            Map<Integer, Map<Integer, String>> table = new HashMap<>();
            int maxRow = 0;
            int maxColumn = 0;

            for (int i = 0; i < rows.getLength(); i++) {
                Element row = (Element) rows.item(i);
                int rowIndex = Integer.parseInt(row.getAttribute("r"));
                maxRow = Math.max(maxRow, rowIndex);

                NodeList cells = row.getElementsByTagNameNS(SPREADSHEET_NS, "c");
                Map<Integer, String> rowValues = new HashMap<>();

                for (int j = 0; j < cells.getLength(); j++) {
                    Element cell = (Element) cells.item(j);
                    CellRef cellRef = parseCellRef(cell.getAttribute("r"));
                    if (cellRef == null) {
                        continue;
                    }

                    maxColumn = Math.max(maxColumn, cellRef.column());
                    String value = readCellValue(cell, sharedStrings);
                    if (value != null && !value.isBlank()) {
                        rowValues.put(cellRef.column(), value.trim());
                    }
                }

                if (!rowValues.isEmpty()) {
                    table.put(rowIndex, rowValues);
                }
            }

            List<WorkPlanAiDto.Item> drawingItems = readDrawingItems(zipFile, table, selectedTradeName);
            if (!drawingItems.isEmpty()) {
                return drawingItems;
            }

            return extractItems(table, maxRow, maxColumn, selectedTradeName);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<WorkPlanAiDto.Item> readDrawingItems(
            ZipFile zipFile,
            Map<Integer, Map<Integer, String>> table,
            String selectedTradeName
    ) {
        Map<Integer, LocalDate> dateByColumn = findDateColumns(table);
        if (dateByColumn.isEmpty()) {
            return List.of();
        }

        List<WorkPlanAiDto.Item> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.getName().startsWith("xl/drawings/") || !entry.getName().endsWith(".xml")) {
                continue;
            }

            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                Document document = parseXml(inputStream);
                NodeList anchors = document.getElementsByTagNameNS(DRAWING_NS, "twoCellAnchor");

                for (int i = 0; i < anchors.getLength(); i++) {
                    Element anchor = (Element) anchors.item(i);
                    Element from = firstChild(anchor, DRAWING_NS, "from");
                    Element to = firstChild(anchor, DRAWING_NS, "to");
                    if (from == null || to == null) {
                        continue;
                    }

                    int fromColumn = intChild(from, "col", -1) + 1;
                    int toColumn = intChild(to, "col", -1);
                    int fromRow = intChild(from, "row", -1) + 1;
                    String geometry = geometry(anchor);
                    String taskName = drawingText(anchor);

                    if (!"rect".equals(geometry) || !isTaskName(taskName)) {
                        continue;
                    }

                    LocalDate startDate = dateByColumn.get(fromColumn);
                    LocalDate endDate = dateByColumn.get(toColumn);
                    if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
                        continue;
                    }

                    String tradeName = firstNonBlank(tradeAtRow(table, fromRow), selectedTradeName, "기타");
                    String key = tradeName + "|" + taskName + "|" + startDate + "|" + endDate;
                    if (!seen.add(key)) {
                        continue;
                    }

                    items.add(WorkPlanAiDto.Item.builder()
                            .tradeName(tradeName)
                            .tradeProcessName(tradeName)
                            .name(taskName)
                            .location(null)
                            .startDate(startDate)
                            .endDate(endDate)
                            .note("엑셀 도형 기준 자동 추출")
                            .build());
                }
            } catch (Exception ignored) {
                // Ignore malformed drawing parts and keep trying other sheets/parts.
            }
        }

        return items;
    }

    private List<WorkPlanAiDto.Item> extractItems(
            Map<Integer, Map<Integer, String>> table,
            int maxRow,
            int maxColumn,
            String selectedTradeName
    ) {
        List<WorkPlanAiDto.Item> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String currentTrade = firstNonBlank(selectedTradeName, "");

        for (int rowIndex = 1; rowIndex <= maxRow; rowIndex++) {
            String tradeCandidate = firstNonBlank(cell(table, rowIndex, 3), cell(table, rowIndex, 2));
            if (isTradeLabel(tradeCandidate)) {
                currentTrade = tradeCandidate;
            }

            LocalDate startDate = parseExcelDate(cell(table, rowIndex, 4));
            if (startDate == null) {
                continue;
            }

            for (int column = 5; column <= maxColumn; column++) {
                Double duration = parseNumber(cell(table, rowIndex, column));
                if (duration == null || duration < 1 || duration > 120) {
                    continue;
                }

                String taskName = findTaskName(table, rowIndex, column);
                if (!isTaskName(taskName)) {
                    continue;
                }

                LocalDate endDate = startDate.plusDays((long) Math.ceil(duration) - 1);
                String tradeName = firstNonBlank(currentTrade, selectedTradeName, "기타");
                String key = tradeName + "|" + taskName + "|" + startDate + "|" + endDate;
                if (!seen.add(key)) {
                    continue;
                }

                items.add(WorkPlanAiDto.Item.builder()
                        .tradeName(tradeName)
                        .tradeProcessName(tradeName)
                        .name(taskName)
                        .location(null)
                        .startDate(startDate)
                        .endDate(endDate)
                        .note("엑셀 템플릿에서 자동 추출")
                        .build());
            }
        }

        return items;
    }

    private String findTaskName(Map<Integer, Map<Integer, String>> table, int rowIndex, int column) {
        int[] offsets = {-1, 1, -2, 2};
        for (int offset : offsets) {
            String value = cell(table, rowIndex + offset, column);
            if (isTaskName(value)) {
                return value;
            }
        }
        return null;
    }

    private Map<Integer, LocalDate> findDateColumns(Map<Integer, Map<Integer, String>> table) {
        Map<Integer, LocalDate> best = new HashMap<>();

        for (Map<Integer, String> rowValues : table.values()) {
            Map<Integer, LocalDate> dates = new HashMap<>();

            for (Map.Entry<Integer, String> cell : rowValues.entrySet()) {
                LocalDate date = parseExcelDate(cell.getValue());
                if (date != null) {
                    dates.put(cell.getKey(), date);
                }
            }

            if (dates.size() > best.size()) {
                best = dates;
            }
        }

        return best.size() >= 5 ? best : Map.of();
    }

    private String tradeAtRow(Map<Integer, Map<Integer, String>> table, int rowIndex) {
        for (int row = rowIndex; row >= 1; row--) {
            String trade = firstNonBlank(cell(table, row, 3), cell(table, row, 2));
            if (isTradeLabel(trade)) {
                return trade;
            }
        }
        return null;
    }

    private String readCellValue(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");

        if ("inlineStr".equals(type)) {
            NodeList texts = cell.getElementsByTagNameNS(SPREADSHEET_NS, "t");
            StringBuilder value = new StringBuilder();
            for (int i = 0; i < texts.getLength(); i++) {
                value.append(texts.item(i).getTextContent());
            }
            return value.toString();
        }

        NodeList values = cell.getElementsByTagNameNS(SPREADSHEET_NS, "v");
        if (values.getLength() == 0) {
            return "";
        }

        String value = values.item(0).getTextContent();
        if ("s".equals(type)) {
            int index = Integer.parseInt(value);
            return index >= 0 && index < sharedStrings.size() ? sharedStrings.get(index) : "";
        }

        return value;
    }

    private Element firstChild(Element parent, String namespace, String tagName) {
        NodeList nodes = parent.getElementsByTagNameNS(namespace, tagName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private int intChild(Element parent, String tagName, int fallback) {
        Element child = firstChild(parent, DRAWING_NS, tagName);
        if (child == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(child.getTextContent());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String geometry(Element anchor) {
        Element geometry = firstChild(anchor, DRAWING_MAIN_NS, "prstGeom");
        return geometry == null ? "" : geometry.getAttribute("prst");
    }

    private String drawingText(Element anchor) {
        NodeList texts = anchor.getElementsByTagNameNS(DRAWING_MAIN_NS, "t");
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < texts.getLength(); i++) {
            value.append(texts.item(i).getTextContent());
        }
        return value.toString().trim();
    }

    private Document parseXml(InputStream inputStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(inputStream);
    }

    private CellRef parseCellRef(String ref) {
        Matcher matcher = CELL_REF_PATTERN.matcher(ref);
        if (!matcher.matches()) {
            return null;
        }
        return new CellRef(Integer.parseInt(matcher.group(2)), columnIndex(matcher.group(1)));
    }

    private int columnIndex(String letters) {
        int result = 0;
        for (int i = 0; i < letters.length(); i++) {
            result = result * 26 + (letters.charAt(i) - 'A' + 1);
        }
        return result;
    }

    private String cell(Map<Integer, Map<Integer, String>> table, int row, int column) {
        Map<Integer, String> rowValues = table.get(row);
        return rowValues == null ? null : rowValues.get(column);
    }

    private LocalDate parseExcelDate(String value) {
        Double number = parseNumber(value);
        if (number != null && number >= 20000 && number <= 80000) {
            return EXCEL_EPOCH.plusDays(number.longValue());
        }

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double parseNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isTradeLabel(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String trimmed = value.trim();
        if (trimmed.length() < 2 || isExcludedText(trimmed)) {
            return false;
        }

        return trimmed.contains("공사")
                || trimmed.contains("건축")
                || trimmed.contains("토목")
                || trimmed.contains("전기")
                || trimmed.contains("설비")
                || trimmed.contains("기계")
                || trimmed.contains("골조")
                || trimmed.contains("마감");
    }

    private boolean isTaskName(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String trimmed = value.trim();
        if (trimmed.length() < 2 || isExcludedText(trimmed)) {
            return false;
        }

        return !trimmed.matches("[가-힣]");
    }

    private boolean isExcludedText(String value) {
        return value.equals("시작일")
                || value.equals("비고")
                || value.equals("MileStone")
                || value.equals("구정명절")
                || value.equals("공정표작성_작업일수입력")
                || value.startsWith("월간 공정표")
                || value.contains("공   종")
                || value.matches("[월화수목금토일]");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record CellRef(int row, int column) {
    }
}
