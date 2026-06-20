package com.mmx.order.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("architecture")
class HexagonalArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.mmx.order");
    }

    @Test
    void domain_is_framework_isolated() {
        ArchitectureRules.domainFrameworkIsolation().check(classes);
    }

    @Test
    void application_is_framework_isolated() {
        ArchitectureRules.applicationFrameworkIsolation().check(classes);
    }

    @Test
    void adapter_out_does_not_depend_on_adapter_in() {
        ArchitectureRules.adapterOutMustNotDependOnAdapterIn().check(classes);
    }

    @Test
    void entities_reside_in_persistence_package() {
        ArchitectureRules.entitiesMustResideInPersistencePackage().check(classes);
    }

    @Test
    void rest_controllers_use_ports_not_services() {
        ArchitectureRules.restControllersMustNotDependOnApplicationServices().check(classes);
    }

    @Test
    void rest_controller_advice_does_not_depend_on_services() {
        ArchitectureRules.restControllerAdviceMustNotDependOnApplicationServices().check(classes);
    }

    @Test
    void layered_architecture_is_respected() {
        ArchitectureRules.layeredArchitectureRules().check(classes);
    }
}
