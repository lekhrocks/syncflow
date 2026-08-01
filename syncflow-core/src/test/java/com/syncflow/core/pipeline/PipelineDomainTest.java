package com.syncflow.core.pipeline;

import com.syncflow.core.pipeline.filter.FilterCondition;
import com.syncflow.core.pipeline.filter.FilterGroup;
import com.syncflow.core.pipeline.mapping.ColumnMapping;
import com.syncflow.core.pipeline.mapping.TableMapping;
import com.syncflow.core.pipeline.transform.TransformationRule;
import com.syncflow.core.pipeline.validation.ValidationIssue;
import com.syncflow.core.pipeline.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineDomainTest {

    @Test
    void createPipelineDesign() {
        var name = new PipelineName("test-pipeline");
        var source = new SourceReference("conn-1", "public", "users");
        var dest = new DestinationReference("conn-2", "public", "users_copy", "UPSERT");
        var mapping = new TableMapping("users", "users_copy", null, null, List.of(), List.of(), List.of(), null);
        var settings = PipelineSettings.defaults();
        var pipeline = PipelineDesign.create(name, source, dest, List.of(mapping), settings);

        assertNotNull(pipeline.id());
        assertEquals("test-pipeline", pipeline.name().value());
        assertEquals(PipelineStatus.DRAFT, pipeline.status());
        assertEquals("conn-1", pipeline.source().connectionId());
        assertEquals(1, pipeline.audit().version());
    }

    @Test
    void pipelineNameRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new PipelineName(""));
        assertThrows(IllegalArgumentException.class, () -> new PipelineName(null));
    }

    @Test
    void pipelineNameRejectsTooLong() {
        assertThrows(IllegalArgumentException.class, () -> new PipelineName("x".repeat(256)));
    }

    @Test
    void pipelineIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> PipelineId.from(""));
        assertThrows(IllegalArgumentException.class, () -> PipelineId.from(null));
    }

    @Test
    void pipelineStatusTransitions() {
        var name = new PipelineName("p");
        var s = new SourceReference("c1", "public", "t1");
        var d = new DestinationReference("c2", "public", "t2", null);
        var pipeline = PipelineDesign.create(name, s, d, List.of(), PipelineSettings.defaults());

        var validated = pipeline.withStatus(PipelineStatus.VALIDATED);
        assertEquals(PipelineStatus.VALIDATED, validated.status());
        assertEquals(pipeline.id(), validated.id());

        var activated = pipeline.withStatus(PipelineStatus.ACTIVATED);
        assertEquals(PipelineStatus.ACTIVATED, activated.status());
    }

    @Test
    void pipelineSettingsDefaults() {
        var s = PipelineSettings.defaults();
        assertEquals(SyncMode.FULL_SNAPSHOT, s.syncMode());
        assertEquals(1000, s.batchSize());
        assertEquals(3, s.maxRetries());
    }

    @Test
    void pipelineSettingsEnforcesPositiveBatch() {
        var s = new PipelineSettings(SyncMode.FULL_SNAPSHOT, 0, 3, false, false, Map.of());
        assertTrue(s.batchSize() > 0);
    }

    @Test
    void columnMappingRoundTrip() {
        var tr = TransformationRule.rename("new_name");
        var cm = new ColumnMapping("old_name", "new_name", List.of(tr));
        assertEquals("old_name", cm.sourceColumn());
        assertEquals("new_name", cm.destinationColumn());
        assertEquals(1, cm.transformations().size());
    }

    @Test
    void filterGroupAndOrNesting() {
        var c1 = FilterCondition.equals("status", "active");
        var c2 = FilterCondition.isNull("deleted_at");
        var inner = FilterGroup.all(List.of(c2));
        var outer = new FilterGroup(FilterGroup.LogicalOperator.AND, List.of(c1), List.of(inner));
        assertEquals(1, outer.conditions().size());
        assertEquals(1, outer.nestedGroups().size());
    }

    @Test
    void validationResultSuccess() {
        var r = ValidationResult.success();
        assertTrue(r.valid());
        assertTrue(r.issues().isEmpty());
    }

    @Test
    void validationResultFailure() {
        var issue = new ValidationIssue("TEST_ERR", "field", "Error", ValidationIssue.Severity.ERROR);
        var r = ValidationResult.failure(List.of(issue));
        assertFalse(r.valid());
        assertEquals(1, r.issues().size());
        assertEquals("TEST_ERR", r.issues().getFirst().code());
    }

    @Test
    void destinationWriteModes() {
        assertEquals("UPSERT", DestinationReference.UPSERT);
        assertEquals("INSERT", DestinationReference.INSERT);
        assertEquals("MERGE", DestinationReference.MERGE);
        var d = new DestinationReference("c", "s", "t", null);
        assertEquals("UPSERT", d.writeMode());
    }

    @Test
    void transformationRuleStaticFactories() {
        var r = TransformationRule.rename("x");
        assertNotNull(r);
        assertNotNull(TransformationRule.ignore());
        assertNotNull(TransformationRule.constant("42"));
        assertNotNull(TransformationRule.uppercase());
        assertNotNull(TransformationRule.lowercase());
        assertNotNull(TransformationRule.trim());
        assertNotNull(TransformationRule.defaultValue("N/A"));
        assertNotNull(TransformationRule.expression("first_name + ' ' + last_name"));
    }

    @Test
    void emptyColumnMappingTransformations() {
        var cm = new ColumnMapping("src", "dest", null);
        assertTrue(cm.transformations().isEmpty());
    }
}
