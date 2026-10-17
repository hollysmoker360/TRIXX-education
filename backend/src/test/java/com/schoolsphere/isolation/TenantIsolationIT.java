package com.schoolsphere.isolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Cross-tenant isolation suite (spec 7.2, 16.3): runs against a real PostgreSQL instance as the
 * non-owner application role {@code schoolsphere_app} and asserts that row-level security leaks
 * nothing. This is the named, separately runnable build target: {@code mvn verify} (failsafe,
 * group {@code isolation}); unit tests stay in {@code mvn test}.
 *
 * <p>Configuration matches the dev profile and can be overridden with
 * SCHOOLSPHERE_TEST_DB_URL, SCHOOLSPHERE_TEST_DB_USER, SCHOOLSPHERE_TEST_DB_PASSWORD,
 * SCHOOLSPHERE_TEST_OWNER_USER, SCHOOLSPHERE_TEST_OWNER_PASSWORD.
 */
@Tag("isolation")
@DisplayName("Cross-tenant row level security isolation")
class TenantIsolationIT {

    private static final String DB_URL = env("SCHOOLSPHERE_TEST_DB_URL", "jdbc:postgresql://localhost:5433/schoolsphere");
    private static final String APP_USER = env("SCHOOLSPHERE_TEST_DB_USER", "schoolsphere_app");
    private static final String APP_PASSWORD = env("SCHOOLSPHERE_TEST_DB_PASSWORD", "schoolsphere_app_dev");
    private static final String OWNER_USER = env("SCHOOLSPHERE_TEST_OWNER_USER", "schoolsphere");
    private static final String OWNER_PASSWORD = env("SCHOOLSPHERE_TEST_OWNER_PASSWORD", "schoolsphere_dev");

    private static final String TENANT_CONTEXT = "SELECT set_config('app.current_school', ?, true)";

