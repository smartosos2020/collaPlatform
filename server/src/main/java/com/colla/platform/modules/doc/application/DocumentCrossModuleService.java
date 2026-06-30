package com.colla.platform.modules.doc.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentDetail;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeContext;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.modules.project.application.ProjectService;
import com.colla.platform.modules.project.domain.ProjectModels.IssueDetail;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentCrossModuleService {
    private final DocumentService documentService;
    private final ProjectService projectService;
    private final DomainEventRepository eventRepository;
    private final AuditService auditService;

    public DocumentCrossModuleService(
        DocumentService documentService,
        ProjectService projectService,
        DomainEventRepository eventRepository,
        AuditService auditService
    ) {
        this.documentService = documentService;
        this.projectService = projectService;
        this.eventRepository = eventRepository;
        this.auditService = auditService;
    }

    @Transactional
    public IssueDetail createIssueFromSelection(
        CurrentUser currentUser,
        UUID documentId,
        UUID projectId,
        String issueType,
        String title,
        String description,
        String priority,
        UUID assigneeId,
        LocalDate dueAt,
        Integer anchorStart,
        Integer anchorEnd,
        String anchorText
    ) {
        DocumentDetail documentDetail = documentService.getDocument(currentUser, documentId);
        DocumentSummary document = documentDetail.document();
        String selectedText = normalizeNullableText(anchorText, 2000);
        String normalizedTitle = title == null || title.isBlank()
            ? defaultIssueTitleFromSelection(document, selectedText)
            : title.trim();
        KnowledgeContext knowledgeContext = documentService.knowledgeContext(currentUser, documentId);
        IssueDetail created = projectService.createIssue(
            currentUser,
            projectId,
            issueType == null || issueType.isBlank() ? "task" : issueType,
            normalizedTitle,
            documentIssueDescription(document, knowledgeContext, selectedText, description, anchorStart, anchorEnd),
            priority,
            assigneeId,
            dueAt
        );
        UUID issueId = created.issue().id();
        documentService.addRelation(currentUser, documentId, "issue", issueId);
        projectService.addRelation(currentUser, issueId, "document", documentId);
        eventRepository.append(
            currentUser.workspaceId(),
            "document.issue.created_from_selection",
            "document",
            documentId,
            currentUser.id(),
            Map.of("documentId", documentId.toString(), "issueId", issueId.toString()),
            "document.issue.created_from_selection:" + documentId + ":" + issueId
        );
        auditService.log(
            currentUser,
            "document.issue.created_from_selection",
            "document",
            documentId,
            Map.of("issueId", issueId.toString())
        );
        return projectService.getIssue(currentUser, issueId);
    }

    private String defaultIssueTitleFromSelection(DocumentSummary document, String selectedText) {
        String source = selectedText == null || selectedText.isBlank() ? document.title() : selectedText;
        String normalized = source.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }

    private String documentIssueDescription(
        DocumentSummary document,
        KnowledgeContext knowledgeContext,
        String selectedText,
        String description,
        Integer anchorStart,
        Integer anchorEnd
    ) {
        List<String> lines = new ArrayList<>();
        String normalizedDescription = normalizeNullableText(description, 2000);
        if (normalizedDescription != null) {
            lines.add(normalizedDescription);
            lines.add("");
        }
        if (knowledgeContext != null) {
            lines.add("来源知识库：" + knowledgeContext.spaceName());
            lines.add("知识路径：" + knowledgeContext.pathText());
            lines.add("反向引用：" + knowledgeContext.webPath());
        } else {
            lines.add("来源文档：" + document.title() + " /docs/" + document.id());
        }
        if (anchorStart != null && anchorEnd != null) {
            lines.add("选区范围：" + anchorStart + "-" + anchorEnd);
        }
        if (selectedText != null && !selectedText.isBlank()) {
            lines.add("");
            lines.add("> " + selectedText.replace("\n", "\n> "));
        }
        return String.join("\n", lines);
    }

    private String normalizeNullableText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
