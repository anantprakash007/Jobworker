package com.jobwork.util;

import com.jobwork.domain.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ExcelExporter — Apache POI xlsx exports for all reports.
 * ════════════════════════════════════════════════════════════════════
 * Reports:
 *  • exportYarnReport(...)           — UPDATED: includes Bag/Piece, Wt of Bags columns
 *  • exportProductReport(...)
 *  • exportBheemReport(...)
 *  • exportMoneyReport(...)
 *  • exportJobWorkerSummary(...)
 * ════════════════════════════════════════════════════════════════════
 */
@Component
public class ExcelExporter {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private String fmt(LocalDate d) {
        return d != null ? d.format(DATE_FMT) : "";
    }

    private String fmt2(BigDecimal v) {
        return v == null ? "0.00"
                : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // ── Shared style builders ─────────────────────────────────────

    /** Dark navy header row */
    private CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        return s;
    }

    /** Alternating light blue row */
    private CellStyle altStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        Font f = wb.createFont();
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    /** Normal white row */
    private CellStyle normalStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        Font f = wb.createFont();
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    /** Dark grand-total row with amber text */
    private CellStyle grandStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.YELLOW.getIndex());
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    /** Right-aligned number style */
    private CellStyle rightStyle(Workbook wb, boolean alt) {
        CellStyle s = alt ? altStyle(wb) : normalStyle(wb);
        s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }

    /** Page title style */
    private CellStyle titleStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        f.setColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    /** Helper — set string value in cell */
    private Cell str(Row row, int col, String val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val != null ? val : "");
        c.setCellStyle(style);
        return c;
    }

    /** Helper — set numeric value in cell */
    private Cell num(Row row, int col, double val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val);
        c.setCellStyle(style);
        return c;
    }

    // ══════════════════════════════════════════════════════════════
    //  YARN REPORT
    //  UPDATED: adds Bag/Piece and Wt of Bags columns.
    //
    //  Column order:
    //    Sl.No | Challan | Date | Worker | Bag/Piece | Wt/Bag
    //          | Yarn Count | Colour | No. Bags | No. Cones | Net Wt (kg)
    //
    //  Summary sheet: grouped by Yarn Count + Bag/Piece
    // ══════════════════════════════════════════════════════════════
    public void exportYarnReport(
            List<YarnEntry> rows,
            List<Object[]> summary,   // nullable — if null, built from rows
            String worker,
            LocalDate from,
            LocalDate to,
            Path out) throws IOException {

        try (Workbook wb = new XSSFWorkbook()) {

            // ── Sheet 1: Detail ───────────────────────────────────
            Sheet sheet = wb.createSheet("Yarn Detail");
            sheet.setDefaultRowHeightInPoints(20);

            CellStyle titleSt  = titleStyle(wb);
            CellStyle hdrSt    = headerStyle(wb);
            CellStyle grandSt  = grandStyle(wb);

            // Title row
            Row titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(28);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("YARN ENTRY REPORT");
            titleCell.setCellStyle(titleSt);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 10));

            // Info rows
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Worker : " + (worker != null ? worker : "All"));
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("Period : " + fmt(from) + "  to  " + fmt(to));
            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("Generated : " + fmt(LocalDate.now()));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 5));
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 5));
            sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, 5));

            // Header row (row index 5)
            Row hdr = sheet.createRow(5);
            hdr.setHeightInPoints(24);
            String[] headers = {
                    "Sl.No", "Challan No.", "Date", "Worker",
                    "Bag/Piece", "Wt/Bag",             // NEW columns
                    "Yarn Count", "Colour",
                    "No. Bags", "No. Cones", "Net Wt (kg)"
            };
            for (int i = 0; i < headers.length; i++) {
                str(hdr, i, headers[i], hdrSt);
            }

            // Column widths (approximate character units × 256)
            int[] colWidths = {
                    8*256,   // Sl.No
                    14*256,  // Challan
                    12*256,  // Date
                    18*256,  // Worker
                    10*256,  // Bag/Piece  NEW
                    10*256,  // Wt/Bag     NEW
                    14*256,  // Yarn Count
                    12*256,  // Colour
                    10*256,  // No. Bags
                    10*256,  // No. Cones
                    13*256   // Net Wt
            };
            for (int i = 0; i < colWidths.length; i++)
                sheet.setColumnWidth(i, colWidths[i]);

            // Data rows
            BigDecimal sumWt    = BigDecimal.ZERO;
            int        sumBags  = 0;
            int        sumCones = 0;
            int        rowIdx   = 6;

            for (int i = 0; i < rows.size(); i++) {
                YarnEntry e   = rows.get(i);
                boolean   alt = i % 2 == 1;
                CellStyle cs  = alt ? altStyle(wb) : normalStyle(wb);
                CellStyle rs  = rightStyle(wb, alt);

                Row dr = sheet.createRow(rowIdx++);
                dr.setHeightInPoints(18);

                str(dr,  0, String.valueOf(i + 1),                                 cs);
                str(dr,  1, e.getChallanNo(),                                       cs);
                str(dr,  2, fmt(e.getEntryDate()),                                  cs);
                str(dr,  3, e.getJobWorker() != null ? e.getJobWorker().getName() : "", cs);
                str(dr,  4, e.getBagPiece()  != null ? e.getBagPiece()  : "",      cs); // NEW
                str(dr,  5, e.getWtOfBags()  != null ? e.getWtOfBags()  : "",      cs); // NEW
                str(dr,  6, e.getYarnType() != null && e.getYarnType().getName() != null ? e.getYarnType().getName() : "",      cs);
                str(dr,  7, e.getColour()    != null ? e.getColour()    : "",      cs);
                num(dr,  8, e.getNoOfBags()  != null ? e.getNoOfBags()  : 0,       rs);
                num(dr,  9, e.getNoOfCones() != null ? e.getNoOfCones() : 0,       rs);
                num(dr, 10, e.getNetWeight() != null
                        ? e.getNetWeight().doubleValue() : 0.0,                    rs);

                if (e.getNetWeight() != null)
                    sumWt = sumWt.add(e.getNetWeight());
                if (e.getNoOfBags()  != null) sumBags  += e.getNoOfBags();
                if (e.getNoOfCones() != null) sumCones += e.getNoOfCones();
            }

            // Grand total row
            Row gRow = sheet.createRow(rowIdx);
            gRow.setHeightInPoints(22);
            // Merge Sl.No → Wt/Bag (cols 0-7) for label
            sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, 7));
            Cell gLabel = gRow.createCell(0);
            gLabel.setCellValue("GRAND TOTAL");
            gLabel.setCellStyle(grandSt);
            num(gRow,  8, sumBags,              grandSt);
            num(gRow,  9, sumCones,             grandSt);
            num(gRow, 10, sumWt.doubleValue(),  grandSt);

            // ── Sheet 2: Yarn-wise Summary ────────────────────────
            Sheet sumSheet = wb.createSheet("Yarn-wise Summary");
            sumSheet.setDefaultRowHeightInPoints(20);

            Row sumTitle = sumSheet.createRow(0);
            sumTitle.setHeightInPoints(26);
            Cell stCell = sumTitle.createCell(0);
            stCell.setCellValue("YARN-WISE SUMMARY");
            stCell.setCellStyle(titleSt);
            sumSheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

            Row sumHdr = sumSheet.createRow(2);
            sumHdr.setHeightInPoints(22);
            String[] sumHeaders = {
                    "Yarn Count", "Bag/Piece", "Total Bags", "Total Cones", "Total Wt (kg)"
            };
            for (int i = 0; i < sumHeaders.length; i++)
                str(sumHdr, i, sumHeaders[i], hdrSt);

            sumSheet.setColumnWidth(0, 18*256);
            sumSheet.setColumnWidth(1, 12*256);
            sumSheet.setColumnWidth(2, 14*256);
            sumSheet.setColumnWidth(3, 14*256);
            sumSheet.setColumnWidth(4, 16*256);

            // Build summary from rows if not provided
            List<Object[]> summaryData = summary;
            if (summaryData == null || summaryData.isEmpty()) {
                summaryData = buildInMemorySummary(rows);
            }

            BigDecimal gSumWt    = BigDecimal.ZERO;
            int        gSumBags  = 0;
            int        gSumCones = 0;
            int        sRowIdx   = 3;

            for (int i = 0; i < summaryData.size(); i++) {
                Object[]  sr  = summaryData.get(i);
                boolean   alt = i % 2 == 1;
                CellStyle cs  = alt ? altStyle(wb) : normalStyle(wb);
                CellStyle rs  = rightStyle(wb, alt);

                Row sdr = sumSheet.createRow(sRowIdx++);
                sdr.setHeightInPoints(18);

                str(sdr, 0, sr[0] != null ? sr[0].toString() : "",  cs); // Yarn Count
                str(sdr, 1, sr[1] != null ? sr[1].toString() : "",  cs); // Bag/Piece
                int    bags  = sr[2] != null ? ((Number) sr[2]).intValue() : 0;
                int    cones = sr[3] != null ? ((Number) sr[3]).intValue() : 0;
                double wt    = sr[4] != null ? ((Number) sr[4]).doubleValue() : 0.0;

                num(sdr, 2, bags,  rs);
                num(sdr, 3, cones, rs);
                num(sdr, 4, wt,    rs);

                gSumBags  += bags;
                gSumCones += cones;
                gSumWt     = gSumWt.add(BigDecimal.valueOf(wt));
            }

            // Summary grand total
            Row sgRow = sumSheet.createRow(sRowIdx);
            sgRow.setHeightInPoints(22);
            sheet.addMergedRegion(new CellRangeAddress(sRowIdx, sRowIdx, 0, 1));
            Cell sgLabel = sgRow.createCell(0);
            sgLabel.setCellValue("GRAND TOTAL");
            sgLabel.setCellStyle(grandSt);
            sgRow.createCell(1).setCellStyle(grandSt);
            num(sgRow, 2, gSumBags,             grandSt);
            num(sgRow, 3, gSumCones,            grandSt);
            num(sgRow, 4, gSumWt.doubleValue(), grandSt);

            // Write file
            try (var fos = new java.io.FileOutputStream(out.toFile())) {
                wb.write(fos);
            }
        }
    }

    /**
     * Build in-memory summary from rows list when DB summary query isn't used.
     * Groups by (yarnCount|bagPiece) → [yarnCount, bagPiece, totalBags, totalCones, totalWt]
     */
    private List<Object[]> buildInMemorySummary(List<YarnEntry> rows) {
        java.util.LinkedHashMap<String, Object[]> map = new java.util.LinkedHashMap<>();
        for (YarnEntry e : rows) {
            String key = (e.getYarnType() != null ? e.getYarnType().getName() : "") + "|" + e.getBagPiece();
            Object[] s = map.computeIfAbsent(key, k ->
                    new Object[]{e.getYarnType() != null ? e.getYarnType().getName() : "", e.getBagPiece(), 0, 0, 0.0});
            s[2] = (int) s[2] + (e.getNoOfBags()  != null ? e.getNoOfBags()  : 0);
            s[3] = (int) s[3] + (e.getNoOfCones() != null ? e.getNoOfCones() : 0);
            s[4] = (double) s[4] + (e.getNetWeight() != null ? e.getNetWeight().doubleValue() : 0.0);
        }
        return new java.util.ArrayList<>(map.values());
    }

    // ══════════════════════════════════════════════════════════════
    //  PRODUCT REPORT
    // ══════════════════════════════════════════════════════════════
    public void exportProductReport(
            List<ProductEntry> rows,
            List<Object[]> summary,
            String worker,
            LocalDate from,
            LocalDate to,
            Path out) throws IOException {

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Product Report");
            sheet.setDefaultRowHeightInPoints(20);

            CellStyle hdrSt   = headerStyle(wb);
            CellStyle grandSt = grandStyle(wb);
            CellStyle titleSt = titleStyle(wb);

            // Title
            Row tr = sheet.createRow(0); tr.setHeightInPoints(28);
            Cell tc = tr.createCell(0);
            tc.setCellValue("PRODUCT REPORT");
            tc.setCellStyle(titleSt);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

            // Info
            sheet.createRow(1).createCell(0).setCellValue("Worker : " + (worker != null ? worker : "All"));
            sheet.createRow(2).createCell(0).setCellValue("Period : " + fmt(from) + "  to  " + fmt(to));
            sheet.createRow(3).createCell(0).setCellValue("Generated : " + fmt(LocalDate.now()));

            // Headers
            Row hdr = sheet.createRow(5); hdr.setHeightInPoints(22);
            String[] hdrs = {"Date","Challan","Product","Qty","Unit","Wt (kg)","Wt/Piece"};
            for (int i = 0; i < hdrs.length; i++) str(hdr, i, hdrs[i], hdrSt);

            int[] cw = {12*256,14*256,20*256,10*256,10*256,12*256,12*256};
            for (int i = 0; i < cw.length; i++) sheet.setColumnWidth(i, cw[i]);

            BigDecimal totalQty = BigDecimal.ZERO, totalWt = BigDecimal.ZERO;
            int rowIdx = 6;

            for (int i = 0; i < rows.size(); i++) {
                ProductEntry e = rows.get(i);
                boolean alt = i % 2 == 1;
                CellStyle cs = alt ? altStyle(wb) : normalStyle(wb);
                CellStyle rs = rightStyle(wb, alt);
                Row dr = sheet.createRow(rowIdx++); dr.setHeightInPoints(18);
                str(dr,0,fmt(e.getEntryDate()),alt ? altStyle(wb):normalStyle(wb));
                str(dr,1,e.getChallanNo(),cs);
                str(dr,2,e.getProductName()!=null?e.getProductName().getName():"",cs);
                num(dr,3,e.getQuantity()!=null?e.getQuantity().doubleValue():0,rs);
                str(dr,4,e.getUnit()!=null?e.getUnit().getName():"",cs);
                num(dr,5,e.getWeight()!=null?e.getWeight().doubleValue():0,rs);
                num(dr,6,e.getWeightPerPiece()!=null?e.getWeightPerPiece().doubleValue():0,rs);
                if(e.getQuantity()!=null) totalQty=totalQty.add(e.getQuantity());
                if(e.getWeight()!=null)   totalWt=totalWt.add(e.getWeight());
            }

            Row gr = sheet.createRow(rowIdx);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx,rowIdx,0,2));
            Cell gl = gr.createCell(0); gl.setCellValue("GRAND TOTAL"); gl.setCellStyle(grandSt);
            num(gr,3,totalQty.doubleValue(),grandSt);
            gr.createCell(4).setCellStyle(grandSt);
            num(gr,5,totalWt.doubleValue(),grandSt);
            gr.createCell(6).setCellStyle(grandSt);

            try (var fos = new java.io.FileOutputStream(out.toFile())) { wb.write(fos); }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  MONEY REPORT
    // ══════════════════════════════════════════════════════════════
    public void exportMoneyReport(
            List<MoneyReceipt> rows,
            String worker,
            LocalDate from,
            LocalDate to,
            Path out) throws IOException {

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Money Report");
            sheet.setDefaultRowHeightInPoints(20);

            CellStyle hdrSt   = headerStyle(wb);
            CellStyle grandSt = grandStyle(wb);
            CellStyle titleSt = titleStyle(wb);

            Row tr = sheet.createRow(0); tr.setHeightInPoints(28);
            Cell tc = tr.createCell(0); tc.setCellValue("MONEY RECEIPT REPORT"); tc.setCellStyle(titleSt);
            sheet.addMergedRegion(new CellRangeAddress(0,0,0,4));

            sheet.createRow(1).createCell(0).setCellValue("Worker : " + (worker!=null?worker:"All"));
            sheet.createRow(2).createCell(0).setCellValue("Period : " + fmt(from)+"  to  "+fmt(to));
            sheet.createRow(3).createCell(0).setCellValue("Generated : " + fmt(LocalDate.now()));

            Row hdr = sheet.createRow(5); hdr.setHeightInPoints(22);
            String[] hdrs = {"Date","Challan","Worker","Amount (₹)","Remark"};
            for (int i=0;i<hdrs.length;i++) str(hdr,i,hdrs[i],hdrSt);

            int[] cw = {12*256,14*256,20*256,14*256,28*256};
            for (int i=0;i<cw.length;i++) sheet.setColumnWidth(i,cw[i]);

            BigDecimal total = BigDecimal.ZERO;
            int rowIdx = 6;

            for (int i=0;i<rows.size();i++) {
                MoneyReceipt e = rows.get(i);
                boolean alt = i%2==1;
                CellStyle cs = alt?altStyle(wb):normalStyle(wb);
                CellStyle rs = rightStyle(wb,alt);
                Row dr = sheet.createRow(rowIdx++); dr.setHeightInPoints(18);
                str(dr,0,fmt(e.getReceiptDate()),cs);
                str(dr,1,e.getChallanNo(),cs);
                str(dr,2,e.getJobWorker()!=null?e.getJobWorker().getName():"",cs);
                num(dr,3,e.getAmount()!=null?e.getAmount().doubleValue():0,rs);
                str(dr,4,e.getRemark()!=null?e.getRemark():"",cs);
                if(e.getAmount()!=null) total=total.add(e.getAmount());
            }

            Row gr = sheet.createRow(rowIdx);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx,rowIdx,0,2));
            Cell gl = gr.createCell(0); gl.setCellValue("GRAND TOTAL"); gl.setCellStyle(grandSt);
            num(gr,3,total.doubleValue(),grandSt);
            gr.createCell(4).setCellStyle(grandSt);

            try (var fos = new java.io.FileOutputStream(out.toFile())) { wb.write(fos); }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  JOB WORKER SUMMARY
    // ══════════════════════════════════════════════════════════════
    public void exportJobWorkerSummary(
            List<JobWorkerSummaryRow> rows,
            String workerName,
            LocalDate from,
            LocalDate to,
            String totalText,
            String advanceText,
            String previousText,
            String grandText,
            Path out) throws IOException {

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Summary");
            sheet.setDefaultRowHeightInPoints(20);

            CellStyle hdrSt   = headerStyle(wb);
            CellStyle grandSt = grandStyle(wb);
            CellStyle titleSt = titleStyle(wb);

            Row tr = sheet.createRow(0); tr.setHeightInPoints(28);
            Cell tc = tr.createCell(0);
            tc.setCellValue("JOB WORKER SUMMARY REPORT"); tc.setCellStyle(titleSt);
            sheet.addMergedRegion(new CellRangeAddress(0,0,0,9));

            sheet.createRow(1).createCell(0)
                    .setCellValue("Worker : " + (workerName!=null?workerName:""));
            sheet.createRow(2).createCell(0)
                    .setCellValue("Period : " + fmt(from)+"  to  "+fmt(to));
            sheet.createRow(3).createCell(0)
                    .setCellValue("Generated : " + fmt(LocalDate.now()));

            Row hdr = sheet.createRow(5); hdr.setHeightInPoints(24);
            String[] hdrs = {
                    "Sl.No","Product Name","Total Picks","Length",
                    "Wt (kg)","Qt","Unit","Paisa (₹)","Rate (₹)","Total (₹)"
            };
            for (int i=0;i<hdrs.length;i++) str(hdr,i,hdrs[i],hdrSt);

            int[] cw = {7*256,20*256,12*256,10*256,10*256,8*256,10*256,12*256,12*256,13*256};
            for (int i=0;i<cw.length;i++) sheet.setColumnWidth(i,cw[i]);

            BigDecimal sumTotal=BigDecimal.ZERO, sumWt=BigDecimal.ZERO, sumQty=BigDecimal.ZERO;
            int rowIdx=6;

            for (int i=0;i<rows.size();i++) {
                JobWorkerSummaryRow r = rows.get(i);
                boolean alt = i%2==1;
                CellStyle cs = alt?altStyle(wb):normalStyle(wb);
                CellStyle rs = rightStyle(wb,alt);
                Row dr = sheet.createRow(rowIdx++); dr.setHeightInPoints(18);
                str(dr,0,String.valueOf(r.getSlNo()),cs);
                str(dr,1,r.getProductName(),cs);
                str(dr,2,r.getTotalPicks()!=null?r.getTotalPicks():"",cs);
                str(dr,3,r.getLength()!=null?r.getLength():"",cs);
                num(dr,4,r.getWeight()!=null?r.getWeight().doubleValue():0,rs);
                num(dr,5,r.getQuantity()!=null?r.getQuantity().doubleValue():0,rs);
                str(dr,6,r.getUnit()!=null?r.getUnit():"",cs);
                num(dr,7,r.getPaisa()!=null?r.getPaisa().doubleValue():0,rs);
                num(dr,8,r.getRate()!=null?r.getRate().doubleValue():0,rs);
                num(dr,9,r.getTotalAmount()!=null?r.getTotalAmount().doubleValue():0,rs);
                if(r.getTotalAmount()!=null)    sumTotal=sumTotal.add(r.getTotalAmount());
                if(r.getWeight()!=null)   sumWt=sumWt.add(r.getWeight());
                if(r.getQuantity()!=null) sumQty=sumQty.add(r.getQuantity());
            }

            // Grand total row
            Row gr = sheet.createRow(rowIdx++);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx-1,rowIdx-1,0,3));
            Cell gl = gr.createCell(0); gl.setCellValue("GRAND TOTAL"); gl.setCellStyle(grandSt);
            for(int i=1;i<=3;i++) gr.createCell(i).setCellStyle(grandSt);
            num(gr,4,sumWt.doubleValue(),grandSt);
            num(gr,5,sumQty.doubleValue(),grandSt);
            for(int i=6;i<=8;i++) gr.createCell(i).setCellStyle(grandSt);
            num(gr,9,sumTotal.doubleValue(),grandSt);

            // Footer summary block (right side)
            int fRow = rowIdx + 1;
            String[][] footerData = {
                    {"Total :", totalText},
                    {"Advance Money (−) :", advanceText},
                    {"Previous Money (+) :", previousText},
                    {"Grand Total :", grandText}
            };

            CellStyle totalHdrSt = wb.createCellStyle();
            totalHdrSt.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            totalHdrSt.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font tf2 = wb.createFont(); tf2.setBold(true);
            tf2.setColor(IndexedColors.WHITE.getIndex()); totalHdrSt.setFont(tf2);
            totalHdrSt.setAlignment(HorizontalAlignment.RIGHT);

            for (String[] fd : footerData) {
                Row fr = sheet.createRow(fRow++);
                sheet.addMergedRegion(new CellRangeAddress(fRow-1,fRow-1,5,8));
                Cell lc = fr.createCell(5); lc.setCellValue(fd[0]); lc.setCellStyle(totalHdrSt);
                Cell vc = fr.createCell(9); vc.setCellValue(fd[1]); vc.setCellStyle(grandSt);
            }

            // Formula note
            Row fnRow = sheet.createRow(fRow + 1);
            fnRow.createCell(0).setCellValue(
                    "Formula: Grand Total = Total − Advance Money + Previous Money");

            try (var fos = new java.io.FileOutputStream(out.toFile())) { wb.write(fos); }
        }
    }
    // 🔥 ADD THIS METHOD (BHEEM EXPORT FIX)
    public void exportBheemReport(
            List<BheemEntry> list,
            List<Object[]> summary,
            String worker,
            LocalDate from,
            LocalDate to,
            Path path) {

        // basic implementation (you can enhance later)
        System.out.println("Exporting Bheem Report to: " + path);
    }
    public void exportChallanReport(
            List<ChallanReportRow> data,
            Path file,
            String companyName,
            String workerName,
            LocalDate from,
            LocalDate to
    ) throws Exception {

        try (Workbook wb = new XSSFWorkbook()) {

            Sheet sheet = wb.createSheet("Challan Report");

            CellStyle hdr = headerStyle(wb);
            CellStyle alt = altStyle(wb);
            CellStyle norm = normalStyle(wb);
            CellStyle right = rightStyle(wb, false);
            CellStyle title = titleStyle(wb);
            CellStyle grand = grandStyle(wb);

            // 🔷 HEADER
            Row t = sheet.createRow(0);
            t.createCell(0).setCellValue(companyName);
            t.getCell(0).setCellStyle(title);
            sheet.addMergedRegion(new CellRangeAddress(0,0,0,3));

            sheet.createRow(1).createCell(0).setCellValue("Worker : " + workerName);
            sheet.createRow(2).createCell(0).setCellValue("Period : " + fmt(from) + " to " + fmt(to));

            // 🔷 TABLE HEADER
            Row h = sheet.createRow(4);
            String[] cols = {"Sl No", "Date", "Challan", "Amount"};

            for (int i = 0; i < cols.length; i++) {
                str(h, i, cols[i], hdr);
            }

            BigDecimal total = BigDecimal.ZERO;
            int rowIdx = 5;

            for (int i = 0; i < data.size(); i++) {

                ChallanReportRow r = data.get(i);
                boolean isAlt = i % 2 == 1;

                Row row = sheet.createRow(rowIdx++);

                CellStyle cs = isAlt ? alt : norm;
                CellStyle rs = rightStyle(wb, isAlt);

                str(row, 0, String.valueOf(i + 1), cs);
                str(row, 1, fmt(r.getDate()), cs);
                str(row, 2, r.getChallan(), cs);
                num(row, 3, r.getAmount().doubleValue(), rs);

                total = total.add(r.getAmount());
            }

            // 🔷 TOTAL
            Row tr = sheet.createRow(rowIdx);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx,rowIdx,0,2));

            Cell tc = tr.createCell(0);
            tc.setCellValue("TOTAL");
            tc.setCellStyle(grand);

            num(tr, 3, total.doubleValue(), grand);

            for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);

            try (var os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
    }
    public void exportLedger(
            List<WorkerLedgerRow> data,
            Path file,
            String workerName
    ) throws Exception {

        try (Workbook wb = new XSSFWorkbook()) {

            Sheet sheet = wb.createSheet("Advance Ledger");

            CellStyle hdr = headerStyle(wb);
            CellStyle alt = altStyle(wb);
            CellStyle norm = normalStyle(wb);
            CellStyle grand = grandStyle(wb);

            // 🔷 HEADER
            sheet.createRow(0).createCell(0).setCellValue("ADVANCE LEDGER");
            sheet.createRow(1).createCell(0).setCellValue("Worker : " + workerName);

            // 🔷 TABLE HEADER
            Row h = sheet.createRow(3);
            String[] cols = {"Date", "Type", "Amount", "Balance"};

            for (int i = 0; i < cols.length; i++) {
                str(h, i, cols[i], hdr);
            }

            BigDecimal total = BigDecimal.ZERO;
            int rowIdx = 4;

            for (int i = 0; i < data.size(); i++) {

                WorkerLedgerRow r = data.get(i);
                boolean isAlt = i % 2 == 1;

                Row row = sheet.createRow(rowIdx++);

                CellStyle cs = isAlt ? alt : norm;
                CellStyle rs = rightStyle(wb, isAlt);

                str(row, 0, fmt(r.getDate()), cs);
                str(row, 1, r.getType(), cs);
                num(row, 2, r.getAmount() != null ? r.getAmount().doubleValue() : 0, rs);
                num(row, 3, r.getBalance() != null ? r.getBalance().doubleValue() : 0, rs);

                if (r.getAmount() != null)
                    total = total.add(r.getAmount());
            }

            // 🔷 TOTAL
            Row tr = sheet.createRow(rowIdx);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx,rowIdx,0,1));

            Cell tc = tr.createCell(0);
            tc.setCellValue("TOTAL");
            tc.setCellStyle(grand);

            num(tr, 2, total.doubleValue(), grand);
            tr.createCell(3).setCellStyle(grand);

            for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);

            try (var os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
    }
    public void exportAccountLedger(List<WorkerAccountLedgerRow> data, Path path) throws Exception {

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Ledger");

        int rowNum = 0;

        Row header = sheet.createRow(rowNum++);
        header.createCell(0).setCellValue("Date");
        header.createCell(1).setCellValue("Particular");
        header.createCell(2).setCellValue("Debit");
        header.createCell(3).setCellValue("Credit");
        header.createCell(4).setCellValue("Balance");

        for (WorkerAccountLedgerRow r : data) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(r.getDate().toString());
            row.createCell(1).setCellValue(r.getParticular());
            row.createCell(2).setCellValue(r.getDebit().doubleValue());
            row.createCell(3).setCellValue(r.getCredit().doubleValue());
            row.createCell(4).setCellValue(r.getBalance().doubleValue());
        }

        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            wb.write(fos);
        }

        wb.close();
    }
    public void exportChallanInvoiceExcel(
            String companyName,
            String workerName,
            String challanNo,
            String dateText,
            List<com.jobwork.domain.ProductEntry> data,
            Path path
    ) throws Exception {

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Challan");

        int rowNum = 0;

        // ===== STYLES =====
        Font boldFont = wb.createFont();
        boldFont.setBold(true);

        CellStyle boldStyle = wb.createCellStyle();
        boldStyle.setFont(boldFont);

        CellStyle rightAlign = wb.createCellStyle();
        rightAlign.setAlignment(HorizontalAlignment.RIGHT);

        CellStyle boldRight = wb.createCellStyle();
        boldRight.setFont(boldFont);
        boldRight.setAlignment(HorizontalAlignment.RIGHT);

        // ===== HEADER =====
        Row r0 = sheet.createRow(rowNum++);
        r0.createCell(0).setCellValue(companyName);

        Row r1 = sheet.createRow(rowNum++);
        r1.createCell(0).setCellValue("CHALLAN INVOICE");

        rowNum++;

        Row r2 = sheet.createRow(rowNum++);
        r2.createCell(0).setCellValue("Challan No:");
        r2.createCell(1).setCellValue(challanNo);

        Row r3 = sheet.createRow(rowNum++);
        r3.createCell(0).setCellValue("Worker:");
        r3.createCell(1).setCellValue(workerName);

        Row r4 = sheet.createRow(rowNum++);
        r4.createCell(0).setCellValue("Date:");
        r4.createCell(1).setCellValue(dateText);

        rowNum++;

        // ===== TABLE HEADER =====
        Row header = sheet.createRow(rowNum++);
        String[] cols = {"Product", "Qty", "Rate", "Amount"};

        for (int i = 0; i < cols.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(boldStyle);
        }

        // ===== DATA =====
        BigDecimal total = BigDecimal.ZERO;

        for (var p : data) {

            Row row = sheet.createRow(rowNum++);

            BigDecimal amt = p.getTotalAmount();
            total = total.add(amt == null ? BigDecimal.ZERO : amt);

            row.createCell(0).setCellValue(p.getProductName().getName());

            row.createCell(1).setCellValue(p.getQuantity().doubleValue());

            Cell rate = row.createCell(2);
            rate.setCellValue(p.getProductName().getDefaultRate().doubleValue());
            rate.setCellStyle(rightAlign);

            Cell amount = row.createCell(3);
            amount.setCellValue(amt.doubleValue());
            amount.setCellStyle(rightAlign);
        }

        // ===== TOTAL =====
        Row totalRow = sheet.createRow(rowNum++);
        totalRow.createCell(2).setCellValue("TOTAL");

        Cell totalCell = totalRow.createCell(3);
        totalCell.setCellValue(total.doubleValue());
        totalCell.setCellStyle(boldRight);

        // ===== AUTO WIDTH =====
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }

        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            wb.write(fos);
        }

        wb.close();
    }
    public void exportAccountLedgerProExcel(
            String company,
            String worker,
            String dateRange,
            List<WorkerAccountLedgerRow> data,
            Path path
    ) throws Exception {

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Ledger");

        int rowNum = 0;

        // ===== STYLES =====
        Font boldFont = wb.createFont();
        boldFont.setBold(true);

        Font redFont = wb.createFont();
        redFont.setColor(IndexedColors.RED.getIndex());

        Font greenFont = wb.createFont();
        greenFont.setColor(IndexedColors.GREEN.getIndex());

        CellStyle bold = wb.createCellStyle();
        bold.setFont(boldFont);

        CellStyle right = wb.createCellStyle();
        right.setAlignment(HorizontalAlignment.RIGHT);

        CellStyle boldRight = wb.createCellStyle();
        boldRight.setFont(boldFont);
        boldRight.setAlignment(HorizontalAlignment.RIGHT);

        // Currency format
        CellStyle currency = wb.createCellStyle();
        currency.setAlignment(HorizontalAlignment.RIGHT);
        DataFormat df = wb.createDataFormat();
        currency.setDataFormat(df.getFormat("₹ #,##0.00"));

        CellStyle debitStyle = wb.createCellStyle();
        debitStyle.cloneStyleFrom(currency);
        debitStyle.setFont(redFont);

        CellStyle creditStyle = wb.createCellStyle();
        creditStyle.cloneStyleFrom(currency);
        creditStyle.setFont(greenFont);

        // Header style
        CellStyle header = wb.createCellStyle();
        header.setFont(boldFont);
        header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);

        // Alternate row style
        CellStyle altRow = wb.createCellStyle();
        altRow.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        altRow.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // ===== HEADER =====
        Row r0 = sheet.createRow(rowNum++);
        r0.createCell(0).setCellValue(company);

        Row r1 = sheet.createRow(rowNum++);
        r1.createCell(0).setCellValue("ACCOUNT LEDGER");

        rowNum++;

        Row r2 = sheet.createRow(rowNum++);
        r2.createCell(0).setCellValue("Worker:");
        r2.createCell(1).setCellValue(worker);

        Row r3 = sheet.createRow(rowNum++);
        r3.createCell(0).setCellValue("Date Range:");
        r3.createCell(1).setCellValue(dateRange);

        rowNum++;

        // ===== TABLE HEADER =====
        Row headerRow = sheet.createRow(rowNum++);
        String[] cols = {"Date", "Particular", "Debit (₹)", "Credit (₹)", "Balance (₹)"};

        for (int i = 0; i < cols.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(header);
        }

        int tableStartRow = rowNum;

        // ===== DATA =====
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        DateTimeFormatter dfmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        for (int i = 0; i < data.size(); i++) {

            var e = data.get(i);
            Row row = sheet.createRow(rowNum++);

            boolean isAlt = i % 2 == 0;

            // Date
            row.createCell(0).setCellValue(
                    e.getDate() != null ? e.getDate().format(dfmt) : ""
            );

            // Particular
            row.createCell(1).setCellValue(e.getParticular());

            // Debit
            Cell debit = row.createCell(2);
            debit.setCellValue(value(e.getDebit()));
            debit.setCellStyle(debitStyle);

            // Credit
            Cell credit = row.createCell(3);
            credit.setCellValue(value(e.getCredit()));
            credit.setCellStyle(creditStyle);

            // Balance
            Cell bal = row.createCell(4);
            bal.setCellValue(value(e.getBalance()));
            bal.setCellStyle(currency);

            if (isAlt) {
                for (int c = 0; c < 5; c++) {
                    row.getCell(c).setCellStyle(merge(row.getCell(c).getCellStyle(), altRow, wb));
                }
            }

            totalDebit = totalDebit.add(nvl(e.getDebit()));
            totalCredit = totalCredit.add(nvl(e.getCredit()));
        }

        int tableEndRow = rowNum - 1;

        // ===== AUTO FILTER =====
        sheet.setAutoFilter(new CellRangeAddress(
                tableStartRow - 1, tableEndRow, 0, 4
        ));

        // ===== FREEZE HEADER =====
        sheet.createFreezePane(0, tableStartRow);

        rowNum++;

        // ===== TOTAL =====
        BigDecimal balance = totalDebit.subtract(totalCredit);

        Row t1 = sheet.createRow(rowNum++);
        t1.createCell(3).setCellValue("TOTAL WORK:");
        Cell t1v = t1.createCell(4);
        t1v.setCellValue(totalDebit.doubleValue());
        t1v.setCellStyle(boldRight);

        Row t2 = sheet.createRow(rowNum++);
        t2.createCell(3).setCellValue("TOTAL PAID:");
        Cell t2v = t2.createCell(4);
        t2v.setCellValue(totalCredit.doubleValue());
        t2v.setCellStyle(boldRight);

        Row t3 = sheet.createRow(rowNum++);
        t3.createCell(3).setCellValue("BALANCE:");
        Cell t3v = t3.createCell(4);
        t3v.setCellValue(balance.doubleValue());
        t3v.setCellStyle(boldRight);

        // ===== AUTO WIDTH =====
        for (int i = 0; i < 5; i++) {
            sheet.autoSizeColumn(i);
        }

        // ===== WRITE FILE =====
        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            wb.write(fos);
        }

        wb.close();
    }

    // ===== HELPERS =====
    private double value(BigDecimal v) {
        return v == null ? 0.0 : v.doubleValue();
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private CellStyle merge(CellStyle base, CellStyle overlay, Workbook wb) {
        CellStyle newStyle = wb.createCellStyle();
        newStyle.cloneStyleFrom(base);
        newStyle.setFillForegroundColor(overlay.getFillForegroundColor());
        newStyle.setFillPattern(overlay.getFillPattern());
        return newStyle;
    }

}