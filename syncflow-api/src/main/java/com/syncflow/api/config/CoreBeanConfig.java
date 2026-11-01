package com.syncflow.api.config;

import com.syncflow.core.pipeline.transform.ExpressionEvaluator;
import com.syncflow.core.registry.SpringConnectorRegistry;
import com.syncflow.core.spi.Connector;
import com.syncflow.expression.SpelExpressionEvaluator;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the core types that {@code syncflow-core} deliberately no longer
 * annotates as Spring beans.
 *
 * <p>
 * {@code syncflow-core} is framework-free (enforced by
 * {@code ArchitectureTest#coreShouldNotDependOnSpring}), so components that
 * used to self-register via {@code @Component}/{@code @Service} are wired here
 * instead — at the composition root, which is where a hexagonal architecture
 * wants that decision to live.
 */
@Configuration
public class CoreBeanConfig {

    /**
     * The built-in {@link Connector} registry. Previously a {@code @Component}
     * in core ({@code SpringConnectorRegistry}); it is instantiated here from
     * every discovered {@link Connector} bean. The app-facing registry remains
     * {@code DelegatingConnectorRegistry}, which layers plugin connectors on top.
     */
    @Bean
    SpringConnectorRegistry springConnectorRegistry(List<Connector> connectors) {
        return new SpringConnectorRegistry(connectors);
    }

    /**
     * The expression engine backing {@code TransformType#EXPRESSION} rules.
     *
     * <p>
     * Bound to the port ({@link ExpressionEvaluator}) rather than the SpEL
     * implementation, so swapping expression languages is a one-line change
     * with no edits to {@code syncflow-core}.
     */
    @Bean
    ExpressionEvaluator expressionEvaluator() {
        return new SpelExpressionEvaluator();
    }
}
