package com.schoolsphere.people.internal;

import com.schoolsphere.academics.api.AcademicsApi;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Students CSV import (spec 11.1): dry-run validation produces a per-row report; the commit path
 * is idempotent and updates the names of a student when the admission number already exists. The
 * class and stream columns are validated against the academics module; enrollment itself stays
 * with the enrollment endpoints.
 */
@Service
@TenantScoped
public class CsvImportService {

    private final StudentRepository studentRepository;
    private final AcademicsApi academicsApi;
    private final JdbcTemplate jdbcTemplate;

    public CsvImportService(StudentRepository studentRepository, AcademicsApi academicsApi,
                            JdbcTemplate jdbcTemplate) {
        this.studentRepository = studentRepository;
        this.academicsApi = academicsApi;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ImportReport importStudents(byte[] content, boolean dryRun) {
        UUID schoolId = TenantContext.schoolId();
        List<List<String>> records = parseRecords(decode(content));
        if (records.isEmpty()) {
            throw ApiException.badRequest("empty_csv", "The uploaded file is empty.");
        }
        Map<String, Integer> columns = headerColumns(records.get(0));
        if (!columns.containsKey("admission_number") || !columns.containsKey("first_name")
                || !columns.containsKey("last_name")) {
            throw ApiException.badRequest("invalid_csv",
                    "The file must contain admission_number, first_name, and last_name columns.");
        }

        Map<String, Optional<UUID>> classCache = new HashMap<>();
        List<RowResult> rows = new ArrayList<>();
        int total = 0;
        int created = 0;
        int updated = 0;
        int errors = 0;
        for (int i = 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            if (record.stream().allMatch(String::isBlank)) {
                continue;
            }
            total++;
            int line = i + 1;
            try {
                ParsedRow row = parseRow(schoolId, record, columns, classCache);
                Student existing = studentRepository
                        .findBySchoolIdAndAdmissionNumberAndDeletedAtIsNull(schoolId, row.admissionNumber())
                        .orElse(null);
                if (dryRun) {
                    rows.add(new RowResult(line, "ok", existing == null ? "would create student" : "would update student"));
                    if (existing == null) {
                        created++;
                    } else {
                        updated++;
                    }
                } else if (existing == null) {
                    Student student = new Student();
                    student.setSchoolId(schoolId);
                    student.setAdmissionNumber(row.admissionNumber());
                    student.setFirstName(row.firstName());
                    student.setLastName(row.lastName());
                    student.setOtherNames(row.otherNames());
                    student.setDateOfBirth(row.dateOfBirth());
                    student.setGender(row.gender());
                    studentRepository.save(student);
                    created++;
                    rows.add(new RowResult(line, "created", "student created"));
                } else {
                    existing.setFirstName(row.firstName());
                    existing.setLastName(row.lastName());
                    existing.setOtherNames(row.otherNames());
                    studentRepository.save(existing);
                    updated++;
                    rows.add(new RowResult(line, "updated", "existing student updated"));
                }
            } catch (RowValidationException e) {
                errors++;
                rows.add(new RowResult(line, "error", e.getMessage()));
            }
        }
        return new ImportReport(total, created, updated, errors, rows);
    }

    private static Map<String, Integer> headerColumns(List<String> header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columns.putIfAbsent(header.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        return columns;
    }

    private ParsedRow parseRow(UUID schoolId, List<String> record, Map<String, Integer> columns,
                               Map<String, Optional<UUID>> classCache) {
        String admissionNumber = field(record, columns.get("admission_number"));
        if (admissionNumber.isBlank()) {
            throw new RowValidationException("admission_number is required");
        }
        String firstName = field(record, columns.get("first_name"));
        String lastName = field(record, columns.get("last_name"));
        if (firstName.isBlank() || lastName.isBlank()) {
            throw new RowValidationException("first_name and last_name are required");
        }
        String otherNames = field(record, columns.get("other_names"));
        String gender = field(record, columns.get("gender"));
        LocalDate dateOfBirth = parseDate(field(record, columns.get("date_of_birth")));
        resolveClass(schoolId, record, columns, classCache);
        return new ParsedRow(admissionNumber, firstName, lastName, otherNames.isBlank() ? null : otherNames,
                dateOfBirth, gender.isBlank() ? null : gender);
    }

    private void resolveClass(UUID schoolId, List<String> record, Map<String, Integer> columns,
                              Map<String, Optional<UUID>> classCache) {
        UUID classGroupId = null;
        String classGroupIdValue = field(record, columns.get("class_group_id"));
        String className = field(record, columns.get("class"));
        if (!classGroupIdValue.isBlank()) {
            UUID parsedId;
            try {
                parsedId = UUID.fromString(classGroupIdValue);
            } catch (IllegalArgumentException e) {
                throw new RowValidationException("invalid class_group_id: " + classGroupIdValue);
            }
            if (academicsApi.findClass(schoolId, parsedId).isEmpty()) {
                throw new RowValidationException("unknown class_group_id: " + classGroupIdValue);
            }
            classGroupId = parsedId;
        } else if (!className.isBlank()) {
            // Read-model exception: class names live in the academics module; RLS keeps this lookup
            // tenant-scoped, and name-to-id resolution is not exposed by AcademicsApi.
            Optional<UUID> resolved = classCache.computeIfAbsent(className.toLowerCase(Locale.ROOT), key -> {
                List<UUID> ids = jdbcTemplate.queryForList(
                        "SELECT id FROM class_group WHERE school_id = ? AND deleted_at IS NULL AND lower(name) = lower(?)",
                        UUID.class, schoolId, key);
                return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
            });
            if (resolved.isEmpty()) {
                throw new RowValidationException("unknown class: " + className);
            }
            classGroupId = resolved.get();
        }
        String streamName = field(record, columns.get("stream"));
        if (!streamName.isBlank()) {
            if (classGroupId == null) {
                throw new RowValidationException("stream requires a class or class_group_id");
            }
            List<UUID> streamIds = jdbcTemplate.queryForList(
                    "SELECT id FROM stream WHERE school_id = ? AND class_group_id = ? AND deleted_at IS NULL AND lower(name) = lower(?)",
                    UUID.class, schoolId, classGroupId, streamName);
            if (streamIds.isEmpty()) {
                throw new RowValidationException("unknown stream: " + streamName);
            }
        }
    }

    private static LocalDate parseDate(String value) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new RowValidationException("invalid date_of_birth (expected YYYY-MM-DD): " + value);
        }
    }

