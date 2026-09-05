package com.syncflow.core.snapshot.pipeline;

import com.syncflow.core.pipeline.DestinationReference;
import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.core.pipeline.PipelineName;
import com.syncflow.core.pipeline.PipelineSettings;
import com.syncflow.core.pipeline.SourceReference;
import com.syncflow.core.pipeline.filter.FilterCondition;
import com.syncflow.core.pipeline.filter.FilterGroup;
import com.syncflow.core.pipeline.filter.FilterOperator;
import com.syncflow.core.pipeline.mapping.ColumnMapping;
import com.syncflow.core.pipeline.mapping.PrimaryKeyMapping;
import com.syncflow.core.pipeline.mapping.TableMapping;
import com.syncflow.core.pipeline.transform.SqlExpressionEvaluator;
import com.syncflow.core.pipeline.transform.TransformationRule;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessingPipelineTest {

    private ProcessingContext ctx(TableMapping mapping) {
        var src = new SourceReference("c1", "s", "t");
        var dst = new DestinationReference("c2", "s", "t_dest", null);
        var pipeline = PipelineDesign.create(new PipelineName("test"), src, dst,
                List.of(mapping), PipelineSettings.defaults());
        return new ProcessingContext(pipeline, mapping);
    }

    @Test
    void filterProcessorDropsNonMatching() {
        var filter = FilterGroup.all(List.of(FilterCondition.equals("status", "active")));
        var mapping = new TableMapping("t", "t_dest", null, null, List.of(), List.of(), List.of(), filter);
        var proc = new FilterProcessor();
        assertNull(proc.process(Map.of("status", "inactive", "name", "x"), ctx(mapping)));
    }

    @Test
    void filterProcessorPassesMatching() {
        var filter = FilterGroup.all(List.of(FilterCondition.equals("status", "active")));
        var mapping = new TableMapping("t", "t_dest", null, null, List.of(), List.of(), List.of(), filter);
        var result = new FilterProcessor().process(Map.of("status", "active", "name", "x"), ctx(mapping));
        assertNotNull(result);
        assertEquals("active", result.get("status"));
    }

    @Test
    void filterProcessorIsNull() {
        var condition = new FilterCondition("deleted_at", FilterOperator.IS_NULL, List.of());
        var filter = FilterGroup.all(List.of(condition));
        var mapping = new TableMapping("t", "t_dest", null, null, List.of(), List.of(), List.of(), filter);

        var proc = new FilterProcessor();
        var input = new HashMap<String, Object>();
        input.put("deleted_at", null);
        input.put("name", "x");
        var passed = proc.process(input, ctx(mapping));
        assertNotNull(passed);

        var dropped = proc.process(Map.of("deleted_at", "2024-01-01", "name", "y"), ctx(mapping));
        assertNull(dropped);
    }

    @Test
    void filterGroupNestedAnd() {
        var c1 = FilterCondition.equals("type", "user");
        var c2 = FilterCondition.isNull("deleted_at");
        var nested = FilterGroup.all(List.of(c2));
        var filter = new FilterGroup(FilterGroup.LogicalOperator.AND, List.of(c1), List.of(nested));
        var mapping = new TableMapping("t", "t_dest", null, null, List.of(), List.of(), List.of(), filter);
        var proc = new FilterProcessor();

        var record = new HashMap<String, Object>();
        record.put("type", "user");
        record.put("deleted_at", null);
        assertNotNull(proc.process(record, ctx(mapping)));

        var record2 = new HashMap<String, Object>();
        record2.put("type", "admin");
        record2.put("deleted_at", null);
        assertNull(proc.process(record2, ctx(mapping)));

        var record3 = new HashMap<String, Object>();
        record3.put("type", "user");
        record3.put("deleted_at", "2024-01-01");
        assertNull(proc.process(record3, ctx(mapping)));
    }

    @Test
    void transformProcessorDefaultValue() {
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        var cm = new ColumnMapping("nickname", "nickname", List.of(TransformationRule.defaultValue("N/A")));
        var mapping = new TableMapping("t", "t_dest", null, pk, List.of(cm), List.of(), List.of(), null);
        var transform = new TransformProcessor();
        var ctx = ctx(mapping);

        var withValue = new HashMap<String, Object>();
        withValue.put("id", 1);
        withValue.put("nickname", "Johnny");
        assertEquals("Johnny", transform.process(withValue, ctx).get("nickname"));

        var withNull = new HashMap<String, Object>();
        withNull.put("id", 2);
        withNull.put("nickname", null);
        assertEquals("N/A", transform.process(withNull, ctx).get("nickname"));
    }

    @Test
    void transformProcessorUppercase() {
        var cm = new ColumnMapping("name", "name_upper", List.of(TransformationRule.uppercase()));
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        var mapping = new TableMapping("t", "t_dest", null, pk, List.of(cm), List.of(), List.of(), null);
        var record = new HashMap<String, Object>();
        record.put("id", 1);
        record.put("name", "john");

        var result = new TransformProcessor().process(record, ctx(mapping));
        assertEquals("JOHN", result.get("name_upper"));
    }

    @Test
    void channelComposition() {
        var filter = FilterGroup.all(List.of(FilterCondition.equals("active", "true")));
        var cm = new ColumnMapping("name", "name_upper", List.of(TransformationRule.uppercase()));
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        var mapping = new TableMapping("t", "t_dest", null, pk, List.of(cm), List.of(), List.of(), filter);
        var ctx = ctx(mapping);
        var chain = new FilterProcessor().andThen(new TransformProcessor());

        var record1 = new HashMap<String, Object>();
        record1.put("id", 1);
        record1.put("active", "true");
        record1.put("name", "john");
        var passed = chain.process(record1, ctx);
        assertNotNull(passed);
        assertEquals("JOHN", passed.get("name_upper"));

        var record2 = new HashMap<String, Object>();
        record2.put("id", 2);
        record2.put("active", "false");
        record2.put("name", "jane");
        assertNull(chain.process(record2, ctx));
    }

    private TableMapping makeTableMapping() {
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        var cm1 = new ColumnMapping("id", "id", List.of());
        var cm2 = new ColumnMapping("first_name", "firstName", List.of(TransformationRule.rename("firstName")));
        var cm3 = new ColumnMapping("last_name", "lastName",
                List.of(TransformationRule.rename("lastName"), TransformationRule.uppercase()));
        var cm4 = new ColumnMapping("email", "email", List.of(TransformationRule.lowercase()));
        var cm5 = new ColumnMapping("deleted", "deleted", List.of(TransformationRule.ignore()));
        var cm6 = new ColumnMapping("full_name", "fullName",
                List.of(TransformationRule.concat(List.of("first_name", "last_name"), " ")));
        return new TableMapping("users", "users_dest", null, pk,
                List.of(cm1, cm2, cm3, cm4, cm5, cm6), List.of(), List.of(), null);
    }

    @Test
    void transformProcessorAppliesMappings() {
        var mapping = makeTableMapping();
        var transform = new TransformProcessor();
        var record = new LinkedHashMap<String, Object>();
        record.put("id", 1);
        record.put("first_name", "John");
        record.put("last_name", "doe");
        record.put("email", "John@Example.COM");
        record.put("deleted", null);
        record.put("full_name", "");

        var result = transform.process(record, ctx(mapping));
        assertNotNull(result);
        assertEquals(1, result.get("id"));
        assertEquals("John", result.get("firstName"));
        assertEquals("DOE", result.get("lastName"));
        assertEquals("john@example.com", result.get("email"));
        assertNull(result.get("deleted"));
        assertEquals("John doe", result.get("fullName"));
    }

    // -------------------------------------------------------------------------
    // EXPRESSION (SpEL) tests
    // -------------------------------------------------------------------------

    @Test
    void expressionUpperCaseViaSpEL() {
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        var cm = new ColumnMapping("name", "name_upper",
                List.of(TransformationRule.expression("#value?.toString()?.toUpperCase()")));
        var mapping = new TableMapping("t", "t_dest", null, pk, List.of(cm), List.of(), List.of(), null);

        var record = new HashMap<String, Object>();
        record.put("id", 1);
        record.put("name", "alice");

        var result = new TransformProcessor().process(record, ctx(mapping));
        assertEquals("ALICE", result.get("name_upper"));
    }

    @Test
    void expressionConcatenateRowFields() {
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        var cm = new ColumnMapping("full_name", "full_name",
                List.of(TransformationRule.expression("#row['first_name'] + ' ' + #row['last_name']")));
        var mapping = new TableMapping("t", "t_dest", null, pk, List.of(cm), List.of(), List.of(), null);

        var record = new HashMap<String, Object>();
        record.put("id", 1);
        record.put("first_name", "Jane");
        record.put("last_name", "Smith");
        record.put("full_name", "");

        var result = new TransformProcessor().process(record, ctx(mapping));
        assertEquals("Jane Smith", result.get("full_name"));
    }

    @Test
    void expressionConditionalDefault() {
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        var cm = new ColumnMapping("nickname", "nickname",
                List.of(TransformationRule.expression("#value != null ? #value : 'anonymous'")));
        var mapping = new TableMapping("t", "t_dest", null, pk, List.of(cm), List.of(), List.of(), null);
        var transform = new TransformProcessor();
        var ctx = ctx(mapping);

        // non-null value: pass through unchanged
        var withValue = new HashMap<String, Object>();
        withValue.put("id", 1);
        withValue.put("nickname", "Sparky");
        assertEquals("Sparky", transform.process(withValue, ctx).get("nickname"));

        // null value: conditional yields the fallback literal
        var withNull = new HashMap<String, Object>();
        withNull.put("id", 2);
        withNull.put("nickname", null);
        assertEquals("anonymous", transform.process(withNull, ctx).get("nickname"));
    }

    @Test
    void expressionArithmetic() {
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        var cm = new ColumnMapping("price", "price_with_tax",
                List.of(TransformationRule.expression("#value * 1.1")));
        var mapping = new TableMapping("t", "t_dest", null, pk, List.of(cm), List.of(), List.of(), null);

        var record = new HashMap<String, Object>();
        record.put("id", 1);
        record.put("price", 100.0);

        var result = new TransformProcessor().process(record, ctx(mapping));
        assertEquals(110.0, (Double) result.get("price_with_tax"), 0.001);
    }

    @Test
    void expressionSubstring() {
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        var cm = new ColumnMapping("code", "prefix",
                List.of(TransformationRule.expression("#value?.toString()?.substring(0, 3)")));
        var mapping = new TableMapping("t", "t_dest", null, pk, List.of(cm), List.of(), List.of(), null);

        var record = new HashMap<String, Object>();
        record.put("id", 1);
        record.put("code", "ABCDEF");

        var result = new TransformProcessor().process(record, ctx(mapping));
        assertEquals("ABC", result.get("prefix"));
    }

    @Test
    void expressionNullValueSafeNavigation() {
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        // ?. safe-navigation: each link in the chain uses ?. so null short-circuits
        var cm = new ColumnMapping("name", "name_upper",
                List.of(TransformationRule.expression("#value?.toString()?.toUpperCase()")));
        var mapping = new TableMapping("t", "t_dest", null, pk, List.of(cm), List.of(), List.of(), null);

        var record = new HashMap<String, Object>();
        record.put("id", 1);
        record.put("name", null);

        var result = new TransformProcessor().process(record, ctx(mapping));
        assertNull(result.get("name_upper"));
    }

    @Test
    void expressionChainedWithOtherRules() {
        // EXPRESSION followed by UPPERCASE to demonstrate rule chaining still works
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        var cm = new ColumnMapping("status", "status_label", List.of(
                // first: map code to label
                TransformationRule.expression(
                        "#value == 'A' ? 'active' : (#value == 'I' ? 'inactive' : 'unknown')"),
                // then: uppercase the result
                TransformationRule.uppercase()));
        var mapping = new TableMapping("t", "t_dest", null, pk, List.of(cm), List.of(), List.of(), null);
        var transform = new TransformProcessor();
        var ctx = ctx(mapping);

        var r1 = new HashMap<String, Object>();
        r1.put("id", 1);
        r1.put("status", "A");
        assertEquals("ACTIVE", transform.process(r1, ctx).get("status_label"));

        var r2 = new HashMap<String, Object>();
        r2.put("id", 2);
        r2.put("status", "I");
        assertEquals("INACTIVE", transform.process(r2, ctx).get("status_label"));

        var r3 = new HashMap<String, Object>();
        r3.put("id", 3);
        r3.put("status", "X");
        assertEquals("UNKNOWN", transform.process(r3, ctx).get("status_label"));
    }

    @Test
    void expressionInvalidSyntaxThrows() {
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        var cm = new ColumnMapping("name", "name",
                List.of(TransformationRule.expression("this is not valid spel {{{")));
        var mapping = new TableMapping("t", "t_dest", null, pk, List.of(cm), List.of(), List.of(), null);

        var record = Map.<String, Object>of("id", 1, "name", "test");
        assertThrows(SqlExpressionEvaluator.ExpressionEvaluationException.class,
                () -> new TransformProcessor().process(record, ctx(mapping)));
    }
}
