package com.syncflow.api;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("arch")
class ArchitectureTest {

    @Test
    void coreShouldNotDependOnApi() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.syncflow");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..core..")
                .should().dependOnClassesThat().resideInAPackage("..api..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