    private static String field(List<String> record, Integer index) {
        if (index == null || index >= record.size()) {
            return "";
        }
        String value = record.get(index);
        return value == null ? "" : value.trim();
    }

    private static String decode(byte[] content) {
        String text = new String(content == null ? new byte[0] : content, StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static List<List<String>> parseRecords(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        boolean hasContent = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        value.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    value.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                record.add(value.toString());
                value.setLength(0);
                hasContent = true;
            } else if (c == '\n') {
                record.add(value.toString());
                value.setLength(0);
                records.add(record);
                record = new ArrayList<>();
                hasContent = false;
            } else if (c != '\r') {
                value.append(c);
                if (!Character.isWhitespace(c)) {
                    hasContent = true;
                }
            }
        }
        if (value.length() > 0 || hasContent || !record.isEmpty()) {
            record.add(value.toString());
            records.add(record);
        }
        return records;
    }

    public record ImportReport(int total, int created, int updated, int errors, List<RowResult> rows) {
    }

    public record RowResult(int row, String status, String message) {
    }

    private record ParsedRow(String admissionNumber, String firstName, String lastName, String otherNames,
                             LocalDate dateOfBirth, String gender) {
    }

    private static class RowValidationException extends RuntimeException {

        RowValidationException(String message) {
            super(message);
        }
    }
}
