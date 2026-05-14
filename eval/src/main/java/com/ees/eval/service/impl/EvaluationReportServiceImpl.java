package com.ees.eval.service.impl;

import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.EvaluationResultDTO;
import com.ees.eval.service.EvaluationReportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xddf.usermodel.chart.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EvaluationReportServiceImpl implements EvaluationReportService {

    private enum GradeColor {
        S("10B981"), A("A855F7"), B("3B82F6"), C("F59E0B"), D("EF4444");

        private final String hex;

        GradeColor(String hex) { this.hex = hex; }

        public String getHex() { return hex; }

        public static String getHexByGrade(String grade) {
            try {
                return GradeColor.valueOf(grade).getHex();
            } catch (IllegalArgumentException | NullPointerException e) {
                return null;
            }
        }
    }

    @Override
    public void generatePremiumReport(EvaluationPeriodDTO period, List<EvaluationResultDTO> results, HttpServletResponse response) throws IOException {
        
        try (Workbook workbook = new XSSFWorkbook()) {
            // 시트 생성
            createSummarySheet(workbook, period, results);
            
            // 데이터 부서별 그룹핑 및 시트 생성
            Map<String, List<EvaluationResultDTO>> groupedByDept = results.stream()
                    .collect(Collectors.groupingBy(r -> r.deptName() != null ? r.deptName() : "미지정"));
            
            for (Map.Entry<String, List<EvaluationResultDTO>> entry : groupedByDept.entrySet()) {
                createDetailedSheet(workbook, entry.getKey(), entry.getValue());
            }

            // 파일명 설정
            String fileName = String.format("인사평가결과보고서_%d_%s.xlsx", period.periodYear(), period.periodName());
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);
            
            workbook.write(response.getOutputStream());
            response.getOutputStream().flush();
        }
    }

    /**
     * [Sheet 1] 대시보드 요약 시트 생성
     */
    private void createSummarySheet(Workbook workbook, EvaluationPeriodDTO period, List<EvaluationResultDTO> results) {
        Sheet sheet = workbook.createSheet("평가 요약");
        sheet.setDisplayGridlines(false); // 그리드 라인 숨김 (리포트 느낌)

        // 1. 제목
        Row titleRow = sheet.createRow(1);
        Cell titleCell = titleRow.createCell(1);
        titleCell.setCellValue("📊 인사 평가 결과 요약 보고서 (" + period.periodYear() + " " + period.periodName() + ")");
        titleCell.setCellStyle(createTitleStyle(workbook));
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 1, 5));

        // 2. 등급 분포 통계
        Row labelRow = sheet.createRow(3);
        labelRow.createCell(1).setCellValue("■ 전사 등급 분포 현황");
        labelRow.getCell(1).setCellStyle(createLabelStyle(workbook));

        Map<String, Long> gradeDist = results.stream()
                .filter(r -> r.isConfirmed() && r.gradeCode() != null && !r.gradeCode().isBlank())
                .collect(Collectors.groupingBy(EvaluationResultDTO::gradeCode, Collectors.counting()));

        String[] grades = {"S", "A", "B", "C", "D"};
        Row headerRow = sheet.createRow(4);
        Row dataRow = sheet.createRow(5);
        CellStyle tableHeader = createTableHeaderStyle(workbook, IndexedColors.CORNFLOWER_BLUE);
        CellStyle tableBody = createTableBodyStyle(workbook);

        for (int i = 0; i < grades.length; i++) {
            Cell h = headerRow.createCell(i + 1);
            h.setCellValue(grades[i] + " 등급");
            h.setCellStyle(tableHeader);

            Cell d = dataRow.createCell(i + 1);
            d.setCellValue(gradeDist.getOrDefault(grades[i], 0L));
            
            CellStyle numStyle = workbook.createCellStyle();
            numStyle.cloneStyleFrom(tableBody);
            numStyle.setDataFormat(workbook.createDataFormat().getFormat("0 \"명\""));
            d.setCellStyle(numStyle);
        }

        // 3. 부서별 평균 (Top 5)
        Row deptLabelRow = sheet.createRow(7);
        deptLabelRow.createCell(1).setCellValue("■ 부서별 성과 요약 (평균 점수)");
        deptLabelRow.getCell(1).setCellStyle(createLabelStyle(workbook));

        Map<String, Double> deptAvg = results.stream()
                .filter(r -> r.deptName() != null && !r.deptName().endsWith("본부") && !r.deptName().endsWith("부서"))
                .collect(Collectors.groupingBy(EvaluationResultDTO::deptName,
                        Collectors.averagingDouble(r -> r.totalScore() != null ? r.totalScore().doubleValue() : 0.0)));

        List<Map.Entry<String, Double>> sortedDept = deptAvg.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .collect(Collectors.toList());

        Row deptHeader = sheet.createRow(8);
        deptHeader.createCell(1).setCellValue("순위");
        deptHeader.createCell(2).setCellValue("부서명");
        deptHeader.createCell(3).setCellValue("평균 점수");
        deptHeader.getCell(1).setCellStyle(tableHeader);
        deptHeader.getCell(2).setCellStyle(tableHeader);
        deptHeader.getCell(3).setCellStyle(tableHeader);

        int rowIdx = 9;
        int rank = 1;
        for (Map.Entry<String, Double> entry : sortedDept) {
            Row r = sheet.createRow(rowIdx++);
            r.createCell(1).setCellValue(rank++);
            r.createCell(2).setCellValue(entry.getKey());
            r.createCell(3).setCellValue(Math.round(entry.getValue() * 100) / 100.0);
            r.getCell(1).setCellStyle(tableBody);
            r.getCell(2).setCellStyle(tableBody);
            r.getCell(3).setCellStyle(tableBody);
        }

        // --- 차트 생성 영역 ---
        if (sheet instanceof XSSFSheet xssfSheet) {
            XSSFDrawing drawing = xssfSheet.createDrawingPatriarch();
            
            // 1. 전사 등급 분포 (Doughnut Chart)
            // 위치: B12 ~ G26 (Col 1~6, Row 11~25)
            XSSFClientAnchor anchor1 = drawing.createAnchor(0, 0, 0, 0, 1, 11, 7, 26);
            XSSFChart chart1 = drawing.createChart(anchor1);
            chart1.setTitleText("전사 등급 분포");
            chart1.setTitleOverlay(false);
            
            XDDFChartLegend legend1 = chart1.getOrAddLegend();
            legend1.setPosition(LegendPosition.RIGHT);

            XDDFDataSource<String> cat1 = XDDFDataSourcesFactory.fromStringCellRange(xssfSheet, new CellRangeAddress(4, 4, 1, 5));
            XDDFNumericalDataSource<Double> val1 = XDDFDataSourcesFactory.fromNumericCellRange(xssfSheet, new CellRangeAddress(5, 5, 1, 5));

            XDDFDoughnutChartData data1 = (XDDFDoughnutChartData) chart1.createData(ChartTypes.DOUGHNUT, null, null);
            data1.setVaryColors(true); 
            data1.setHoleSize(50); 
            XDDFDoughnutChartData.Series series1 = (XDDFDoughnutChartData.Series) data1.addSeries(cat1, val1);
            series1.setTitle("등급 분포", null);
            
            // --- 각 슬라이스별 색상 지정 (S, A, B, C, D 순서) ---
            GradeColor[] colors = GradeColor.values();
            for (int i = 0; i < colors.length; i++) {
                setSliceColor(series1, i, colors[i].getHex());
            }

            chart1.plot(data1);

            // 2. 부서별 평균 점수 (Bar Chart)
            if (sortedDept.size() > 0) {
                int lastRow = 8 + sortedDept.size();
                // 위치: I12 ~ N26 (Col 8~13, Row 11~25)
                XSSFClientAnchor anchor2 = drawing.createAnchor(0, 0, 0, 0, 8, 11, 14, 26);
                XSSFChart chart2 = drawing.createChart(anchor2);
                chart2.setTitleText("부서별 평균 점수");
                chart2.setTitleOverlay(false);
                
                XDDFCategoryAxis bottomAxis = chart2.createCategoryAxis(AxisPosition.BOTTOM);
                bottomAxis.setCrosses(AxisCrosses.AUTO_ZERO);
                
                XDDFValueAxis leftAxis = chart2.createValueAxis(AxisPosition.LEFT);
                leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);
                leftAxis.setCrossBetween(AxisCrossBetween.BETWEEN); // 막대가 눈금선 '사이'에 오도록 정렬
                
                // 두 축을 반드시 서로 교차(Cross) 연결해야 차트가 공중에 뜨지 않고 정상 결합됩니다.
                bottomAxis.crossAxis(leftAxis);
                leftAxis.crossAxis(bottomAxis);
                
                XDDFDataSource<String> cat2 = XDDFDataSourcesFactory.fromStringCellRange(xssfSheet, new CellRangeAddress(9, lastRow, 2, 2));
                XDDFNumericalDataSource<Double> val2 = XDDFDataSourcesFactory.fromNumericCellRange(xssfSheet, new CellRangeAddress(9, lastRow, 3, 3));
                
                XDDFBarChartData data2 = (XDDFBarChartData) chart2.createData(ChartTypes.BAR, bottomAxis, leftAxis);
                data2.setBarDirection(BarDirection.COL);
                data2.setBarGrouping(BarGrouping.STANDARD);
                data2.setVaryColors(false);
                
                XDDFBarChartData.Series series2 = (XDDFBarChartData.Series) data2.addSeries(cat2, val2);
                series2.setTitle("평균 점수", null);
                chart2.plot(data2);
            }
        }

        sheet.setColumnWidth(1, 3000);
        sheet.setColumnWidth(2, 6000);
        sheet.setColumnWidth(3, 4000);
    }

    /**
     * [Sheet 2 ~ N] 부서별 상세 결과 시트 생성 (통합 표 구조)
     */
    private void createDetailedSheet(Workbook workbook, String sheetName, List<EvaluationResultDTO> results) {
        String safeSheetName = sheetName.replaceAll("[:\\\\/?*\\[\\]]", "_");
        if (safeSheetName.length() > 31) {
            safeSheetName = safeSheetName.substring(0, 31);
        }
        Sheet sheet = workbook.createSheet(safeSheetName);
        
        CellStyle headerStyle = createTableHeaderStyle(workbook, IndexedColors.GREY_50_PERCENT);
        CellStyle bodyStyle = createTableBodyStyle(workbook);

        // 1. 그룹 헤더
        Row row0 = sheet.createRow(0);
        String[] groups = new String[]{"피평가자 정보", "", "", "", "", "성과평가 (MBO)", "", "", "역량평가 (COMP)", "", "", "다면평가 (MULTI)", "", "", "종합결과", ""};
        
        for (int i = 0; i < groups.length; i++) {
            Cell cell = row0.createCell(i);
            cell.setCellValue(groups[i]);
            cell.setCellStyle(headerStyle);
        }
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4)); // 피평가자 정보
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 5, 7)); // 성과평가
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 8, 10)); // 역량평가
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 11, 13)); // 다면평가
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 14, 15)); // 종합결과

        // 2. 컬럼 헤더
        Row row1 = sheet.createRow(1);
        String[] headers = new String[]{"부서명", "직책", "직위", "사번", "성명", 
                                        "1차", "2차", "합계", 
                                        "1차", "2차", "합계", 
                                        "1차", "2차", "합계", 
                                        "종합점수", "최종등급"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row1.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // 3. 데이터 본문
        int rowIdx = 2;
        for (EvaluationResultDTO r : results) {
            Row row = sheet.createRow(rowIdx++);
            int col = 0;
            row.createCell(col++).setCellValue(r.deptName());
            row.createCell(col++).setCellValue(r.jobTitle());
            row.createCell(col++).setCellValue(r.positionName());
            row.createCell(col++).setCellValue(r.empId().toString());
            row.createCell(col++).setCellValue(r.empName());

            if (r.isLeader()) {
                row.createCell(col++).setCellValue("-");
                row.createCell(col++).setCellValue("-");
                row.createCell(col++).setCellValue("-");
                
                row.createCell(col++).setCellValue("-");
                row.createCell(col++).setCellValue("-");
                row.createCell(col++).setCellValue("-");
                
                row.createCell(col++).setCellValue(getVal(r.multi1stScore()));
                row.createCell(col++).setCellValue(getVal(r.multi2ndScore()));
                row.createCell(col++).setCellValue(getVal(r.multiFinalScore()));
            } else {
                row.createCell(col++).setCellValue(getVal(r.mbo1stScore()));
                row.createCell(col++).setCellValue(getVal(r.mbo2ndScore()));
                row.createCell(col++).setCellValue(getVal(r.mboFinalScore()));
                
                row.createCell(col++).setCellValue(getVal(r.comp1stScore()));
                row.createCell(col++).setCellValue(getVal(r.comp2ndScore()));
                row.createCell(col++).setCellValue(getVal(r.compFinalScore()));
                
                row.createCell(col++).setCellValue("-");
                row.createCell(col++).setCellValue("-");
                row.createCell(col++).setCellValue("-");
            }

            row.createCell(col++).setCellValue(getVal(r.totalScore()));
            Cell gradeCell = row.createCell(col++);
            gradeCell.setCellValue(r.isConfirmed() && r.gradeCode() != null ? r.gradeCode() : "-");

            // 스타일 적용 및 등급별 색상 강조
            for (int i = 0; i < col; i++) {
                row.getCell(i).setCellStyle(bodyStyle);
            }
            applyGradeStyle(workbook, gradeCell, r.gradeCode());
        }

        // 4. 편의 기능 적용 (틀 고정 및 오토 필터)
        sheet.createFreezePane(5, 2); // E열까지, 2행까지 고정
        sheet.setAutoFilter(new CellRangeAddress(1, 1, 0, headers.length - 1));

        // 5. 너비 조절
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) < 3000) sheet.setColumnWidth(i, 3500);
        }
        sheet.setColumnWidth(0, 5000); // 부서명은 좀 더 넉넉히
    }

    private double getVal(java.math.BigDecimal val) {
        return val != null ? val.doubleValue() : 0.0;
    }

    // ===== 스타일 유틸리티 =====

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 18);
        font.setBold(true);
        font.setColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    private CellStyle createLabelStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createTableHeaderStyle(Workbook workbook, IndexedColors bgColor) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(bgColor.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createTableBodyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void applyGradeStyle(Workbook workbook, Cell cell, String grade) {
        if (grade == null || !(workbook instanceof XSSFWorkbook)) return;
        
        XSSFWorkbook xwb = (XSSFWorkbook) workbook;
        CellStyle style = xwb.createCellStyle();
        style.cloneStyleFrom(cell.getCellStyle());
        XSSFFont font = xwb.createFont();
        font.setBold(true);

        String hex = GradeColor.getHexByGrade(grade);

        if (hex != null) {
            byte[] rgb = hexToBytes(hex);
            XSSFColor color = new XSSFColor(rgb, null);
            style.setFillForegroundColor(color);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            font.setColor(IndexedColors.WHITE.getIndex()); // 배경색이 진하므로 글자는 흰색으로
        }

        style.setFont(font);
        cell.setCellStyle(style);
    }

    /**
     * 차트 슬라이스(데이터 포인트)별 색상을 지정하는 유틸리티
     * XDDFDoughnutChartData.Series 내부의 private 'series' 필드에 직접 접근합니다.
     */
    private void setSliceColor(XDDFDoughnutChartData.Series series, int index, String hex) {
        try {
            // XDDFDoughnutChartData.Series 클래스에는 CTPieSer 타입의 'series' 필드가 있습니다.
            java.lang.reflect.Field field = series.getClass().getDeclaredField("series");
            field.setAccessible(true);
            Object ctObj = field.get(series);

            if (ctObj instanceof org.openxmlformats.schemas.drawingml.x2006.chart.CTPieSer ctPieSer) {
                org.openxmlformats.schemas.drawingml.x2006.chart.CTDPt dPt = ctPieSer.addNewDPt();
                dPt.addNewIdx().setVal(index);
                
                org.openxmlformats.schemas.drawingml.x2006.main.CTShapeProperties spPr = dPt.addNewSpPr();
                org.openxmlformats.schemas.drawingml.x2006.main.CTSolidColorFillProperties fill = spPr.addNewSolidFill();
                org.openxmlformats.schemas.drawingml.x2006.main.CTSRgbColor rgbClr = fill.addNewSrgbClr();
                
                // RGB 값 설정
                rgbClr.setVal(hexToBytes(hex));
                
                log.debug("[엑셀 리포트] 차트 슬라이스 {} 색상 적용 완료 (#{})", index, hex);
            }
        } catch (Exception e) {
            log.warn("[엑셀 리포트] 차트 색상 적용 실패 (index: {}, error: {})", index, e.getMessage());
            // 필드 접근 실패 시 대안으로 getXmlObject 메서드 재시도
            try {
                java.lang.reflect.Method method = series.getClass().getSuperclass().getDeclaredMethod("getXmlObject");
                method.setAccessible(true);
                Object ctObj = method.invoke(series);
                if (ctObj instanceof org.openxmlformats.schemas.drawingml.x2006.chart.CTPieSer ctPieSer) {
                    org.openxmlformats.schemas.drawingml.x2006.chart.CTDPt dPt = ctPieSer.addNewDPt();
                    dPt.addNewIdx().setVal(index);
                    dPt.addNewSpPr().addNewSolidFill().addNewSrgbClr().setVal(hexToBytes(hex));
                }
            } catch (Exception ignored) {}
        }
    }

    private byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[3];
        for (int i = 0; i < 3; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
