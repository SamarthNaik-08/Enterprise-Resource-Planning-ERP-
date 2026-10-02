package com.erp.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the FULL stack against a real PostgreSQL 16 database (Testcontainers).
 * This is the production-parity check: Flyway migrations must apply cleanly
 * on genuine PostgreSQL, not just on H2.
 *
 * Skipped automatically when Docker is not available (e.g. minimal CI runners) —
 * run `mvn test` on any Docker-enabled machine/CI to execute it.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class PostgresMigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayMigrationsApply_onRealPostgres_andSeederRuns() {
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'",
                Integer.class);
        assertThat(tables).isGreaterThanOrEqualTo(9); // 7 domain tables + refresh_tokens + history

        Integer users = jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);
        assertThat(users).isGreaterThanOrEqualTo(3); // seeded admin/manager/employee

        Integer products = jdbc.queryForObject("SELECT count(*) FROM products", Integer.class);
        assertThat(products).isGreaterThanOrEqualTo(6); // seeded catalog

        // the optimistic-lock version column exists
        Integer versionCols = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns " +
                "WHERE table_name = 'products' AND column_name = 'version'",
                Integer.class);
        assertThat(versionCols).isEqualTo(1);
    }
}
