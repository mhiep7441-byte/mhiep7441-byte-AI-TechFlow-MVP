package vn.techflow.manager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:techflow_migrations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class FlywayMigrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void appliesAllProductionMigrationsIncludingResearchAndTikTokTables() {
        Integer version = jdbc.queryForObject(
                "select max(cast(\"version\" as integer)) from \"flyway_schema_history\" where \"success\" = true",
                Integer.class);
        Integer tiktokTables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where lower(table_name) = 'tiktok_accounts'",
                Integer.class);
        Integer researchColumns = jdbc.queryForObject(
                "select count(*) from information_schema.columns where lower(table_name) = 'work_tasks' and lower(column_name) = 'research_json'",
                Integer.class);

        assertTrue(version != null && version >= 4);
        assertEquals(1, tiktokTables);
        assertEquals(1, researchColumns);
    }
}