    private static UUID schoolA;
    private static UUID schoolB;
    private static UUID userId;
    private static UUID taskA;
    private static UUID taskB;

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Connection openAppConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, APP_USER, APP_PASSWORD);
    }

    private static void setTenantContext(Connection connection, UUID schoolId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(TENANT_CONTEXT)) {
            statement.setString(1, schoolId.toString());
            statement.execute();
        }
    }

    private static int countTasks(Connection connection, String where) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT count(*) FROM task " + where)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        // Owner role runs the migrations (forward-only), exactly like the application at boot.
        Flyway.configure()
                .dataSource(DB_URL, OWNER_USER, OWNER_PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        UUID suffix = UUID.randomUUID();
        schoolA = UUID.nameUUIDFromBytes(("isolation-a" + suffix).getBytes());
        schoolB = UUID.nameUUIDFromBytes(("isolation-b" + suffix).getBytes());
        userId = UUID.nameUUIDFromBytes(("isolation-user" + suffix).getBytes());
        taskA = UUID.nameUUIDFromBytes(("isolation-task-a" + suffix).getBytes());
        taskB = UUID.nameUUIDFromBytes(("isolation-task-b" + suffix).getBytes());

        try (Connection connection = openAppConnection()) {
            connection.setAutoCommit(false);

            // Global tables (spec 7.1): users and schools are outside RLS by design.
            try (PreparedStatement user = connection.prepareStatement(
                    "INSERT INTO app_user (id, email, password_hash, display_name, locale, status, "
                            + "token_version, is_platform_admin, created_at, updated_at, version) "
                            + "VALUES (?, ?, 'x', 'Isolation Fixture', 'en', 'active', 0, false, now(), now(), 0)")) {
                user.setObject(1, userId);
                user.setString(2, "isolation-" + suffix + "@example.test");
                user.executeUpdate();
            }
            for (UUID school : new UUID[] {schoolA, schoolB}) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO school (id, name, slug, created_at, updated_at, version) "
                                + "VALUES (?, ?, ?, now(), now(), 0)")) {
                    insert.setObject(1, school);
                    insert.setString(2, "Isolation " + school.toString().substring(0, 8));
                    insert.setString(3, "isolation-" + school.toString().substring(0, 8));
                    insert.executeUpdate();
                }
            }
            connection.commit();

            insertTask(connection, schoolA, taskA, "Fixture task in school A");
            insertTask(connection, schoolB, taskB, "Fixture task in school B");
            connection.commit();
        }
    }

    private static void insertTask(Connection connection, UUID school, UUID taskId, String title) throws SQLException {
        setTenantContext(connection, school);
        insertTaskRow(connection, school, taskId, title);
    }

    /** Inserts a task row WITHOUT touching the tenant context (for negative RLS tests). */
    private static void insertTaskRow(Connection connection, UUID school, UUID taskId, String title)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO task (id, school_id, owner_user_id, title, priority, created_at, updated_at, version) "
                        + "VALUES (?, ?, ?, ?, 'medium', now(), now(), 0)")) {
            insert.setObject(1, taskId);
            insert.setObject(2, school);
            insert.setObject(3, userId);
            insert.setString(4, title);
            insert.executeUpdate();
        }
    }

    @AfterAll
    static void cleanup() throws Exception {
        try (Connection connection = openAppConnection()) {
            connection.setAutoCommit(false);
            for (UUID school : new UUID[] {schoolA, schoolB}) {
                setTenantContext(connection, school);
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM task WHERE school_id = ?")) {
                    delete.setObject(1, school);
                    delete.executeUpdate();
                }
            }
            connection.commit();
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM school WHERE id = ANY (?)")) {
                delete.setArray(1, connection.createArrayOf("uuid", new Object[] {schoolA, schoolB}));
                delete.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM app_user WHERE id = ?")) {
                delete.setObject(1, userId);
                delete.executeUpdate();
            }
            connection.commit();
        }
    }

    @Test
    @DisplayName("Missing tenant context returns zero rows, never all rows")
    void withoutContextNoTenantRowsAreVisible() throws Exception {
        try (Connection connection = openAppConnection()) {
            connection.setAutoCommit(false);
            assertThat(countTasks(connection, "")).isZero();
            connection.rollback();
        }
    }

    @Test
    @DisplayName("Without tenant context INSERTs are rejected by RLS")
    void withoutContextInsertFails() throws Exception {
        try (Connection connection = openAppConnection()) {
            connection.setAutoCommit(false);
            UUID rogueId = UUID.randomUUID();
            assertThatThrownBy(() -> insertTaskRow(connection, schoolA, rogueId, "should not be inserted"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("row-level security");
            connection.rollback();
        }
    }

    @Test
    @DisplayName("A tenant context sees only its own school's rows")
    void contextScopesReadsToOwnTenant() throws Exception {
        try (Connection connection = openAppConnection()) {
            connection.setAutoCommit(false);
            setTenantContext(connection, schoolA);

            assertThat(countTasks(connection, "")).isEqualTo(1);
            assertThat(countTasks(connection, "WHERE school_id = '" + schoolB + "'")).isZero();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT id FROM task")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject(1).toString()).isEqualTo(taskA.toString());
                assertThat(rs.next()).isFalse();
            }
            connection.rollback();
        }
    }

    @Test
    @DisplayName("Writing another tenant's school_id is rejected")
    void crossTenantWriteIsRejected() throws Exception {
        try (Connection connection = openAppConnection()) {
            connection.setAutoCommit(false);
            setTenantContext(connection, schoolA);
            UUID rogueId = UUID.randomUUID();

            assertThatThrownBy(() -> insertTaskRow(connection, schoolB, rogueId, "cross tenant write"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("row-level security");
            connection.rollback();
        }
    }

    @Test
    @DisplayName("Tenant context is transaction-local and cannot leak across pooled connections")
    void contextIsTransactionLocal() throws Exception {
        try (Connection connection = openAppConnection()) {
            connection.setAutoCommit(false);
            setTenantContext(connection, schoolA);
            assertThat(countTasks(connection, "")).isEqualTo(1);
            connection.commit();

            // Same physical connection, new transaction, context never re-set: must see nothing.
            assertThat(countTasks(connection, "")).isZero();
            connection.rollback();
        }
    }

    @Test
    @DisplayName("Tombstoned rows stay invisible to plain reads until purged")
    void softDeletedRowsFollowNormalScoping() throws Exception {
        try (Connection connection = openAppConnection()) {
            connection.setAutoCommit(false);
            setTenantContext(connection, schoolA);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE task SET deleted_at = now() WHERE id = ?")) {
                update.setObject(1, taskA);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            connection.commit();

            connection.setAutoCommit(false);
            setTenantContext(connection, schoolA);
            assertThat(countTasks(connection, "WHERE deleted_at IS NULL")).isZero();
            assertThat(countTasks(connection, "")).isEqualTo(1); // tombstone still in tenant scope for sync
            connection.rollback();

            // Restore for other tests / cleanup.
            connection.setAutoCommit(false);
            setTenantContext(connection, schoolA);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE task SET deleted_at = NULL WHERE id = ?")) {
                update.setObject(1, taskA);
                update.executeUpdate();
            }
            connection.commit();
        }
    }
}
