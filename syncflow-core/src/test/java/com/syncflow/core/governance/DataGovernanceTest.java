package com.syncflow.core.governance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataGovernanceTest {

    private final DataGovernanceService svc = new DataGovernanceService();

    @Test
    void classifyEmailColumn() {
        var tags = svc.classifyColumns("users", List.of("email"));
        assertTrue(tags.contains(ColumnTag.PII_EMAIL));
        assertTrue(svc.isSensitive(ColumnTag.PII_EMAIL));
    }

    @Test
    void classifyPasswordColumn() {
        var tags = svc.classifyColumns("users", List.of("password_hash"));
        assertTrue(tags.contains(ColumnTag.CREDENTIAL));
    }

    @Test
    void classifySsnColumn() {
        var tags = svc.classifyColumns("employees", List.of("ssn"));
        assertTrue(tags.contains(ColumnTag.PII_SSN));
    }

    @Test
    void classifyNameColumn() {
        var tags = svc.classifyColumns("users", List.of("first_name"));
        assertTrue(tags.contains(ColumnTag.PII_NAME));
    }

    @Test
    void classifyAddressColumn() {
        var tags = svc.classifyColumns("addresses", List.of("street_address"));
        assertTrue(tags.contains(ColumnTag.PII_ADDRESS));
    }

    @Test
    void classifyDobColumn() {
        var tags = svc.classifyColumns("users", List.of("date_of_birth"));
        assertTrue(tags.contains(ColumnTag.PII_DOB));
    }

    @Test
    void classifyPhoneColumn() {
        var tags = svc.classifyColumns("contacts", List.of("phone_number"));
        assertTrue(tags.contains(ColumnTag.PII_PHONE));
    }

    @Test
    void classifyFinancialColumn() {
        var tags = svc.classifyColumns("accounts", List.of("account_number"));
        assertTrue(tags.contains(ColumnTag.FINANCIAL));
    }

    @Test
    void classifyHealthColumn() {
        var tags = svc.classifyColumns("patients", List.of("diagnosis_code"));
        assertTrue(tags.contains(ColumnTag.HEALTH));
    }

    @Test
    void classifyBulkColumns() {
        var columns = List.of("id", "email", "password_hash", "first_name", "last_name",
                "phone", "ssn", "address", "dob", "salary", "diagnosis");
        var tags = svc.classifyColumns("users", columns);
        assertTrue(tags.contains(ColumnTag.PII_EMAIL));
        assertTrue(tags.contains(ColumnTag.CREDENTIAL));
        assertTrue(tags.contains(ColumnTag.PII_NAME));
        assertTrue(tags.contains(ColumnTag.PII_PHONE));
        assertTrue(tags.contains(ColumnTag.PII_SSN));
        assertTrue(tags.contains(ColumnTag.PII_ADDRESS));
        assertTrue(tags.contains(ColumnTag.PII_DOB));
        assertTrue(tags.contains(ColumnTag.FINANCIAL));
        assertTrue(tags.contains(ColumnTag.HEALTH));
    }

    @Test
    void classifyTableRestricted() {
        var tags = List.of(ColumnTag.PII_SSN, ColumnTag.PII_NAME);
        assertEquals(DataClassification.RESTRICTED, svc.classifyTable(tags));
    }

    @Test
    void classifyTableConfidential() {
        var tags = List.of(ColumnTag.PII_EMAIL, ColumnTag.PII_NAME);
        assertEquals(DataClassification.CONFIDENTIAL, svc.classifyTable(tags));
    }

    @Test
    void classifyTableInternal() {
        var tags = List.of(ColumnTag.INTERNAL_ONLY);
        assertEquals(DataClassification.INTERNAL, svc.classifyTable(tags));
    }

    @Test
    void classifyTablePublic() {
        assertEquals(DataClassification.INTERNAL, svc.classifyTable(List.of()));
    }

    @Test
    void schemaVersionTracksChanges() {
        svc.recordSchemaChange("conn-1", "public", "users",
                "{\"columns\":[{\"name\":\"id\",\"type\":\"integer\"}]}",
                "Initial schema", "CREATE TABLE users (id INTEGER)");
        svc.recordSchemaChange("conn-1", "public", "users",
                "{\"columns\":[{\"name\":\"id\",\"type\":\"integer\"},{\"name\":\"email\",\"type\":\"varchar\"}]}",
                "Added email column", "ALTER TABLE users ADD COLUMN email VARCHAR(255)");

        var history = svc.schemaHistory("conn-1", "users");
        assertEquals(2, history.size());
        assertEquals(1, history.get(0).version());
        assertEquals(2, history.get(1).version());
        assertEquals("Initial schema", history.get(0).changeSummary());
    }

    @Test
    void dataLineageTracksPipeline() {
        svc.recordLineage("pipeline-1", "src-1", "public", "users",
                "id,name,email", "dest-1", "public", "users_copy",
                "id,full_name,email", "rename name→full_name", 1000);
        svc.recordLineage("pipeline-1", "src-1", "public", "orders",
                "id,user_id,total", "dest-1", "public", "orders_copy",
                "id,user_id,total", "direct", 500);

        var lineage = svc.lineage("pipeline-1");
        assertEquals(2, lineage.size());
        assertTrue(lineage.stream().anyMatch(l -> l.sourceTable().equals("users")));
    }

    @Test
    void retentionPolicyDefaults() {
        assertEquals(365, RetentionPolicy.auditLogs().retentionDuration().toDays());
        assertEquals(180, RetentionPolicy.pipelineHistory().retentionDuration().toDays());
        assertEquals(90, RetentionPolicy.deadLetterEvents().retentionDuration().toDays());
        assertEquals(365, RetentionPolicy.piiData().retentionDuration().toDays());
    }
}
