package dev.zoel.keystone.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * These tests make the hexagonal architecture EXECUTABLE.
 *
 * If anyone (you, six months from now) imports a JPA entity from the domain, the
 * build turns red. That is the difference between "we documented the architecture"
 * and "the architecture is enforced".
 */
@AnalyzeClasses(
    packages = "dev.zoel.keystone",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule layers_are_respected = Architectures.layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("Domain").definedBy("dev.zoel.keystone.domain..")
        .layer("Application").definedBy("dev.zoel.keystone.application..")
        .layer("Infrastructure").definedBy("dev.zoel.keystone.infrastructure..")
        .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
        .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");

    @ArchTest
    static final ArchRule domain_knows_nothing_about_spring = noClasses()
        .that().resideInAPackage("dev.zoel.keystone.domain..")
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
        .because("the domain must compile and be testable without the framework");

    @ArchTest
    static final ArchRule domain_knows_nothing_about_jpa = noClasses()
        .that().resideInAPackage("dev.zoel.keystone.domain..")
        .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "jakarta.validation..")
        .because("persistence is a detail, not a business rule");

    @ArchTest
    static final ArchRule application_knows_nothing_about_spring = noClasses()
        .that().resideInAPackage("dev.zoel.keystone.application..")
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
        .because("use cases are wired in UseCaseConfiguration, never annotated");

    @ArchTest
    static final ArchRule controllers_never_touch_persistence_directly = noClasses()
        .that().resideInAnyPackage("dev.zoel.keystone.infrastructure.rest..",
                                   "dev.zoel.keystone.infrastructure.web..",
                                   "dev.zoel.keystone.infrastructure.security..")
        .should().dependOnClassesThat().resideInAPackage("dev.zoel.keystone.infrastructure.persistence..")
        .because("a controller talks to ports, not to JPA entities");

    @ArchTest
    static final ArchRule rest_and_web_adapters_do_not_call_each_other = noClasses()
        .that().resideInAPackage("dev.zoel.keystone.infrastructure.web..")
        .should().dependOnClassesThat().resideInAPackage("dev.zoel.keystone.infrastructure.rest..")
        .because("they are sibling adapters over the same port; neither depends on the other");
}
