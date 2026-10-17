package com.schoolsphere.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schoolsphere.audit.internal.AuditLogQueryService.AuditLogEntry;
import com.schoolsphere.common.api.PageResponse;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Paging math, filter assembly, and CSV escaping for the audit read API (spec 13.2). The JDBC
 * layer is mocked; the SQL is assembled in the service, so the tests assert on the statements and
 * bind parameters that reach JdbcTemplate.
 */
class AuditLogQueryServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AuditLogQueryService service;
    private UUID schoolId;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new AuditLogQueryService(jdbcTemplate);
        schoolId = UUID.randomUUID();
        TenantContext.set(schoolId, UUID.randomUUID(), UUID.randomUUID(), "device-1");

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-02-01T00:00:00Z");

    @Test
    void clampsOversizedPages() {
        PageResponse<AuditLogEntry> response = service.query(null, null, null, null, null, 2, 500);

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(AuditLogQueryService.MAX_SIZE);
        assertThat(AuditLogQueryService.MAX_SIZE).isEqualTo(200);

        Object[] args = captureQueryArgs();
        assertThat(args[args.length - 2]).isEqualTo(200);
        assertThat(args[args.length - 1]).isEqualTo(400);
    }

    @Test
    void clampsNegativePageAndZeroSize() {
        PageResponse<AuditLogEntry> response = service.query(null, null, null, null, null, -5, 0);

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(1);

        Object[] args = captureQueryArgs();
        assertThat(args[args.length - 2]).isEqualTo(1);
        assertThat(args[args.length - 1]).isEqualTo(0);
    }

    @Test
    void assemblesEveryFilterInOrder() {
        UUID entityId = UUID.randomUUID();
        service.query("auth.login", "app_user", entityId, FROM, TO, 1, 10);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), args.capture());

        assertThat(sql.getValue())
                .contains("WHERE school_id = ?")
                .contains("AND action = ?")
                .contains("AND entity = ?")
                .contains("AND entity_id = ?")
                .contains("AND created_at >= ?")
                .contains("AND created_at <= ?")
                .contains("ORDER BY created_at DESC, id DESC")
                .contains("LIMIT ? OFFSET ?");
        assertThat(args.getValue())
                .containsExactly(schoolId, "auth.login", "app_user", entityId, FROM, TO, 10, 10);
    }

    @Test
    void ignoresBlankAndMissingFilters() {
        service.query("  ", "", null, null, null, 0, 50);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));

        assertThat(sql.getValue())
                .contains("WHERE school_id = ?")
                .doesNotContain("action = ?")
                .doesNotContain("entity = ?")
                .doesNotContain("entity_id = ?")
                .doesNotContain("created_at >=")
                .doesNotContain("created_at <=");
    }

    @Test
    void emptyResultsProduceAnEmptyPage() {
        PageResponse<AuditLogEntry> response = service.query("does.not.exist", null, null, null, null, 0, 50);

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(50);
    }

    @Test
    void getByIdReturnsTheEntryWhenFound() {
        AuditLogEntry entry = entry("auth.login");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(entry));

        assertThat(service.get(entry.id())).isEqualTo(entry);
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("audit_entry_not_found"));
    }

    @Test
    void csvExportStartsWithBomAndEscapesValues() {
        AuditLogEntry entry = new AuditLogEntry(UUID.randomUUID(), schoolId, UUID.randomUUID(), UUID.randomUUID(),
                "role\"changed, really", "app_user", UUID.randomUUID(), "{\"role\": \"teacher\"}", null,
                "device-1", "10.0.0.1", Instant.parse("2026-01-15T08:30:00Z"));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(entry));

        byte[] csv = service.exportCsv(null, null, null, null, null);

        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(csv[0]).isEqualTo((byte) 0xEF);
        assertThat(csv[1]).isEqualTo((byte) 0xBB);
        assertThat(csv[2]).isEqualTo((byte) 0xBF);
        assertThat(text).contains("Id,Created at,Action,Entity");
        assertThat(text).contains("\"role\"\"changed, really\"");
        assertThat(text).contains("2026-01-15T08:30:00Z");

        Object[] args = captureQueryArgs();
        assertThat(args[args.length - 1]).isEqualTo(10_000);
    }

    private Object[] captureQueryArgs() {
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), args.capture());
        return args.getValue();
    }

    private static AuditLogEntry entry(String action) {
        return new AuditLogEntry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                action, "app_user", UUID.randomUUID(), null, null, null, null, Instant.now());
    }
}
