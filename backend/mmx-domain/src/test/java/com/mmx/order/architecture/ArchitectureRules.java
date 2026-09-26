package com.mmx.order.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

public final class ArchitectureRules {

    private static final String GENERATED_REST = "..adapter.in.rest.generated..";

    private ArchitectureRules() {}

    private static DescribedPredicate<JavaClass> annotatedWith(String annotationName) {
        return new DescribedPredicate<>("annotated with " + annotationName) {
            @Override
            public boolean test(JavaClass javaClass) {
                return javaClass.isAnnotatedWith(annotationName);
            }
        };
    }

    public static ArchRule domainFrameworkIsolation() {
        return noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "com.fasterxml.jackson..",
                        "com.mmx.order.application..",
                        "com.mmx.order.adapter..");
    }

    public static ArchRule applicationFrameworkIsolation() {
        return noClasses()
                .that()
                .resideInAPackage("..application..")
                .and()
                .resideOutsideOfPackage(GENERATED_REST)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "com.mmx.order.adapter..");
    }

    public static ArchRule adapterOutMustNotDependOnAdapterIn() {
        return noClasses()
                .that()
                .resideInAPackage("..adapter.out..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..adapter.in..");
    }

    public static ArchRule entitiesMustResideInPersistencePackage() {
        return classes()
                .that(annotatedWith("jakarta.persistence.Entity"))
                .should()
                .resideInAPackage("..adapter.out..entity..");
    }

    public static ArchRule restControllersMustNotDependOnApplicationServices() {
        return noClasses()
                .that(annotatedWith("org.springframework.web.bind.annotation.RestController"))
                .and()
                .resideInAPackage("..adapter.in.rest..")
                .and()
                .resideOutsideOfPackage(GENERATED_REST)
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..application.service..");
    }

    public static ArchRule restControllerAdviceMustNotDependOnApplicationServices() {
        return noClasses()
                .that(annotatedWith("org.springframework.web.bind.annotation.RestControllerAdvice"))
                .and()
                .resideInAPackage("..adapter.in.rest..")
                .and()
                .resideOutsideOfPackage(GENERATED_REST)
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..application.service..");
    }

    public static ArchRule layeredArchitectureRules() {
        return layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain")
                .definedBy("com.mmx.order.domain..")
                .layer("Application")
                .definedBy("com.mmx.order.application..")
                .layer("AdapterIn")
                .definedBy("com.mmx.order.adapter.in..")
                .layer("AdapterOut")
                .definedBy("com.mmx.order.adapter.out..")
                .layer("Bootstrap")
                .definedBy("com.mmx.order.config..")
                .whereLayer("Domain")
                .mayOnlyBeAccessedByLayers("Application", "AdapterIn", "AdapterOut", "Bootstrap")
                .whereLayer("Application")
                .mayOnlyBeAccessedByLayers("AdapterIn", "AdapterOut", "Bootstrap")
                .whereLayer("AdapterIn")
                .mayOnlyBeAccessedByLayers("Bootstrap")
                .whereLayer("AdapterOut")
                .mayOnlyBeAccessedByLayers("Bootstrap")
                .whereLayer("AdapterIn")
                .mayOnlyAccessLayers("Application", "Domain", "AdapterIn")
                .whereLayer("AdapterOut")
                .mayOnlyAccessLayers("Application", "Domain", "AdapterOut")
                .whereLayer("Application")
                .mayOnlyAccessLayers("Application", "Domain")
                .whereLayer("Bootstrap")
                .mayOnlyAccessLayers("Domain", "Application", "AdapterIn", "AdapterOut", "Bootstrap");
    }
}
