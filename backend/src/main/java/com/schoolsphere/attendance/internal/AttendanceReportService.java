package com.schoolsphere.attendance.internal;

import org.openpdf.text.Document;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

/** CSV and PDF exports for attendance reports (spec 11.6). */
@Service
public class AttendanceReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    public byte[] classReportCsv(AttendanceService.ClassReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("Student,Admission number,Present,Absent,Late,Excused,Attendance rate (%)\n");
        for (AttendanceService.StudentReportRow row : report.rows()) {
            sb.append(csv(row.studentName())).append(',')
                    .append(csv(row.admissionNumber())).append(',')
                    .append(row.present()).append(',')
                    .append(row.absent()).append(',')
                    .append(row.late()).append(',')
                    .append(row.excused()).append(',')
                    .append(row.attendanceRate()).append('\n');
        }
        AttendanceService.AttendanceCounts totals = report.totals();
        sb.append("TOTAL,, ").append(totals.present()).append(',')
                .append(totals.absent()).append(',')
                .append(totals.late()).append(',')
                .append(totals.excused()).append(",\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] studentHistoryCsv(AttendanceService.StudentSummary summary,
                                    List<AttendanceService.EventView> history) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("Date and time,Status,Reason\n");
        for (AttendanceService.EventView event : history) {
            sb.append(event.eventTime()).append(',')
                    .append(csv(event.status())).append(',')
                    .append(csv(event.reason())).append('\n');
        }
        sb.append("SUMMARY,").append(csv(summary.studentName())).append(',')
                .append("present=").append(summary.present())
                .append(" absent=").append(summary.absent())
                .append(" late=").append(summary.late())
                .append(" excused=").append(summary.excused()).append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] classReportPdf(String schoolName, AttendanceService.ClassReport report) {
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(new Paragraph(schoolName == null ? "Attendance report" : schoolName,
                    new Font(Font.HELVETICA, 16, Font.BOLD)));
            document.add(new Paragraph("Class attendance, " + DATE.format(report.from()) + " to "
                    + DATE.format(report.to()), new Font(Font.HELVETICA, 10)));
            document.add(new Paragraph(" "));
            PdfPTable table = new PdfPTable(new float[]{4, 3, 2, 2, 2, 2, 3});
            table.setWidthPercentage(100);
            for (String header : List.of("Student", "Admission", "Present", "Absent", "Late", "Excused", "Rate %")) {
                PdfPCell cell = new PdfPCell(new Paragraph(header, new Font(Font.HELVETICA, 9, Font.BOLD)));
                table.addCell(cell);
            }
            Font body = new Font(Font.HELVETICA, 9);
            for (AttendanceService.StudentReportRow row : report.rows()) {
                table.addCell(new Paragraph(row.studentName(), body));
                table.addCell(new Paragraph(row.admissionNumber() == null ? "" : row.admissionNumber(), body));
                table.addCell(new Paragraph(String.valueOf(row.present()), body));
                table.addCell(new Paragraph(String.valueOf(row.absent()), body));
                table.addCell(new Paragraph(String.valueOf(row.late()), body));
                table.addCell(new Paragraph(String.valueOf(row.excused()), body));
                table.addCell(new Paragraph(String.valueOf(row.attendanceRate()), body));
            }
            document.add(table);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render attendance PDF", e);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    public byte[] studentReportPdf(AttendanceService.StudentSummary summary,
                                   List<AttendanceService.EventView> history) {
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(new Paragraph("Attendance report: " + summary.studentName(),
                    new Font(Font.HELVETICA, 16, Font.BOLD)));
            document.add(new Paragraph(DATE.format(summary.from()) + " to " + DATE.format(summary.to())
                    + " | Present " + summary.present() + ", absent " + summary.absent()
                    + ", late " + summary.late() + ", excused " + summary.excused(),
                    new Font(Font.HELVETICA, 10)));
            document.add(new Paragraph(" "));
            PdfPTable table = new PdfPTable(new float[]{3, 2, 5});
            table.setWidthPercentage(100);
            for (String header : List.of("When", "Status", "Reason")) {
                table.addCell(new PdfPCell(new Paragraph(header, new Font(Font.HELVETICA, 9, Font.BOLD))));
            }
            Font body = new Font(Font.HELVETICA, 9);
            for (AttendanceService.EventView event : history) {
                table.addCell(new Paragraph(String.valueOf(event.eventTime()), body));
                table.addCell(new Paragraph(event.status(), body));
                table.addCell(new Paragraph(event.reason() == null ? "" : event.reason(), body));
            }
            document.add(table);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render attendance PDF", e);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
