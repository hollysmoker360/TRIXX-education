package com.schoolsphere.attendance.web;

import com.schoolsphere.attendance.internal.AttendanceReportService;
import com.schoolsphere.attendance.internal.AttendanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceReportService reportService;

    public AttendanceController(AttendanceService attendanceService, AttendanceReportService reportService) {
        this.attendanceService = attendanceService;
        this.reportService = reportService;
    }

    // ----- sessions -----

    @PostMapping("/sessions")
    @PreAuthorize("@policy.can('attendance.take')")
    public AttendanceService.SessionView openSession(@Valid @RequestBody SessionRequest request) {
        return attendanceService.openSession(request.classGroupId(), request.streamId(), request.subjectId(),
                request.sessionType(), request.sessionDate(), request.periodSeq(), request.notes());
    }

    @GetMapping("/sessions")
    @PreAuthorize("@policy.can('attendance.take')")
    public List<AttendanceService.SessionView> sessions(@RequestParam(required = false) LocalDate date,
                                                        @RequestParam(required = false) UUID classGroupId) {
        return attendanceService.listSessions(date, classGroupId);
    }

    @GetMapping("/sessions/{id}")
    @PreAuthorize("@policy.can('attendance.take')")
    public AttendanceService.SessionDetailView sessionDetail(@PathVariable UUID id) {
        return attendanceService.sessionDetail(id);
    }

    @PostMapping("/sessions/{id}/close")
    @PreAuthorize("@policy.can('attendance.take')")
    public AttendanceService.SessionView closeSession(@PathVariable UUID id, @RequestBody(required = false) CloseRequest request) {
        return attendanceService.closeSession(id, request == null ? null : request.notes());
    }

    @DeleteMapping("/sessions/{id}")
    @PreAuthorize("@policy.can('attendance.take')")
    public void deleteSession(@PathVariable UUID id) {
        attendanceService.deleteSession(id);
    }

    @PostMapping("/sessions/{id}/marks")
    @PreAuthorize("@policy.can('attendance.take')")
    public AttendanceService.SessionDetailView mark(@PathVariable UUID id, @Valid @RequestBody MarkRequest request) {
        return attendanceService.mark(id, request.marks().stream()
                .map(m -> new AttendanceService.MarkInput(m.studentId(), m.status(), m.reason()))
                .toList());
    }

    @PostMapping("/events/{id}/correct")
    @PreAuthorize("@policy.can('attendance.correct')")
    public AttendanceService.EventView correct(@PathVariable UUID id, @Valid @RequestBody CorrectRequest request) {
        return attendanceService.correct(id, request.status(), request.reason());
    }

    // ----- per-student views (own student or linked guardian; enforced in the service) -----

    @GetMapping("/students/{studentId}/summary")
    public AttendanceService.StudentSummary studentSummary(@PathVariable UUID studentId,
                                                           @RequestParam LocalDate from,
                                                           @RequestParam LocalDate to) {
        return attendanceService.studentSummary(studentId, from, to);
    }

    @GetMapping("/students/{studentId}/history")
    public List<AttendanceService.EventView> studentHistory(@PathVariable UUID studentId,
                                                            @RequestParam LocalDate from,
                                                            @RequestParam LocalDate to) {
        return attendanceService.studentHistory(studentId, from, to);
    }

    // ----- reports -----

    @GetMapping("/reports/class")
    @PreAuthorize("@policy.can('attendance.report')")
    public AttendanceService.ClassReport classReport(@RequestParam UUID classGroupId,
                                                     @RequestParam LocalDate from,
                                                     @RequestParam LocalDate to) {
        return attendanceService.classReport(classGroupId, from, to);
    }

    @GetMapping("/reports/class.csv")
    @PreAuthorize("@policy.can('attendance.report')")
    public ResponseEntity<byte[]> classReportCsv(@RequestParam UUID classGroupId,
                                                 @RequestParam LocalDate from,
                                                 @RequestParam LocalDate to) {
        byte[] csv = reportService.classReportCsv(attendanceService.classReport(classGroupId, from, to));
        return download(csv, "text/csv", "attendance-" + from + "-" + to + ".csv");
    }

    @GetMapping("/reports/class.pdf")
    @PreAuthorize("@policy.can('attendance.report')")
    public ResponseEntity<byte[]> classReportPdf(@RequestParam UUID classGroupId,
                                                 @RequestParam LocalDate from,
                                                 @RequestParam LocalDate to) {
        byte[] pdf = reportService.classReportPdf(null, attendanceService.classReport(classGroupId, from, to));
        return download(pdf, MediaType.APPLICATION_PDF_VALUE, "attendance-" + from + "-" + to + ".pdf");
    }

    @GetMapping("/students/{studentId}/report.pdf")
    public ResponseEntity<byte[]> studentReportPdf(@PathVariable UUID studentId,
                                                   @RequestParam LocalDate from,
                                                   @RequestParam LocalDate to) {
        byte[] pdf = reportService.studentReportPdf(attendanceService.studentSummary(studentId, from, to),
                attendanceService.studentHistory(studentId, from, to));
        return download(pdf, MediaType.APPLICATION_PDF_VALUE, "attendance-student-" + from + "-" + to + ".pdf");
    }

    private static ResponseEntity<byte[]> download(byte[] body, String contentType, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(body);
    }

    public record SessionRequest(UUID classGroupId, UUID streamId, UUID subjectId, String sessionType,
                                 LocalDate sessionDate, Integer periodSeq, String notes) {
    }

    public record CloseRequest(String notes) {
    }

    public record MarkRequest(@NotEmpty List<Mark> marks) {

        public record Mark(UUID studentId, String status, String reason) {
        }
    }

    public record CorrectRequest(String status, String reason) {
    }
}
