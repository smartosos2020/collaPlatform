package com.colla.platform.modules.platform.application;

import com.colla.platform.modules.platform.domain.PlatformModels.ParsedInternalLink;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class InternalLinkService {
    private static final Pattern WEB_OBJECT_PATTERN = Pattern.compile("^/(issues|docs|bases|approvals)/([0-9a-fA-F-]{36})(?:/.*)?$");
    private static final Pattern COLLA_OBJECT_PATTERN = Pattern.compile("^colla://([a-zA-Z_-]+)/([0-9a-fA-F-]{36})(?:/.*)?$");

    private final PlatformObjectRepository objectRepository;
    private final PlatformObjectResolverRegistry resolverRegistry;

    public InternalLinkService(PlatformObjectRepository objectRepository, PlatformObjectResolverRegistry resolverRegistry) {
        this.objectRepository = objectRepository;
        this.resolverRegistry = resolverRegistry;
    }

    public ParsedInternalLink resolve(CurrentUser currentUser, String source) {
        if (source == null || source.isBlank()) {
            return ParsedInternalLink.unresolved(source);
        }

        String normalized = normalize(source.trim());
        Optional<ParsedInternalLink> exact = objectRepository.findObjectLinkByPath(currentUser.workspaceId(), normalized)
            .map(link -> toParsed(currentUser, source, link.objectType(), link.objectId()));
        if (exact.isPresent()) {
            return exact.get();
        }

        Matcher webMatcher = WEB_OBJECT_PATTERN.matcher(normalized);
        if (webMatcher.matches()) {
            return toParsed(currentUser, source, webType(webMatcher.group(1)), UUID.fromString(webMatcher.group(2)));
        }

        Matcher deepMatcher = COLLA_OBJECT_PATTERN.matcher(normalized);
        if (deepMatcher.matches()) {
            return toParsed(currentUser, source, deepType(deepMatcher.group(1)), UUID.fromString(deepMatcher.group(2)));
        }

        return ParsedInternalLink.unresolved(source);
    }

    private ParsedInternalLink toParsed(CurrentUser currentUser, String source, String objectType, UUID objectId) {
        PlatformObjectSummary summary = resolverRegistry.resolve(currentUser, objectType, objectId);
        return new ParsedInternalLink(
            summary.accessState().name().equals("available"),
            source,
            objectType,
            objectId,
            summary.webPath(),
            summary.deepLink(),
            summary
        );
    }

    private String normalize(String source) {
        if (source.startsWith("http://") || source.startsWith("https://")) {
            URI uri = URI.create(source);
            return uri.getPath();
        }
        return source;
    }

    private String webType(String segment) {
        return switch (segment) {
            case "issues" -> "issue";
            case "docs" -> "document";
            case "bases" -> "base";
            case "approvals" -> "approval";
            default -> segment;
        };
    }

    private String deepType(String type) {
        return type.replace("-", "_");
    }
}
