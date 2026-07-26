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

    @ArchTest
    static final ArchRule EVENT_MODULE_MUST_NOT_IMPORT_REDIS_TRANSPORT =
        noClasses()
            .that().resideInAPackage("com.colla.platform.modules.event..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.data.redis..")
            .because("event workers publish through the shared realtime port, not a Redis implementation");

    @ArchTest
    static final ArchRule BUSINESS_MODULES_MUST_NOT_SEND_TO_LOCAL_WEBSOCKET_SESSIONS =
        noClasses()
            .that().resideInAPackage("com.colla.platform.modules..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("com.colla.platform.shared.websocket.WebSocketMessageSender")
            .because("business modules publish durable realtime signals instead of addressing gateway-local sessions");

    @ArchTest
    static final ArchRule BUSINESS_MODULES_MUST_NOT_ACCESS_LOCAL_WEBSOCKET_REGISTRY =
        noClasses()
            .that().resideInAPackage("com.colla.platform.modules..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("com.colla.platform.shared.websocket.WebSocketSessionRegistry")
            .because("gateway-local session ownership must not leak into API or Worker business modules");

    @ArchTest
    static final ArchRule KNOWLEDGE_COLLABORATION_MUST_NOT_USE_PLATFORM_SIGNAL_SOCKET =
        noClasses()
            .that().resideInAPackage("com.colla.platform.modules.knowledge..")
            .should().dependOnClassesThat().resideInAPackage("com.colla.platform.shared.websocket..")
            .because("knowledge editing is exclusively owned by the Hocuspocus/Yjs collaboration protocol");

    @ArchTest
    static final ArchRule PROJECT_RUNTIME_MUST_NOT_READ_LIVE_CONFIGURATION =
        noClasses()
            .that().resideInAPackage("com.colla.platform.modules.project.runtime..")
            .should().dependOnClassesThat().haveNameMatching(
                ".*\\.(WorkItemTypeRepository|WorkItemFieldRepository|WorkItemFieldOptionRepository|"
                    + "WorkItemLayoutRepository|ConfigurationDraftRepository|ConfigurationTemplateRepository|"
                    + "ConfigurationPublicationRepository|WorkItemTypeCommandRepository|"
                    + "WorkItemFieldCommandRepository|WorkItemLayoutCommandRepository)"
            )
            .because("S07 runtime semantics must come from immutable published snapshots only");

    @ArchTest
    static final ArchRule PROJECT_RUNTIME_MUST_NOT_CALL_CONFIGURATION_SERVICES =
        noClasses()
            .that().resideInAPackage("com.colla.platform.modules.project.runtime..")
            .should().dependOnClassesThat().haveNameMatching(
                ".*\\.(WorkItemConfigurationDraftService|WorkItemConfigurationSnapshotAssembler|"
                    + "WorkItemConfigurationPublicationService|WorkItemConfigurationTemplateService|"
                    + "WorkItemTypeDefinitionService|WorkItemTypeConfigurationService|"
                    + "WorkItemFieldDefinitionService|WorkItemFieldConfigurationService|"
                    + "WorkItemLayoutConfigurationService|WorkItemLayoutAccessProjectionService|"
                    + "WorkItemTypePresetCatalog)"
            )
            .because("runtime configuration must be derived from the bound immutable snapshot");

    @ArchTest
    static final ArchRule WORK_ITEM_RUNTIME_MUST_NOT_READ_LIVE_OR_DRAFT_CONFIGURATION =
        noClasses()
            .that().haveSimpleNameStartingWith("WorkItemRuntime")
            .or().haveSimpleName("WorkItemService")
            .or().haveSimpleName("WorkItemFieldValueCodec")
            .should().dependOnClassesThat().haveNameMatching(
                ".*\\.(WorkItemTypeRepository|WorkItemFieldRepository|WorkItemFieldOptionRepository|"
                    + "WorkItemLayoutRepository|ConfigurationDraftRepository|ConfigurationTemplateRepository|"
                    + "ConfigurationPublicationRepository|WorkItemTypeCommandRepository|"
                    + "WorkItemFieldCommandRepository|WorkItemLayoutCommandRepository|"
                    + "WorkItemConfigurationDraftService|WorkItemConfigurationPublicationService|"
                    + "WorkItemConfigurationTemplateService|WorkItemTypeDefinitionService|"
                    + "WorkItemFieldDefinitionService|WorkItemLayoutConfigurationService)"
            )
            .because("work item commands and projections must only interpret the bound published snapshot");
}
