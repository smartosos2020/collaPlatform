package com.colla.platform.modules.knowledge.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;

class KnowledgeContentApiContractTests {
    @Test
    void canonicalControllerPublishesEveryFormerEditorCapability() {
        Set<String> mappings = new HashSet<>();
        Arrays.stream(KnowledgeContentController.class.getDeclaredMethods()).forEach(method -> {
            addMappings(mappings, "GET", method.getAnnotation(GetMapping.class));
            addMappings(mappings, "POST", method.getAnnotation(PostMapping.class));
            addMappings(mappings, "PATCH", method.getAnnotation(PatchMapping.class));
            addMappings(mappings, "DELETE", method.getAnnotation(DeleteMapping.class));
        });

        Set<String> expected = Set.of(
            "GET ",
            "PATCH ",
            "PATCH /metadata",
            "GET /blocks",
            "PATCH /blocks",
            "POST /blocks",
            "PATCH /blocks/{blockId}",
            "POST /blocks/reorder",
            "DELETE /blocks/{blockId}",
            "GET /versions",
            "POST /versions/checkpoint",
            "POST /versions/named",
            "GET /versions/diff",
            "POST /versions/{versionNo}/restore",
            "POST /import/markdown",
            "POST /import/html",
            "GET /export/markdown",
            "GET /export/html",
            "POST /permissions",
            "POST /permission-requests",
            "POST /share-link",
            "POST /share-link/enable",
            "POST /share-link/disable",
            "POST /relations",
            "POST /issues/from-selection",
            "POST /comments",
            "POST /comments/{commentId}/replies",
            "POST /comments/{commentId}/resolve",
            "POST /comments/{commentId}/reopen",
            "GET /path",
            "GET /performance",
            "GET /migration-preview",
            "GET /migration/preview",
            "GET /collaboration/health"
        );
        assertTrue(mappings.containsAll(expected), () -> "Missing canonical mappings: " + difference(expected, mappings));
    }

    @Test
    void canonicalResponseDtosNeverExposeDocumentId() {
        Arrays.stream(KnowledgeApiDtos.class.getDeclaredClasses())
            .filter(Class::isRecord)
            .filter(type -> type.getSimpleName().endsWith("View"))
            .forEach(type -> {
                Set<String> names = Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
                assertFalse(names.contains("documentId"), () -> type.getSimpleName() + " exposes documentId");
                assertFalse(names.contains("sourceDocumentId"), () -> type.getSimpleName() + " exposes sourceDocumentId");
            });
    }

    private void addMappings(Set<String> mappings, String method, GetMapping mapping) {
        if (mapping != null) {
            addValues(mappings, method, mapping.value());
        }
    }

    private void addMappings(Set<String> mappings, String method, PostMapping mapping) {
        if (mapping != null) {
            addValues(mappings, method, mapping.value());
        }
    }

    private void addMappings(Set<String> mappings, String method, PatchMapping mapping) {
        if (mapping != null) {
            addValues(mappings, method, mapping.value());
        }
    }

    private void addMappings(Set<String> mappings, String method, DeleteMapping mapping) {
        if (mapping != null) {
            addValues(mappings, method, mapping.value());
        }
    }

    private void addValues(Set<String> mappings, String method, String[] values) {
        if (values.length == 0) {
            mappings.add(method + " ");
            return;
        }
        Arrays.stream(values).map(value -> method + " " + value).forEach(mappings::add);
    }

    private Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }
}

