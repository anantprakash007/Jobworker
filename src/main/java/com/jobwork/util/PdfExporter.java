package com.jobwork.util;


import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.jobwork.domain.*;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.itextpdf.layout.element.*;

@Component
public class PdfExporter {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private String fmt(LocalDate d) {
        return d != null ? d.format(DATE_FMT) : "";
    }

    private String fmt2(BigDecimal v) {
        return v == null ? "0.00"
                : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // ── Colors ───────────────────────────────────────────────────
    private static final DeviceRgb HEADER_BG   = new DeviceRgb(0x1e, 0x3a, 0x8a);
    private static final DeviceRgb ALT_BG      = new DeviceRgb(0xf8, 0xfa, 0xff);
    private static final DeviceRgb GRAND_BG    = new DeviceRgb(0x0a, 0x16, 0x28);
    private static final DeviceRgb TOTAL_BG    = new DeviceRgb(0x1e, 0x3a, 0x8a);
    private static final DeviceRgb ADVANCE_BG  = new DeviceRgb(0xff, 0xf2, 0xf2);
    private static final DeviceRgb ADVANCE_CLR = new DeviceRgb(0xdc, 0x26, 0x26);
    private static final DeviceRgb PREV_BG     = new DeviceRgb(0xf0, 0xfd, 0xf4);
    private static final DeviceRgb PREV_CLR    = new DeviceRgb(0x05, 0x96, 0x69);
    private static final DeviceRgb AMBER_CLR   = new DeviceRgb(0xfc, 0xd3, 0x4d);

    // ── Cell builders ────────────────────────────────────────────
    private Cell hdr(String text) {
        return new Cell()
                .add(new Paragraph(text).setBold().setFontColor(ColorConstants.WHITE).setFontSize(9))
                .setBackgroundColor(HEADER_BG)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(5);
    }

    private Cell cell(String text, boolean alt) {
        return new Cell()
                .add(new Paragraph(text != null ? text : "").setFontSize(9))
                .setBackgroundColor(alt ? ALT_BG : ColorConstants.WHITE)
                .setPadding(4);
    }

    private Cell right(String text, boolean alt) {
        return new Cell()
                .add(new Paragraph(text != null ? text : "").setFontSize(9))
                .setBackgroundColor(alt ? ALT_BG : ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.RIGHT)
                .setPadding(4);
    }

    private Cell grand(String text) {
        return new Cell()
                .add(new Paragraph(text).setBold().setFontColor(AMBER_CLR).setFontSize(10))
                .setBackgroundColor(GRAND_BG)
                .setTextAlignment(TextAlignment.RIGHT)
                .setPadding(5);
    }

    private Cell grandLabel(String text) {
        return new Cell()
                .add(new Paragraph(text).setBold().setFontColor(ColorConstants.WHITE).setFontSize(9))
                .setBackgroundColor(GRAND_BG)
                .setTextAlignment(TextAlignment.RIGHT)
                .setPadding(5);
    }

    private Cell totalCell(String text) {
        return new Cell()
                .add(new Paragraph(text).setBold().setFontColor(ColorConstants.WHITE).setFontSize(9))
                .setBackgroundColor(TOTAL_BG)
                .setTextAlignment(TextAlignment.RIGHT)
                .setPadding(5);
    }

    private Cell totalLabel(String text) {
        return new Cell()
                .add(new Paragraph(text).setBold().setFontColor(ColorConstants.WHITE).setFontSize(9))
                .setBackgroundColor(TOTAL_BG)
                .setTextAlignment(TextAlignment.RIGHT)
                .setPadding(5);
    }

    private Cell grandBlank() {
        return new Cell().setBackgroundColor(GRAND_BG).setPadding(5);
    }

    // ── Page header block ─────────────────────────────────────────
    private void headerBlock(Document doc, String title,
                             String worker, LocalDate from, LocalDate to) {
        doc.add(new Paragraph(title)
                .setBold().setFontSize(16)
                .setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph("Worker : " + (worker != null ? worker : "All"))
                .setFontSize(11));
        doc.add(new Paragraph("Period : " + fmt(from) + "  to  " + fmt(to))
                .setFontSize(11));
        doc.add(new Paragraph("Generated : " + fmt(LocalDate.now()))
                .setFontSize(10).setItalic()
                .setTextAlignment(TextAlignment.RIGHT));
        doc.add(new Paragraph("\n"));
    }

    // ══════════════════════════════════════════════════════════════
    //  JOB WORKER SUMMARY REPORT
    //
    //  Column indices (0-based):
    //    0=Sl.No  1=Product Name  2=Total Picks  3=Length
    //    4=Wt(kg) 5=Qt            6=Unit         7=Paisa(₹)
    //    8=Rate(₹) 9=Total(₹)
    //
    //  FIX 1: Sl.No width reduced from 22f → 14f
    //  FIX 2: Grand total row — label spans cols 0-6 (7 cols),
    //         Wt at col 4 replaced with span 0-5 label,
    //         Qt shown at col 5, Total value at col 9 (RIGHT side).
    //         Previous bug: label spanned only 0-3, leaving total
    //         value wrapping to col 0 of a new visual row.
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
            Path outputPath) throws IOException {

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(outputPath.toFile()));
             Document doc    = new Document(pdf, PageSize.A4.rotate())) {

            // ── PDF page header ───────────────────────────────────
            doc.add(new Paragraph("JOB WORKER SUMMARY REPORT")
                    .setBold().setFontSize(18)
                    .setFontColor(new DeviceRgb(0x1e, 0x3a, 0x8a))
                    .setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph("Worker : " + (workerName != null ? workerName : ""))
                    .setBold().setFontSize(12)
                    .setFontColor(new DeviceRgb(0x0f, 0x1b, 0x3d)));
            doc.add(new Paragraph("Period : " + fmt(from) + "  to  " + fmt(to))
                    .setFontSize(11));
            doc.add(new Paragraph("Generated : " + fmt(LocalDate.now()))
                    .setFontSize(10).setItalic()
                    .setTextAlignment(TextAlignment.RIGHT));
            doc.add(new Paragraph("\n"));

            // ── Column widths (percent-based — TRUE percentages) ──
            // ROOT CAUSE OF WIDE Sl.No:
            //   createPointArray + setWidth(100%) makes iText SCALE UP
            //   all columns proportionally (594pt → 793pt on A4 landscape).
            //   Even 14f inflated to ~19pt — still appears wide.
            // FIX: createPercentArray — Sl.No = 3.5% ≈ 28pt actual, truly narrow.
            float[] colWidths = {
                    3.5f,   // 0 — Sl.No        ← 3.5% ≈ 28pt, fits "999" with padding
                    17.5f,  // 1 — Product Name  ← widest text column
                    9.0f,   // 2 — Total Picks
                    7.5f,   // 3 — Length
                    8.5f,   // 4 — Wt (kg)
                    6.5f,   // 5 — Qt
                    7.0f,   // 6 — Unit
                    9.0f,   // 7 — Paisa (₹)
                    8.5f,   // 8 — Rate (₹)
                    10.0f   // 9 — Total (₹)    sum ≈ 97, iText normalises to 100%
            };
            Table table = new Table(UnitValue.createPercentArray(colWidths));
            table.setWidth(UnitValue.createPercentValue(100));

            // Header row
            String[] headers = {
                    "Sl.No", "Product Name", "Total Picks", "Length",
                    "Wt (kg)", "Qt", "Unit", "Paisa (₹)", "Rate (₹)", "Total (₹)"
            };
            for (String h : headers) table.addHeaderCell(hdr(h));

            // Data rows + running totals
            BigDecimal sumTotal = BigDecimal.ZERO;
            BigDecimal sumWt    = BigDecimal.ZERO;
            BigDecimal sumQty   = BigDecimal.ZERO;

            for (int i = 0; i < rows.size(); i++) {
                JobWorkerSummaryRow r = rows.get(i);
                boolean alt = (i % 2 == 1);

                table.addCell(cell(String.valueOf(r.getSlNo()), alt)
                        .setTextAlignment(TextAlignment.CENTER));          // 0
                table.addCell(cell(r.getProductName(), alt));              // 1
                table.addCell(cell(r.getTotalPicks(), alt)
                        .setTextAlignment(TextAlignment.CENTER));          // 2
                table.addCell(cell(r.getLength(), alt)
                        .setTextAlignment(TextAlignment.CENTER));          // 3
                table.addCell(right(fmt2(r.getWeight()), alt));            // 4
                table.addCell(cell(r.getQuantity().toPlainString(), alt)
                        .setTextAlignment(TextAlignment.CENTER));          // 5
                table.addCell(cell(r.getUnit(), alt)
                        .setTextAlignment(TextAlignment.CENTER));          // 6
                table.addCell(right(fmt2(r.getPaisa()), alt));             // 7
                table.addCell(right(fmt2(r.getRate()), alt));              // 8
                table.addCell(right(fmt2(r.getTotalAmount()), alt));             // 9

                sumTotal = sumTotal.add(r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO);
                sumWt    = sumWt.add(r.getWeight()   != null ? r.getWeight() : BigDecimal.ZERO);
                sumQty   = sumQty.add(r.getQuantity() != null ? r.getQuantity() : BigDecimal.ZERO);
            }

            // ── Grand total row — FIX 2 ───────────────────────────
            // 10 columns total (indices 0-9).
            // Layout:
            //   [  GRAND TOTAL (label, span 0-3, 4 cols)  ] [Wt val] [Qt val] [blank Unit] [blank Paisa] [blank Rate] [Total val]
            //    0                                          3   4        5         6             7              8           9
            //
            // All 10 column slots must be filled = 4 + 1 + 1 + 1 + 1 + 1 + 1 = 10 ✓

            // Col 0-3: label (span 4)
            table.addCell(new Cell(1, 4)
                    .add(new Paragraph("GRAND TOTAL")
                            .setBold().setFontColor(ColorConstants.WHITE).setFontSize(9))
                    .setBackgroundColor(GRAND_BG)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setPadding(5));

            // Col 4: Total Wt
            table.addCell(new Cell()
                    .add(new Paragraph(fmt2(sumWt)).setBold()
                            .setFontColor(AMBER_CLR).setFontSize(9))
                    .setBackgroundColor(GRAND_BG)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setPadding(5));

            // Col 5: Total Qt
            table.addCell(new Cell()
                    .add(new Paragraph(fmt2(sumQty)).setBold()
                            .setFontColor(AMBER_CLR).setFontSize(9))
                    .setBackgroundColor(GRAND_BG)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setPadding(5));

            // Col 6: Unit — blank
            table.addCell(grandBlank());

            // Col 7: Paisa — blank
            table.addCell(grandBlank());

            // Col 8: Rate — blank
            table.addCell(grandBlank());

            // Col 9: Total (₹) — RIGHT side, FIX for total appearing on left
            table.addCell(new Cell()
                    .add(new Paragraph(totalText).setBold()
                            .setFontColor(AMBER_CLR).setFontSize(10))
                    .setBackgroundColor(GRAND_BG)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setPadding(5));

            doc.add(table);
            doc.add(new Paragraph(" "));

            // ── Footer summary table (right-aligned, 55% width) ───
            Table footer = new Table(UnitValue.createPercentArray(new float[]{65f, 35f}));
            footer.setWidth(UnitValue.createPercentValue(55));
            footer.setHorizontalAlignment(
                    com.itextpdf.layout.properties.HorizontalAlignment.RIGHT);

            // Row 1: Total
            footer.addCell(totalLabel("Total  :"));
            footer.addCell(totalCell(totalText));

            // Row 2: Advance Money (−)
            footer.addCell(new Cell()
                    .add(new Paragraph("Advance Money (−)  :").setBold()
                            .setFontColor(ADVANCE_CLR).setFontSize(9))
                    .setBackgroundColor(ADVANCE_BG)
                    .setTextAlignment(TextAlignment.RIGHT).setPadding(5));
            footer.addCell(new Cell()
                    .add(new Paragraph(advanceText).setBold()
                            .setFontColor(ADVANCE_CLR).setFontSize(9))
                    .setBackgroundColor(ADVANCE_BG)
                    .setTextAlignment(TextAlignment.RIGHT).setPadding(5));

            // Row 3: Previous Money (+)
            footer.addCell(new Cell()
                    .add(new Paragraph("Previous Money (+)  :").setBold()
                            .setFontColor(PREV_CLR).setFontSize(9))
                    .setBackgroundColor(PREV_BG)
                    .setTextAlignment(TextAlignment.RIGHT).setPadding(5));
            footer.addCell(new Cell()
                    .add(new Paragraph(previousText).setBold()
                            .setFontColor(PREV_CLR).setFontSize(9))
                    .setBackgroundColor(PREV_BG)
                    .setTextAlignment(TextAlignment.RIGHT).setPadding(5));

            // Row 4: Grand Total
            footer.addCell(grandLabel("Grand Total  :"));
            footer.addCell(grand(grandText));

            doc.add(footer);
            doc.add(new Paragraph(" "));

            // Formula note
            doc.add(new Paragraph(
                    "Formula:  Grand Total  =  Total  −  Advance Money  +  Previous Money")
                    .setFontSize(9).setItalic()
                    .setFontColor(new DeviceRgb(0x64, 0x74, 0x8b)));
        }
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

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(out.toFile()));
             Document doc    = new Document(pdf, PageSize.A4.rotate())) {

            headerBlock(doc, "Product Report", worker, from, to);

            Table t = new Table(UnitValue.createPercentArray(
                    new float[]{10, 14, 18, 10, 8, 10, 10}))
                    .useAllAvailableWidth();

            for (String h : new String[]{"Date", "Challan", "Product", "Qty", "Unit", "Wt", "Wt/Pc"})
                t.addHeaderCell(hdr(h));

            BigDecimal totalQty = BigDecimal.ZERO;
            BigDecimal totalWt  = BigDecimal.ZERO;

            for (int i = 0; i < rows.size(); i++) {
                ProductEntry e = rows.get(i);
                boolean alt = i % 2 == 1;
                t.addCell(cell(fmt(e.getEntryDate()), alt));
                t.addCell(cell(e.getChallanNo(), alt));
                t.addCell(cell(e.getProductName() != null ? e.getProductName().getName() : "", alt));
                t.addCell(right(e.getQuantity()       != null ? e.getQuantity().toPlainString() : "0", alt));
                t.addCell(cell(e.getUnit()            != null ? e.getUnit().getName() : "", alt));
                t.addCell(right(e.getWeight()         != null ? e.getWeight().toPlainString() : "0", alt));
                t.addCell(right(e.getWeightPerPiece() != null ? e.getWeightPerPiece().toPlainString() : "0", alt));
                if (e.getQuantity() != null) totalQty = totalQty.add(e.getQuantity());
                if (e.getWeight()   != null) totalWt  = totalWt.add(e.getWeight());
            }

            t.addCell(new Cell(1, 3).add(new Paragraph("GRAND TOTAL").setBold()
                            .setFontColor(ColorConstants.WHITE).setFontSize(9))
                    .setBackgroundColor(GRAND_BG).setPadding(5));
            t.addCell(grand(totalQty.toPlainString()));
            t.addCell(new Cell());
            t.addCell(grand(totalWt.toPlainString()));
            t.addCell(new Cell());
            doc.add(t);

            if (summary != null && !summary.isEmpty()) {
                doc.add(new Paragraph("\nProduct-wise Summary").setBold().setFontSize(12));
                Table s = new Table(UnitValue.createPercentArray(new float[]{40, 30, 30}))
                        .useAllAvailableWidth();
                for (String h : new String[]{"Product", "Total Qty", "Total Wt"})
                    s.addHeaderCell(hdr(h));
                BigDecimal gq = BigDecimal.ZERO, gw = BigDecimal.ZERO;
                for (int i = 0; i < summary.size(); i++) {
                    Object[] r = summary.get(i);
                    boolean alt = i % 2 == 1;
                    BigDecimal qty = new BigDecimal(r[1].toString());
                    BigDecimal wt  = new BigDecimal(r[2].toString());
                    s.addCell(cell(r[0].toString(), alt));
                    s.addCell(right(qty.toPlainString(), alt));
                    s.addCell(right(wt.toPlainString(), alt));
                    gq = gq.add(qty); gw = gw.add(wt);
                }
                s.addCell(grandLabel("GRAND TOTAL"));
                s.addCell(grand(gq.toPlainString()));
                s.addCell(grand(gw.toPlainString()));
                doc.add(s);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  BHEEM REPORT
    // ══════════════════════════════════════════════════════════════
    public void exportBheemReport(
            List<BheemEntry> rows,
            List<Object[]> summary,
            String worker,
            LocalDate from,
            LocalDate to,
            Path out) throws IOException {

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(out.toFile()));
             Document doc    = new Document(pdf, PageSize.A4.rotate())) {

            headerBlock(doc, "Bheem Report", worker, from, to);

            Table t = new Table(UnitValue.createPercentArray(
                    new float[]{12, 15, 15, 8, 10, 12, 10}))
                    .useAllAvailableWidth();

            for (String h : new String[]{"Date", "Challan", "Bheem", "Taar", "Wt", "Wrapper", "Colour"})
                t.addHeaderCell(hdr(h));

            BigDecimal totalWt = BigDecimal.ZERO;

            for (int i = 0; i < rows.size(); i++) {
                BheemEntry e = rows.get(i);
                boolean alt = i % 2 == 1;
                t.addCell(cell(fmt(e.getEntryDate()), alt));
                t.addCell(cell(e.getChallanNo(), alt));
                t.addCell(cell(e.getBheemName() != null ? e.getBheemName().getName() : "", alt));
                t.addCell(cell(e.getTaar()      != null ? String.valueOf(e.getTaar().getValue()) : "", alt));
                t.addCell(right(e.getWeight()   != null ? e.getWeight().toPlainString() : "0", alt));
                t.addCell(cell(e.getWrapper()   != null ? e.getWrapper().getName() : "", alt));
                t.addCell(cell(e.getColour(), alt));
                if (e.getWeight() != null) totalWt = totalWt.add(e.getWeight());
            }

            t.addCell(new Cell(1, 4).add(new Paragraph("GRAND TOTAL").setBold()
                            .setFontColor(ColorConstants.WHITE).setFontSize(9))
                    .setBackgroundColor(GRAND_BG).setPadding(5));
            t.addCell(grand(totalWt.toPlainString()));
            t.addCell(new Cell(1, 2));
            doc.add(t);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  YARN REPORT
    // ══════════════════════════════════════════════════════════════
    // ══════════════════════════════════════════════════════════════
    //  YARN REPORT — UPDATED
    //  NEW columns: Bag/Piece (col 4) | Wt/Bag (col 5)
    //  Uses e.getYarnType().getName() (YarnType entity, not String)
    //  Uses e.getDeliveryLocation() for location column
    //
    //  Column order (11 cols, percent-based widths):
    //    Sl.No | Challan | Date | Worker | Bag/Piece | Wt/Bag
    //          | Yarn Count | Colour | No.Bags | No.Cones | Net Wt(kg)
    //
    //  Grand total row: label spans 0-7, then Bags, Cones, Wt at 8,9,10
    // ══════════════════════════════════════════════════════════════
    public void exportYarnReport(
            List<YarnEntry> rows,
            List<Object[]> summary,
            String worker,
            LocalDate from,
            LocalDate to,
            Path out) throws IOException {

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(out.toFile()));
             Document doc    = new Document(pdf, PageSize.A4.rotate())) {

            // ── Page header ───────────────────────────────────────
            doc.add(new Paragraph("YARN ENTRY REPORT")
                    .setBold().setFontSize(18)
                    .setFontColor(new DeviceRgb(0x1e, 0x3a, 0x8a))
                    .setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph("Worker : " + (worker != null ? worker : "All"))
                    .setBold().setFontSize(12)
                    .setFontColor(new DeviceRgb(0x0f, 0x1b, 0x3d)));
            doc.add(new Paragraph("Period : " + fmt(from) + "  to  " + fmt(to))
                    .setFontSize(11));
            doc.add(new Paragraph("Generated : " + fmt(LocalDate.now()))
                    .setFontSize(10).setItalic()
                    .setTextAlignment(TextAlignment.RIGHT));
            doc.add(new Paragraph("\n"));

            // ── Column widths (percent) — 11 columns ─────────────
            // Sl.No narrow (3%), total must approach 100%
            float[] colWidths = {
                    3.0f,   // 0  Sl.No
                    11.0f,  // 1  Challan No.
                    9.0f,   // 2  Date
                    13.0f,  // 3  Worker
                    7.5f,   // 4  Bag/Piece     NEW
                    8.0f,   // 5  Wt/Bag        NEW
                    11.0f,  // 6  Yarn Count
                    9.0f,   // 7  Colour
                    8.5f,   // 8  No. Bags
                    8.5f,   // 9  No. Cones
                    10.5f   // 10 Net Wt (kg)
            };
            Table table = new Table(UnitValue.createPercentArray(colWidths));
            table.setWidth(UnitValue.createPercentValue(100));

            // Header row
            String[] headers = {
                    "Sl.No", "Challan No.", "Date", "Worker",
                    "Bag/Piece", "Wt/Bag",
                    "Yarn Count", "Colour",
                    "No. Bags", "No. Cones", "Net Wt (kg)"
            };
            for (String h : headers) table.addHeaderCell(hdr(h));

            // Data rows
            BigDecimal sumWt    = BigDecimal.ZERO;
            int        sumBags  = 0;
            int        sumCones = 0;

            for (int i = 0; i < rows.size(); i++) {
                YarnEntry e   = rows.get(i);
                boolean   alt = (i % 2 == 1);

                // YarnType is an entity — get name via getYarnType().getName()
                String yarnTypeName = (e.getYarnType() != null
                        && e.getYarnType().getName() != null)
                        ? e.getYarnType().getName() : "";

                String workerName = (e.getJobWorker() != null)
                        ? e.getJobWorker().getName() : "";

                table.addCell(cell(String.valueOf(i + 1), alt)
                        .setTextAlignment(TextAlignment.CENTER));              // 0
                table.addCell(cell(e.getChallanNo(), alt));                    // 1
                table.addCell(cell(fmt(e.getEntryDate()), alt));               // 2
                table.addCell(cell(workerName, alt));                          // 3
                table.addCell(cell(e.getBagPiece()  != null
                        ? e.getBagPiece()  : "", alt)
                        .setTextAlignment(TextAlignment.CENTER));              // 4 NEW
                table.addCell(cell(e.getWtOfBags()  != null
                        ? e.getWtOfBags()  : "", alt)
                        .setTextAlignment(TextAlignment.CENTER));              // 5 NEW
                table.addCell(cell(yarnTypeName, alt));                        // 6
                table.addCell(cell(e.getColour() != null
                        ? e.getColour() : "", alt));                           // 7
                table.addCell(right(e.getNoOfBags()  != null
                        ? String.valueOf(e.getNoOfBags())  : "0", alt));      // 8
                table.addCell(right(e.getNoOfCones() != null
                        ? String.valueOf(e.getNoOfCones()) : "0", alt));      // 9
                table.addCell(right(fmt2(e.getNetWeight()), alt));             // 10

                if (e.getNetWeight() != null) sumWt = sumWt.add(e.getNetWeight());
                if (e.getNoOfBags()  != null) sumBags  += e.getNoOfBags();
                if (e.getNoOfCones() != null) sumCones += e.getNoOfCones();
            }

            // Grand total row — 11 columns (0-10)
            // Label spans 0-7 (8 cols), then Bags(8), Cones(9), Wt(10)
            table.addCell(new Cell(1, 8)
                    .add(new Paragraph("GRAND TOTAL").setBold()
                            .setFontColor(ColorConstants.WHITE).setFontSize(9))
                    .setBackgroundColor(GRAND_BG)
                    .setTextAlignment(TextAlignment.RIGHT).setPadding(5));     // 0-7
            table.addCell(new Cell()
                    .add(new Paragraph(String.valueOf(sumBags)).setBold()
                            .setFontColor(AMBER_CLR).setFontSize(9))
                    .setBackgroundColor(GRAND_BG)
                    .setTextAlignment(TextAlignment.RIGHT).setPadding(5));     // 8
            table.addCell(new Cell()
                    .add(new Paragraph(String.valueOf(sumCones)).setBold()
                            .setFontColor(AMBER_CLR).setFontSize(9))
                    .setBackgroundColor(GRAND_BG)
                    .setTextAlignment(TextAlignment.RIGHT).setPadding(5));     // 9
            table.addCell(new Cell()
                    .add(new Paragraph(fmt2(sumWt)).setBold()
                            .setFontColor(AMBER_CLR).setFontSize(10))
                    .setBackgroundColor(GRAND_BG)
                    .setTextAlignment(TextAlignment.RIGHT).setPadding(5));     // 10

            doc.add(table);

            // ── Yarn-wise summary section ─────────────────────────
            if (summary != null && !summary.isEmpty()) {
                doc.add(new Paragraph("\n"));
                doc.add(new Paragraph("Yarn-wise Summary")
                        .setBold().setFontSize(13)
                        .setFontColor(new DeviceRgb(0x06, 0x4e, 0x3b)));

                float[] sColW = {28f, 14f, 19f, 19f, 20f};
                Table st = new Table(UnitValue.createPercentArray(sColW));
                st.setWidth(UnitValue.createPercentValue(100));

                for (String h : new String[]{
                        "Yarn Count", "Type", "Total Bags", "Total Cones", "Total Wt (kg)"})
                    st.addHeaderCell(hdr(h));

                BigDecimal gWt = BigDecimal.ZERO;
                int gBags = 0, gCones = 0;

                for (int i = 0; i < summary.size(); i++) {
                    Object[] r   = summary.get(i);
                    boolean  alt = (i % 2 == 1);
                    int    bags  = r[2] != null ? ((Number) r[2]).intValue() : 0;
                    int    cones = r[3] != null ? ((Number) r[3]).intValue() : 0;
                    BigDecimal wt = r[4] != null
                            ? new BigDecimal(r[4].toString()) : BigDecimal.ZERO;

                    st.addCell(cell(r[0] != null ? r[0].toString() : "", alt));
                    st.addCell(cell(r[1] != null ? r[1].toString() : "", alt)
                            .setTextAlignment(TextAlignment.CENTER));
                    st.addCell(right(String.valueOf(bags), alt));
                    st.addCell(right(String.valueOf(cones), alt));
                    st.addCell(right(fmt2(wt), alt));

                    gBags  += bags; gCones += cones;
                    gWt     = gWt.add(wt);
                }

                // Summary grand total (3 value cols)
                st.addCell(new Cell(1, 2)
                        .add(new Paragraph("GRAND TOTAL").setBold()
                                .setFontColor(ColorConstants.WHITE).setFontSize(9))
                        .setBackgroundColor(GRAND_BG)
                        .setTextAlignment(TextAlignment.RIGHT).setPadding(5));
                st.addCell(new Cell()
                        .add(new Paragraph(String.valueOf(gBags)).setBold()
                                .setFontColor(AMBER_CLR).setFontSize(9))
                        .setBackgroundColor(GRAND_BG)
                        .setTextAlignment(TextAlignment.RIGHT).setPadding(5));
                st.addCell(new Cell()
                        .add(new Paragraph(String.valueOf(gCones)).setBold()
                                .setFontColor(AMBER_CLR).setFontSize(9))
                        .setBackgroundColor(GRAND_BG)
                        .setTextAlignment(TextAlignment.RIGHT).setPadding(5));
                st.addCell(new Cell()
                        .add(new Paragraph(fmt2(gWt)).setBold()
                                .setFontColor(AMBER_CLR).setFontSize(10))
                        .setBackgroundColor(GRAND_BG)
                        .setTextAlignment(TextAlignment.RIGHT).setPadding(5));
                doc.add(st);
            }
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

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(out.toFile()));
             Document doc    = new Document(pdf, PageSize.A4.rotate())) {

            headerBlock(doc, "Money Report", worker, from, to);

            Table t = new Table(UnitValue.createPercentArray(
                    new float[]{15, 20, 20, 20, 25}))
                    .useAllAvailableWidth();

            for (String h : new String[]{"Date", "Challan", "Worker", "Amount", "Remark"})
                t.addHeaderCell(hdr(h));

            BigDecimal total = BigDecimal.ZERO;

            for (int i = 0; i < rows.size(); i++) {
                MoneyReceipt e = rows.get(i);
                boolean alt = i % 2 == 1;
                t.addCell(cell(fmt(e.getReceiptDate()), alt));
                t.addCell(cell(e.getChallanNo(), alt));
                t.addCell(cell(e.getJobWorker() != null ? e.getJobWorker().getName() : "", alt));
                t.addCell(right(e.getAmount()   != null ? e.getAmount().toPlainString() : "0", alt));
                t.addCell(cell(e.getRemark(), alt));
                if (e.getAmount() != null) total = total.add(e.getAmount());
            }

            t.addCell(new Cell(1, 3).add(new Paragraph("GRAND TOTAL").setBold()
                            .setFontColor(ColorConstants.WHITE).setFontSize(9))
                    .setBackgroundColor(GRAND_BG).setPadding(5));
            t.addCell(grand(total.toPlainString()));
            t.addCell(new Cell());
            doc.add(t);
        }
    }
    public void exportChallanReport(
            List<ChallanReportRow> data,
            Path file,
            String companyName,
            String workerName,
            LocalDate from,
            LocalDate to
    ) throws Exception {

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(file.toFile()));
             Document doc = new Document(pdf, PageSize.A4)) {

            // 🔷 USE YOUR COMMON HEADER STYLE
            doc.add(new Paragraph(companyName)
                    .setBold().setFontSize(18)
                    .setTextAlignment(TextAlignment.CENTER));

            headerBlock(doc, "Challan Report", workerName, from, to);

            // 🔷 TABLE (MODERN STYLE)
            Table table = new Table(UnitValue.createPercentArray(new float[]{8, 22, 20, 25}));
            table.setWidth(UnitValue.createPercentValue(100));

            table.addHeaderCell(hdr("Sl No"));
            table.addHeaderCell(hdr("Date"));
            table.addHeaderCell(hdr("Challan"));
            table.addHeaderCell(hdr("Amount (₹)"));

            BigDecimal total = BigDecimal.ZERO;

            for (int i = 0; i < data.size(); i++) {

                ChallanReportRow r = data.get(i);
                boolean alt = (i % 2 == 1);

                table.addCell(cell(String.valueOf(i + 1), alt)
                        .setTextAlignment(TextAlignment.CENTER));

                table.addCell(cell(fmt(r.getDate()), alt));

                table.addCell(cell(r.getChallan(), alt)
                        .setTextAlignment(TextAlignment.CENTER));

                table.addCell(right("₹ " + fmt2(r.getAmount()), alt));

                total = total.add(r.getAmount());
            }

            // 🔷 TOTAL ROW (PRO STYLE)
            table.addCell(new Cell(1, 3)
                    .add(new Paragraph("TOTAL").setBold()
                            .setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(GRAND_BG)
                    .setTextAlignment(TextAlignment.RIGHT));

            table.addCell(new Cell()
                    .add(new Paragraph("₹ " + fmt2(total)).setBold()
                            .setFontColor(AMBER_CLR))
                    .setBackgroundColor(GRAND_BG)
                    .setTextAlignment(TextAlignment.RIGHT));

            doc.add(table);
        }
    }
    public void exportLedger(
            List<WorkerLedgerRow> data,
            Path file,
            String workerName
    ) throws Exception {

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(file.toFile()));
             Document doc = new Document(pdf, PageSize.A4)) {

            headerBlock(doc, "Advance Ledger", workerName, null, null);

            Table table = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25}));
            table.setWidth(UnitValue.createPercentValue(100));

            table.addHeaderCell(hdr("Date"));
            table.addHeaderCell(hdr("Type"));
            table.addHeaderCell(hdr("Amount"));
            table.addHeaderCell(hdr("Balance"));

            BigDecimal total = BigDecimal.ZERO;

            for (int i = 0; i < data.size(); i++) {

                WorkerLedgerRow r = data.get(i);
                boolean alt = (i % 2 == 1);

                table.addCell(cell(fmt(r.getDate()), alt));
                table.addCell(cell(r.getType(), alt));
                table.addCell(right("₹ " + fmt2(r.getAmount()), alt));
                table.addCell(right("₹ " + fmt2(r.getBalance()), alt));

                total = total.add(
                        r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO
                );
            }

            // 🔷 TOTAL
            table.addCell(new Cell(1, 2)
                    .add(new Paragraph("TOTAL").setBold()
                            .setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(GRAND_BG)
                    .setTextAlignment(TextAlignment.RIGHT));

            table.addCell(new Cell()
                    .add(new Paragraph("₹ " + fmt2(total)).setBold()
                            .setFontColor(AMBER_CLR))
                    .setBackgroundColor(GRAND_BG)
                    .setTextAlignment(TextAlignment.RIGHT));

            table.addCell(new Cell().setBackgroundColor(GRAND_BG));

            doc.add(table);
        }
    }
    public void exportAccountLedger(List<WorkerAccountLedgerRow> data, Path path) throws Exception {

        // ✅ iText 7 correct way
        PdfWriter writer = new PdfWriter(new FileOutputStream(path.toFile()));
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf);

        doc.add(new Paragraph("ACCOUNT LEDGER").setBold().setFontSize(14));

        Table table = new Table(5);

        // HEADER
        table.addHeaderCell(new Cell().add(new Paragraph("Date")));
        table.addHeaderCell(new Cell().add(new Paragraph("Particular")));
        table.addHeaderCell(new Cell().add(new Paragraph("Debit")));
        table.addHeaderCell(new Cell().add(new Paragraph("Credit")));
        table.addHeaderCell(new Cell().add(new Paragraph("Balance")));

        // DATA
        for (WorkerAccountLedgerRow r : data) {
            table.addCell(new Cell().add(new Paragraph(r.getDate().toString())));
            table.addCell(new Cell().add(new Paragraph(r.getParticular())));
            table.addCell(new Cell().add(new Paragraph(format(r.getDebit()))));
            table.addCell(new Cell().add(new Paragraph(format(r.getCredit()))));
            table.addCell(new Cell().add(new Paragraph(format(r.getBalance()))));
        }

        doc.add(table);

        // ✅ close (no open() in iText7)
        doc.close();
    }
    private String format(java.math.BigDecimal v) {
        return String.format("%.2f", v == null ? java.math.BigDecimal.ZERO : v);
    }
    public void exportChallanInvoice(
            String companyName,
            String workerName,
            String challanNo,
            String dateText,
            List<com.jobwork.domain.ProductEntry> data,
            Path path
    ) throws Exception {

        PdfWriter writer = new PdfWriter(new FileOutputStream(path.toFile()));
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf);

        // ===== HEADER =====
        doc.add(new Paragraph(companyName)
                .setBold()
                .setFontSize(16)
                .setTextAlignment(TextAlignment.CENTER));

        doc.add(new Paragraph("CHALLAN INVOICE")
                .setBold()
                .setFontSize(13)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(10));

        doc.add(new Paragraph("Challan No: " + challanNo));
        doc.add(new Paragraph("Worker: " + workerName));
        doc.add(new Paragraph("Date: " + dateText).setMarginBottom(10));

        // ===== TABLE =====
        float[] widths = {4, 2, 2, 2};
        Table table = new Table(UnitValue.createPercentArray(widths))
                .useAllAvailableWidth();

        // Header style
        Cell h = new Cell().setBold()
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setTextAlignment(TextAlignment.CENTER);

        table.addHeaderCell(
                new Cell()
                        .add(new Paragraph("Product"))
                        .setBold()
                        .setBackgroundColor(ColorConstants.LIGHT_GRAY)
        );

        table.addHeaderCell(
                new Cell()
                        .add(new Paragraph("Qty"))
                        .setBold()
                        .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                        .setTextAlignment(TextAlignment.CENTER)
        );

        table.addHeaderCell(
                new Cell()
                        .add(new Paragraph("Rate"))
                        .setBold()
                        .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                        .setTextAlignment(TextAlignment.RIGHT)
        );

        table.addHeaderCell(
                new Cell()
                        .add(new Paragraph("Amount"))
                        .setBold()
                        .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                        .setTextAlignment(TextAlignment.RIGHT)
        );

        // ===== DATA =====
        BigDecimal total = BigDecimal.ZERO;

        for (var p : data) {

            BigDecimal amount = p.getTotalAmount();
            total = total.add(amount == null ? BigDecimal.ZERO : amount);

            table.addCell(new Cell().add(new Paragraph(
                    p.getProductName().getName())));

            table.addCell(new Cell().add(new Paragraph(
                            p.getQuantity().toString()))
                    .setTextAlignment(TextAlignment.RIGHT));

            table.addCell(new Cell().add(new Paragraph(
                            com.jobwork.util.FormatUtil.money(
                                    p.getProductName().getDefaultRate())))
                    .setTextAlignment(TextAlignment.RIGHT));

            table.addCell(new Cell().add(new Paragraph(
                            com.jobwork.util.FormatUtil.money(amount)))
                    .setTextAlignment(TextAlignment.RIGHT));
        }

        // ===== TOTAL ROW =====
        Cell totalCell = new Cell(1, 3)
                .add(new Paragraph("TOTAL"))
                .setBold()
                .setTextAlignment(TextAlignment.RIGHT);

        table.addCell(totalCell);

        table.addCell(new Cell()
                .add(new Paragraph(
                        com.jobwork.util.FormatUtil.money(total)))
                .setBold()
                .setTextAlignment(TextAlignment.RIGHT));

        doc.add(table);

        // Footer line
        doc.add(new Paragraph("\nAuthorized Signatory")
                .setTextAlignment(TextAlignment.RIGHT));

        doc.close();
    }
    public void exportAccountLedgerPro(
            String company,
            String worker,
            String dateRange,
            List<WorkerAccountLedgerRow> data,
            Path path
    ) throws Exception {

        PdfWriter writer = new PdfWriter(path.toFile());
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf);

        // ===== HEADER =====
        doc.add(new Paragraph(company)
                .setBold()
                .setFontSize(16)
                .setTextAlignment(TextAlignment.CENTER));

        doc.add(new Paragraph("ACCOUNT LEDGER")
                .setBold()
                .setFontSize(13)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(10));

        doc.add(new Paragraph("Worker: " + worker));
        doc.add(new Paragraph("Date Range: " + dateRange)
                .setMarginBottom(10));

        // ===== TABLE =====
        float[] widths = {2, 4, 2, 2, 2};
        Table table = new Table(UnitValue.createPercentArray(widths))
                .useAllAvailableWidth();

        // HEADER CELLS
        table.addHeaderCell(header("Date"));
        table.addHeaderCell(header("Particular"));
        table.addHeaderCell(header("Debit"));
        table.addHeaderCell(header("Credit"));
        table.addHeaderCell(header("Balance"));

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (WorkerAccountLedgerRow r : data) {

            table.addCell(cell(r.getDate() == null ? "" :
                    r.getDate().toString()));

            table.addCell(cell(r.getParticular()));

            table.addCell(amountCell(r.getDebit(), true));
            table.addCell(amountCell(r.getCredit(), false));
            table.addCell(amountCell(r.getBalance(), false));

            totalDebit = totalDebit.add(
                    r.getDebit() == null ? BigDecimal.ZERO : r.getDebit());

            totalCredit = totalCredit.add(
                    r.getCredit() == null ? BigDecimal.ZERO : r.getCredit());
        }

        doc.add(table);

        // ===== SUMMARY =====
        BigDecimal balance = totalDebit.subtract(totalCredit);

        doc.add(new Paragraph("\n"));

        doc.add(new Paragraph("TOTAL WORK: " +
                FormatUtil.money(totalDebit)).setBold());

        doc.add(new Paragraph("TOTAL PAID: " +
                FormatUtil.money(totalCredit)).setBold());

        doc.add(new Paragraph("BALANCE: " +
                FormatUtil.money(balance))
                .setBold()
                .setFontColor(balance.signum() >= 0
                        ? ColorConstants.RED
                        : ColorConstants.GREEN));

        doc.close();
    }
    private Cell header(String text) {
        return new Cell()
                .add(new Paragraph(text))
                .setBold()
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setTextAlignment(TextAlignment.CENTER);
    }

    private Cell cell(String text) {
        return new Cell()
                .add(new Paragraph(text));
    }

    private Cell amountCell(BigDecimal val, boolean isDebit) {

        String txt = FormatUtil.money(val);

        Paragraph p = new Paragraph(txt);

        if (val != null && val.compareTo(BigDecimal.ZERO) > 0) {
            p.setFontColor(isDebit
                    ? ColorConstants.RED
                    : ColorConstants.GREEN);
        }

        return new Cell()
                .add(p)
                .setTextAlignment(TextAlignment.RIGHT);
    }

}