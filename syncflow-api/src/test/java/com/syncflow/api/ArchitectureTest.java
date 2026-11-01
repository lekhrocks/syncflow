package com.syncflow.api;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Enforces SyncFlow's hexagonal (ports &amp; adapters) boundaries.
 *
 * <p>
 * The layout this guards:
 *
 * <pre>
 *   common        shared primitives only (tenant IDs, exceptions, correlation)
 *     ^
 *   core          domain model + SPI ports. Must stay framework-free.
 *     ^
 *   persistence / connectors / runtime / plugin-api   adapters (implement core ports)
 *     ^
 *   api           driving adapters (REST) + composition root
 * </pre>
 *
 * <p>
 * Arrows point at dependencies: each layer may only depend on layers below it.
 * The {@code core} module is the stable center — nothing below it may reference
 * anything above it, and it must not couple to Spring, JPA, or a database
 * driver.
 *
 * <p>
 * Rules grouped as "burn-down" are currently violated by legacy code. They are
 * {@link Disabled @Disabled} with a TODO pointing at the work item that
 * resolves
 * them, so they stay visible without failing the build. Re-enable each one as
 * its
 * work item lands; do not add new violations to those packages.
 *
 * <p>
 * Note: {@code syncflow-agent} is not on this module's classpath, so its
 * boundaries cannot be asserted from here. Keep it dependency-inward by review.
 */
@Tag("arch")
class ArchitectureTest {

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.syncflow");
    }

    // ──────────────────────────────────────────────────────────────────
    // Enforced now — these boundaries hold today. Do not break them.
    // ──────────────────────────────────────────────────────────────────

    @Test
    void coreShouldNotDependOnApiRuntimePersistenceOrConnectors() {
        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.syncflow.core..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.syncflow.api..",
                        "com.syncflow.runtime..",
                        "com.syncflow.persistence..",
                        "com.syncflow.connector..")
                .because("core is the hexagonal domain layer and must not depend on any adapter")
                .check(productionClasses());
    }

    @Test
    void connectorsShouldNotDependOnApiOrPersistence() {
        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.syncflow.connector..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.syncflow.api..", "com.syncflow.persistence..")
                .because("connectors are adapters implementing core ports; siblings are invisible to them")
                .check(productionClasses());
    }

    @Test
    void persistenceShouldNotDependOnApiOrConnectors() {
        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.syncflow.persistence..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.syncflow.api..", "com.syncflow.connector..")
                .because("persistence is a lower adapter and must not reach up into the driving layer")
                .check(productionClasses());
    }

    @Test
    void runtimeShouldNotDependOnApiOrPersistence() {
        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.syncflow.runtime..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.syncflow.api..", "com.syncflow.persistence..")
                .because("runtime is a lower adapter and must not reach up into the driving layer")
                .check(productionClasses());
    }

    @Test
    void securityModuleShouldNotDependOnUpperLayers() {
        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.syncflow.security..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.syncflow.api..",
                        "com.syncflow.core..",
                        "com.syncflow.persistence..",
                        "com.syncflow.connector..")
                .because("security config is a leaf module and must stay decoupled from the domain")
                .check(productionClasses());
    }

    @Test
    void pluginApiShouldNotDependOnOtherModules() {
        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.syncflow.plugin..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.syncflow.api..",
                        "com.syncflow.core..",
                        "com.syncflow.persistence..",
                        "com.syncflow.connector..",
                        "com.syncflow.runtime..",
                        "com.syncflow.security..",
                        "com.syncflow.common..")
                .because("plugin-api is the external contract; it must be dependency-free for third-party authors")
                .check(productionClasses());
    }

    @Test
    void commonShouldNotDependOnUpperLayers() {
        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.syncflow.common..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.syncflow.api..",
                        "com.syncflow.core..",
                        "com.syncflow.persistence..",
                        "com.syncflow.connector..",
                        "com.syncflow.runtime..")
                .because("common is the base of the dependency graph and must not reference anything above it")
                .check(productionClasses());
    }

    @Test
    void apiShouldNotDependOnJpaTypes() {
        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.syncflow.api..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.data.jpa..", "jakarta.persistence..")
                .because("the driving layer talks to ports, not to the ORM; JPA belongs in persistence")
                .check(productionClasses());
    }

    // ──────────────────────────────────────────────────────────────────
    // Burn-down — currently violated. Re-enable as each work item lands.
    // ──────────────────────────────────────────────────────────────────

    /**
     * {@code syncflow-core} is the hexagonal domain layer and must carry no
     * framework dependency.
     *
     * <p>
     * This was the original burn-down rule (work item 3), now enforced. The
     * four violations it tracked have been resolved:
     * <ul>
     * <li>{@code SqlExpressionEvaluator} — replaced by the
     * {@code ExpressionEvaluator} port; the SpEL implementation moved to the
     * {@code syncflow-expression} module ({@code SpelExpressionEvaluator}).</li>
     * <li>{@code SpringConnectorRegistry} ({@code @Component}) — de-annotated;
     * registered via {@code @Bean} in {@code api}'s {@code CoreBeanConfig}.</li>
     * <li>{@code InMemoryPipelineRepository} ({@code @Repository}) —
     * de-annotated; production uses the JPA adapter, this is test-only.</li>
     * <li>{@code PipelineService} ({@code @Service}) — de-annotated; it is only
     * ever constructed directly.</li>
     * </ul>
     */
    @Test
    void coreShouldNotDependOnSpring() {
        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.syncflow.core..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..")
                .because("core is the hexagonal domain layer and must stay framework-free")
                .check(productionClasses());
    }

    /**
     * TODO(work-item-4): persistence must own entities behind ports. Currently
     * 19 api classes import {@code com.syncflow.persistence..entity..} directly,
     * most notably {@code SyncOrchestrator}, which uses {@code SyncJobEntity}
     * end to end. Ports return domain types; adapters map at the boundary.
     */
    @Test
    @Disabled("TODO(work-item-4): api must not see persistence entities — 19 classes still do")
    void apiShouldNotDependOnPersistenceEntities() {
        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.syncflow.api..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.syncflow.persistence..entity..")
                .check(productionClasses());
    }

    /**
     * TODO(work-item-4): api should call application services / ports, not Spring
     * Data repositories. Currently 20 api classes import
     * {@code com.syncflow.persistence..repository..}. Contrast with
     * {@code PipelineRepositoryAdapter}, which is the correct pattern: implement
     * the core port and map at the boundary. That adapter should also move into
     * the persistence module.
     */
    @Test
    @Disabled("TODO(work-item-4): api must not call persistence repositories — 20 classes still do")
    void apiShouldNotDependOnPersistenceRepositories() {
        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.syncflow.api..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.syncflow.persistence..repository..")
                .check(productionClasses());
    }
}
