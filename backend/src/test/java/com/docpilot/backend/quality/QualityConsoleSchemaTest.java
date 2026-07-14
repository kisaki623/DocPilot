package com.docpilot.backend.quality;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class QualityConsoleSchemaTest {

    @Test
    void shouldContainQualityConsolePersistenceMigration() throws IOException {
        String sql = readSqlScript("/sql/011_init_quality_console_persistence.sql");

        assertThat(sql).contains("COLUMN_NAME = 'is_internal_admin'");
        assertThat(sql).contains("ALTER TABLE tb_user ADD COLUMN is_internal_admin");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS tb_quality_run");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS tb_quality_run_gate");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS tb_quality_run_case");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS tb_quality_import_event");
        assertThat(sql).contains("UNIQUE KEY uk_quality_run_marker (marker)");
        assertThat(sql).contains("UNIQUE KEY uk_quality_run_source_sha256 (source_sha256)");
        assertThat(sql).contains("KEY idx_quality_import_sha256 (artifact_sha256)");
    }

    private String readSqlScript(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
