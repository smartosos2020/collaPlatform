package com.colla.platform.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.colla.platform")
class ModuleArchitectureTests {

    @ArchTest
    static final ArchRule PUBLIC_CONTRACTS_MUST_NOT_DEPEND_ON_PROVIDER_PRIVATE_LAYERS =
        noClasses()
            .that().resideInAPackage("..modules.*.contract..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..modules.*.api..",
                "..modules.*.application..",
                "..modules.*.domain..",
                "..modules.*.infrastructure.."
            )
            .because("public contracts must remain independent of provider-private layers");

    @ArchTest
    static final ArchRule SHARED_KERNEL_MUST_NOT_DEPEND_ON_BUSINESS_MODULES =
        noClasses()
            .that().resideInAPackage("com.colla.platform.shared..")
            .should().dependOnClassesThat().resideInAPackage("com.colla.platform.modules..")
            .because("shared infrastructure exposes inbound ports and must not select a business-module provider");
}
