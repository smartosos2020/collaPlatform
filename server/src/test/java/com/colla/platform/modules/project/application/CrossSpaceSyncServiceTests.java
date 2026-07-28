package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.contract.CrossSpaceRelationCommand;
import com.colla.platform.modules.project.contract.CrossSpaceWorkItemCommand;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SaveSyncRuleCommand;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncRun;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncStep;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.CrossSpaceRelationRepository;
import com.colla.platform.modules.project.infrastructure.CrossSpaceSyncRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrossSpaceSyncServiceTests {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    @Test
    void rejectsExecutableOrUnboundedFieldTransformsBeforeAnyPersistence() {
        Fixture fixture = fixture();
        ArrayNode mappings = JsonNodeFactory.instance.arrayNode().add(
            JsonNodeFactory.instance.objectNode()
                .put("sourceField", "title")
                .put("targetField", "title")
                .put("transform", "javascript")
        );

        assertThatThrownBy(() -> fixture.service.save(
            user(),
            UUID.randomUUID(),
            new SaveSyncRuleCommand(
                1, "sync-rule-invalid-1", 0, null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Title sync", "source_to_target", "manual",
                mappings, JsonNodeFactory.instance.arrayNode(), "manual"
            )
        )).isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("invalid");

        verify(fixture.repository, never()).createRule(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void persistedRunAndStepContractsExposeFingerprintsNotFieldPayloads() {
        assertThat(Arrays.stream(SyncRun.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName))
            .doesNotContain("sourceFields", "targetFields", "fieldValues", "payload");
        assertThat(Arrays.stream(SyncStep.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName))
            .doesNotContain("beforeValue", "afterValue", "payload");
    }

    private Fixture fixture() {
        CrossSpaceSyncRepository repository = mock(CrossSpaceSyncRepository.class);
        CrossSpaceSyncService service = new CrossSpaceSyncService(
            repository,
            mock(CrossSpaceRelationRepository.class),
            mock(CrossSpaceRelationCommand.class),
            mock(CrossSpaceWorkItemCommand.class),
            mock(CrossSpaceGrantService.class),
            mock(WorkItemRelationAccessDecisionService.class),
            mock(AuditLog.class),
            mock(TransactionalOutbox.class),
            new ObjectMapper().findAndRegisterModules()
        );
        return new Fixture(repository, service);
    }

    private CurrentUser user() {
        return new CurrentUser(
            ACTOR, WORKSPACE, UUID.randomUUID(), "actor", "Actor", Set.of(), Set.of()
        );
    }

    private record Fixture(
        CrossSpaceSyncRepository repository,
        CrossSpaceSyncService service
    ) {
    }
}
