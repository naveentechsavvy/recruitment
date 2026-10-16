package com.example.recruitment.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SchemaUpgradeTest {

    private static final String ALTER = "alter table app_users modify role varchar(20) not null";

    @Test
    void anOldFixedListColumnIsWidened() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("enum('ADMIN','HIRING_MANAGER','RECRUITER')"));

        new SchemaUpgrade(jdbc).run();

        verify(jdbc).execute(ALTER);
    }

    @Test
    void aColumnThatAlreadyKnowsTheNewRolesIsLeftAlone() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("enum('ADMIN','HIRING_MANAGER','HR_BP','MANAGER','RECRUITER')"));

        new SchemaUpgrade(jdbc).run();

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void aPlainTextColumnIsLeftAlone() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of("varchar(20)"));

        new SchemaUpgrade(jdbc).run();

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void aMissingTableDoesNothing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());

        new SchemaUpgrade(jdbc).run();

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void aFailureNeverStopsTheApplicationFromStarting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class))).thenThrow(new RuntimeException("no permission"));

        assertDoesNotThrow(() -> new SchemaUpgrade(jdbc).run());
    }

    @Test
    void needsUpgradeOnlyForOldEnumColumns() {
        assertTrue(SchemaUpgrade.needsUpgrade("enum('ADMIN','RECRUITER')"));
        assertFalse(SchemaUpgrade.needsUpgrade("varchar(20)"));
        assertFalse(SchemaUpgrade.needsUpgrade(null));
        assertFalse(SchemaUpgrade.needsUpgrade("enum('ADMIN','HR_BP','MANAGER')"));
    }

    // ---------------------------------------------------------------- old unique index on email

    private static JdbcTemplate withIndexes(List<String> names) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(eq(SchemaUpgrade.FIND_COLUMN_TYPE), eq(String.class))).thenReturn(List.of("varchar(20)"));
        when(jdbc.queryForList(eq(SchemaUpgrade.FIND_EMAIL_ONLY_UNIQUE_INDEXES), eq(String.class))).thenReturn(names);
        return jdbc;
    }

    @Test
    void theOldUniqueEmailIndexIsDropped() {
        JdbcTemplate jdbc = withIndexes(List.of("uk_role_assignment_email"));

        new SchemaUpgrade(jdbc).run();

        verify(jdbc).execute("alter table client_role_assignments drop index `uk_role_assignment_email`");
    }

    @Test
    void nothingIsDroppedWhenThereIsNoOldIndex() {
        JdbcTemplate jdbc = withIndexes(List.of());

        new SchemaUpgrade(jdbc).run();

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void anUnsafeIndexNameIsNeverUsedInAStatement() {
        JdbcTemplate jdbc = withIndexes(List.of("x`; drop table app_users; --", "bad name"));

        new SchemaUpgrade(jdbc).run();

        verify(jdbc, never()).execute(anyString());
        assertTrue(SchemaUpgrade.isSafeName("uk_role_assignment_email"));
        assertFalse(SchemaUpgrade.isSafeName(null));
        assertFalse(SchemaUpgrade.isSafeName("a b"));
    }

    @Test
    void aFailureWhileLookingForTheIndexNeverStopsStartup() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(eq(SchemaUpgrade.FIND_EMAIL_ONLY_UNIQUE_INDEXES), eq(String.class)))
                .thenThrow(new RuntimeException("no permission"));

        assertDoesNotThrow(() -> new SchemaUpgrade(jdbc).run());
    }
}
