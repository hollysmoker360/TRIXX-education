package com.schoolsphere.audit.web;

import com.schoolsphere.audit.internal.AuditLogQueryService;
import com.schoolsphere.common.api.PageResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit log reads for owners, principals, admins, and IT admins (spec 8.3, 13.2). The log is
 * append-only: there are no update or delete endpoints.
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    public AuditLogController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    @GetMapping
    @PreAuthorize("@policy.can('audit.view')")
    public PageResponse<AuditLogQueryService.AuditLogEntry> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return auditLogQueryService.query(action, entity, entityId, from, to, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@policy.can('audit.view')")
    public AuditLogQueryService.AuditLogEntry get(@PathVariable UUID id) {
        return auditLogQueryService.get(id);
    }

    @GetMapping("/export.csv")
    @PreAuthorize("@policy.can('audit.view')")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) String action,
                                            @RequestParam(required = false) String entity,
                                            @RequestParam(required = false) UUID entityId,
                                            @RequestParam(required = false) Instant from,
                                            @RequestParam(required = false) Instant to) {
        byte[] csv = auditLogQueryService.exportCsv(action, entity, entityId, from, to);
        return download(csv, "text/csv", "audit-log.csv");
    }

    private static ResponseEntity<byte[]> download(byte[] body, String contentType, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
