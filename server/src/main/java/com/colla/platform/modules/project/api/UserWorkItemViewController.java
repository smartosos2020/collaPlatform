package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemViewService;
import com.colla.platform.modules.project.domain.WorkItemViewModels.BulkCommand;
import com.colla.platform.modules.project.domain.WorkItemViewModels.BulkResult;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ExportCommand;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ExportJob;
import com.colla.platform.modules.project.domain.WorkItemViewModels.PreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ViewPreference;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ViewRequest;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ViewResult;
import com.colla.platform.shared.auth.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}")
public final class UserWorkItemViewController {
    private final WorkItemViewService service;

    public UserWorkItemViewController(WorkItemViewService service) {
        this.service = service;
    }

    @PostMapping("/work-item-views:render")
    public ViewResult render(
        @PathVariable UUID spaceId,
        @RequestBody ViewRequest request,
        Authentication authentication
    ) {
        return service.render(user(authentication), spaceId, request);
    }

    @GetMapping("/work-item-views/preferences/{viewKey}")
    public ViewPreference preference(
        @PathVariable UUID spaceId,
        @PathVariable String viewKey,
        Authentication authentication
    ) {
        return service.preference(user(authentication), spaceId, viewKey);
    }

    @PutMapping("/work-item-views/preferences/{viewKey}")
    public ViewPreference savePreference(
        @PathVariable UUID spaceId,
        @PathVariable String viewKey,
        @RequestBody PreferenceCommand command,
        Authentication authentication
    ) {
        return service.savePreference(user(authentication), spaceId, viewKey, command);
    }

    @PostMapping("/work-item-views:bulk")
    public BulkResult bulk(
        @PathVariable UUID spaceId,
        @RequestBody BulkCommand command,
        Authentication authentication
    ) {
        return service.bulk(user(authentication), spaceId, command);
    }

    @PostMapping("/work-item-views:export")
    public ExportJob export(
        @PathVariable UUID spaceId,
        @RequestBody ExportCommand command,
        Authentication authentication
    ) {
        return service.createExport(user(authentication), spaceId, command);
    }

    @GetMapping("/work-item-views/exports/{exportId}/download")
    public ResponseEntity<byte[]> download(
        @PathVariable UUID spaceId,
        @PathVariable UUID exportId,
        Authentication authentication
    ) {
        var result = service.download(user(authentication), spaceId, exportId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(result.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(result.fileName(), StandardCharsets.UTF_8)
            .build());
        return ResponseEntity.ok().headers(headers)
            .body(result.content().getBytes(StandardCharsets.UTF_8));
    }

    private CurrentUser user(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
