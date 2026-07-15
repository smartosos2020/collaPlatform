package com.colla.platform.modules.knowledge.application;

import com.colla.platform.modules.knowledge.domain.KnowledgeContentCanonicalModels;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentCanonicalModels.KnowledgeContentCanonicalBlock;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentCanonicalModels.KnowledgeContentCanonicalDocument;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentCanonicalModels.KnowledgeContentCanonicalEmbed;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentCanonicalModels.KnowledgeContentSchemaIssue;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentBlockDraft;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeContentSchemaService {
    private static final Set<String> BLOCK_TYPES = Set.of(
        "paragraph", "heading", "bulletList", "orderedList", "listItem", "taskList", "taskItem",
        "blockquote", "codeBlock", "table", "tableRow", "tableHeader", "tableCell", "image", "file",
        "embed", "objectCard", "fileCard", "callout", "horizontalRule", "toc", "unsupported", "text"
    );
    private static final Set<String> MARK_TYPES = Set.of("bold", "italic", "strike", "underline", "link", "code");
    private static final Map<String, String> TYPE_ALIASES = Map.ofEntries(
        Map.entry("bullet_list", "bulletList"),
        Map.entry("bulleted_list", "bulletList"),
        Map.entry("ordered_list", "orderedList"),
        Map.entry("task_list", "taskList"),
        Map.entry("task_item", "taskItem"),
        Map.entry("code_block", "codeBlock"),
        Map.entry("blockquote", "blockquote"),
        Map.entry("quote", "blockquote"),
        Map.entry("divider", "horizontalRule"),
        Map.entry("legacy_html", "unsupported"),
        Map.entry("embed_object", "embed")
    );

    private final ObjectMapper objectMapper;
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {
    };

    public KnowledgeContentSchemaService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public KnowledgeContentCanonicalDocument fromLegacy(
        UUID itemId,
        List<KnowledgeContentBlockDraft> blocks,
        String markdown
    ) {
        List<KnowledgeContentBlockDraft> source = blocks == null ? List.of() : blocks.stream()
            .filter(block -> block != null && !Boolean.TRUE.equals(block.deleted()))
            .sorted(Comparator.comparingInt(block -> block.sortOrder() == null ? Integer.MAX_VALUE : block.sortOrder()))
            .toList();
        List<KnowledgeContentBlockDraft> effective = source.isEmpty() ? parseMarkdown(markdown) : source;
        ArrayNode content = JsonNodeFactory.instance.arrayNode();
        Map<UUID, ObjectNode> nodes = new LinkedHashMap<>();
        Map<UUID, UUID> parentIds = new HashMap<>();
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < effective.size(); index++) {
            KnowledgeContentBlockDraft block = effective.get(index);
            UUID blockId = block.id() == null
                ? deterministicId(itemId, index, normalizeLegacyType(block.blockType()), block.content())
                : block.id();
            if (!ids.add(blockId)) {
                blockId = deterministicId(itemId, index, normalizeLegacyType(block.blockType()), block.content() + ":duplicate");
                ids.add(blockId);
            }
            ObjectNode node = legacyNode(block, blockId);
            nodes.put(blockId, node);
            if (block.parentId() != null) {
                parentIds.put(blockId, block.parentId());
            }
        }
        for (UUID blockId : ids) {
            ObjectNode node = nodes.get(blockId);
            UUID parentId = parentIds.get(blockId);
            if (parentId != null && nodes.containsKey(parentId) && !parentId.equals(blockId)) {
                nodes.get(parentId).withArray("content").add(node);
            } else {
                content.add(node);
            }
        }
        ObjectNode document = document(content);
        KnowledgeContentCanonicalDocument normalized = normalizeDocument(document, itemId);
        return new KnowledgeContentCanonicalDocument(
            normalized.document(),
            normalized.schemaVersion(),
            normalized.checksum(),
            normalized.blocks(),
            normalized.plainText(),
            normalized.markdown(),
            normalized.embeds(),
            normalized.issues()
        );
    }

    public String legacyChecksum(UUID itemId, List<KnowledgeContentBlockDraft> blocks, String markdown) {
        List<KnowledgeContentBlockDraft> source = blocks == null ? List.of() : blocks.stream()
            .filter(block -> block != null && !Boolean.TRUE.equals(block.deleted()))
            .sorted(Comparator.comparingInt(block -> block.sortOrder() == null ? Integer.MAX_VALUE : block.sortOrder()))
            .toList();
        ObjectNode legacySource = objectMapper.createObjectNode();
        legacySource.put("itemId", itemId == null ? "" : itemId.toString());
        legacySource.set("blocks", objectMapper.valueToTree(source));
        legacySource.put("markdown", markdown == null ? "" : markdown);
        return checksum(legacySource);
    }

    public KnowledgeContentCanonicalDocument normalizeDocument(JsonNode source, UUID itemId) {
        List<KnowledgeContentSchemaIssue> issues = new ArrayList<>();
        if (source == null || !source.isObject()) {
            throw schemaException("SCHEMA_INVALID_DOCUMENT", "$", "Canonical document must be an object");
        }
        int sourceVersion = source.path("schemaVersion").asInt(1);
        if (sourceVersion > KnowledgeContentCanonicalModels.CURRENT_SCHEMA_VERSION) {
            throw schemaException("SCHEMA_UNSUPPORTED", "$.schemaVersion", "Document schema version is newer than this server");
        }
        if (!source.path("type").asText("doc").equals("doc")) {
            throw schemaException("SCHEMA_INVALID_ROOT", "$.type", "Canonical document root must be doc");
        }
        JsonNode rawContent = source.get("content");
        if (rawContent != null && !rawContent.isArray()) {
            throw schemaException("SCHEMA_INVALID_CONTENT", "$.content", "Document content must be an array");
        }
        ArrayNode content = JsonNodeFactory.instance.arrayNode();
        Set<UUID> assignedIds = new HashSet<>();
        if (rawContent != null) {
            for (int index = 0; index < rawContent.size(); index++) {
                content.add(normalizeNode(rawContent.get(index), itemId, "content[" + index + "]", index, null, assignedIds, issues));
            }
        }
        if (content.isEmpty()) {
            content.add(textBlock("paragraph", deterministicId(itemId, 0, "paragraph", ""), ""));
        }
        ObjectNode document = document(content);
        ObjectNode metadata = source.path("metadata").isObject() ? (ObjectNode) source.path("metadata") : null;
        if (metadata != null && !metadata.isEmpty()) {
            document.set("metadata", sortJson(metadata));
        }
        JsonNode sortedDocument = sortJson(document);
        return project(sortedDocument, issues);
    }

    public String checksum(JsonNode value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(writeJson(sortJson(value)).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    public List<KnowledgeContentBlockDraft> toBlockDrafts(KnowledgeContentCanonicalDocument canonical) {
        return canonical.blocks().stream().map(block -> {
            JsonNode node = block.node();
            Map<String, Object> attrs = node.path("attrs").isObject()
                ? objectMapper.convertValue(node.path("attrs"), STRING_OBJECT_MAP)
                : Map.of();
            Map<String, Object> richContent = Map.of(
                "type", "tiptap-node",
                "node", objectMapper.convertValue(node, STRING_OBJECT_MAP)
            );
            String type = switch (block.blockType()) {
                case "bulletList" -> "bullet_list";
                case "orderedList" -> "ordered_list";
                case "taskList", "taskItem" -> "task_item";
                case "codeBlock" -> "code_block";
                case "blockquote" -> "quote";
                case "horizontalRule" -> "divider";
                case "objectCard" -> objectCardBlockType(attrs);
                case "fileCard" -> "file_embed";
                default -> block.blockType();
            };
            String content = switch (block.blockType()) {
                case "objectCard", "fileCard" -> writeJson(node.path("attrs"));
                default -> block.markdown();
            };
            return new KnowledgeContentBlockDraft(
                block.blockId(), block.parentId(), type, content, block.sortOrder(),
                KnowledgeContentCanonicalModels.CURRENT_SCHEMA_VERSION, attrs, richContent,
                block.plainText(), "block-" + block.blockId(), false
            );
        }).toList();
    }

    private String objectCardBlockType(Map<String, Object> attrs) {
        String objectType = String.valueOf(attrs.getOrDefault("objectType", "knowledge_content"));
        return switch (objectType) {
            case "base", "base_table" -> "base_view";
            case "issue" -> "issue_embed";
            case "message" -> "message_embed";
            case "file" -> "file_embed";
            case "external_link" -> "link_card";
            default -> "embed_object";
        };
    }

    private ObjectNode legacyNode(KnowledgeContentBlockDraft block, UUID blockId) {
        String type = normalizeLegacyType(block.blockType());
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", type);
        ObjectNode attrs = node.putObject("attrs");
        attrs.put("blockId", blockId.toString());
        if (block.parentId() != null) {
            attrs.put("parentBlockId", block.parentId().toString());
        }
        if (block.attrs() != null) {
            block.attrs().forEach((key, value) -> {
                if (!"blockId".equals(key) && !"parentBlockId".equals(key)) {
                    attrs.set(key, objectMapper.valueToTree(value));
                }
            });
        }
        if ("table".equals(type)) {
            node.set("content", tableContent(block.content()));
        } else if ("embed".equals(type)) {
            JsonNode embed = parseJson(block.content());
            if (embed != null) {
                attrs.set("object", sortJson(embed));
            }
        } else if (!"horizontalRule".equals(type)) {
            String text = block.content() == null ? "" : block.content();
            if ("heading".equals(type) && !attrs.has("level")) {
                attrs.put("level", 1);
            }
            node.set("content", textContent(text));
        }
        return node;
    }

    private JsonNode tableContent(String content) {
        JsonNode value = parseJson(content);
        if (value == null || !value.isObject()) {
            return JsonNodeFactory.instance.arrayNode();
        }
        ArrayNode rows = JsonNodeFactory.instance.arrayNode();
        JsonNode columns = value.path("columns");
        JsonNode dataRows = value.path("rows");
        if (dataRows.isArray()) {
            for (JsonNode row : dataRows) {
                ArrayNode cells = JsonNodeFactory.instance.arrayNode();
                if (row.isArray()) {
                    for (JsonNode cell : row) {
                        cells.add(tableCell(cell.asText("")));
                    }
                }
                rows.add(tableRow(cells));
            }
        } else if (columns.isArray()) {
            ArrayNode cells = JsonNodeFactory.instance.arrayNode();
            for (JsonNode column : columns) {
                cells.add(tableCell(column.asText("")));
            }
            rows.add(tableRow(cells));
        }
        return rows;
    }

    private ObjectNode tableRow(ArrayNode cells) {
        ObjectNode row = JsonNodeFactory.instance.objectNode();
        row.put("type", "tableRow");
        row.set("content", cells);
        return row;
    }

    private ObjectNode tableCell(String value) {
        ObjectNode cell = JsonNodeFactory.instance.objectNode();
        cell.put("type", "tableCell");
        cell.set("content", JsonNodeFactory.instance.arrayNode().add(textBlock("paragraph", null, value)));
        return cell;
    }

    private ObjectNode textBlock(String type, UUID blockId, String text) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", type);
        if (blockId != null) {
            node.putObject("attrs").put("blockId", blockId.toString());
        }
        if (!"horizontalRule".equals(type)) {
            node.set("content", textContent(text));
        }
        return node;
    }

    private ArrayNode textContent(String value) {
        ArrayNode content = JsonNodeFactory.instance.arrayNode();
        if (value != null && !value.isEmpty()) {
            content.add(JsonNodeFactory.instance.objectNode().put("type", "text").put("text", value));
        }
        return content;
    }

    private ObjectNode document(ArrayNode content) {
        ObjectNode document = JsonNodeFactory.instance.objectNode();
        document.put("type", "doc");
        document.put("schemaVersion", KnowledgeContentCanonicalModels.CURRENT_SCHEMA_VERSION);
        document.set("content", content);
        return document;
    }

    private ObjectNode normalizeNode(
        JsonNode source,
        UUID itemId,
        String path,
        int index,
        UUID parentId,
        Set<UUID> assignedIds,
        List<KnowledgeContentSchemaIssue> issues
    ) {
        if (source == null || !source.isObject()) {
            throw schemaException("SCHEMA_INVALID_NODE", path, "Node must be an object");
        }
        String rawType = source.path("type").asText("");
        if (rawType.isBlank() || "doc".equals(rawType)) {
            throw schemaException("SCHEMA_INVALID_NODE_TYPE", path + ".type", "Editable node type is required");
        }
        String type = TYPE_ALIASES.getOrDefault(rawType, rawType);
        if ("text".equals(type)) {
            String text = source.path("text").asText(null);
            if (text == null) {
                throw schemaException("SCHEMA_INVALID_TEXT", path + ".text", "Text node requires text");
            }
            ObjectNode textNode = JsonNodeFactory.instance.objectNode();
            textNode.put("type", "text");
            textNode.put("text", text);
            if (source.has("marks")) {
                textNode.set("marks", normalizeMarks(source.get("marks"), path + ".marks", issues));
            }
            return textNode;
        }
        ObjectNode attrs = source.path("attrs").isObject()
            ? (ObjectNode) sortJson(source.path("attrs"))
            : JsonNodeFactory.instance.objectNode();
        UUID blockId = parseUuid(attrs.path("blockId").asText(null));
        if (blockId == null || assignedIds.contains(blockId)) {
            blockId = deterministicId(itemId, index, rawType, writeJson(source));
            if (assignedIds.contains(blockId)) {
                blockId = deterministicId(itemId, index, rawType, writeJson(source) + ":duplicate");
            }
            issues.add(new KnowledgeContentSchemaIssue(
                "SCHEMA_BLOCK_ID_ASSIGNED",
                path + ".attrs.blockId",
                "Stable blockId was assigned during normalization",
                "warning"
            ));
        }
        assignedIds.add(blockId);
        attrs.put("blockId", blockId.toString());
        if (parentId != null) {
            attrs.put("parentBlockId", parentId.toString());
        }
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        if (!BLOCK_TYPES.contains(type)) {
            issues.add(new KnowledgeContentSchemaIssue(
                "SCHEMA_UNKNOWN_NODE_PRESERVED",
                path + ".type",
                "Unknown node preserved as unsupported content",
                "warning"
            ));
            normalized.put("type", "unsupported");
            ObjectNode unsupportedAttrs = normalized.putObject("attrs");
            unsupportedAttrs.put("blockId", blockId.toString());
            unsupportedAttrs.put("originalType", rawType);
            unsupportedAttrs.set("originalNode", sortJson(source));
            normalized.set("content", textContent(extractText(source)));
            return normalized;
        }
        normalized.put("type", type);
        normalized.set("attrs", attrs);
        JsonNode rawContent = source.get("content");
        if (rawContent != null && !rawContent.isArray()) {
            throw schemaException("SCHEMA_INVALID_CONTENT", path + ".content", "Node content must be an array");
        }
        if (rawContent != null) {
            ArrayNode childContent = normalized.putArray("content");
            for (int childIndex = 0; childIndex < rawContent.size(); childIndex++) {
                childContent.add(normalizeNode(
                    rawContent.get(childIndex),
                    itemId,
                    path + ".content[" + childIndex + "]",
                    index * 1000 + childIndex + 1,
                    blockId,
                    assignedIds,
                    issues
                ));
            }
        } else if (Set.of("paragraph", "heading", "blockquote", "codeBlock", "callout", "unsupported").contains(type)) {
            normalized.set("content", textContent(extractText(source)));
        }
        JsonNode marks = source.get("marks");
        if (marks != null) {
            normalized.set("marks", normalizeMarks(marks, path + ".marks", issues));
        }
        return normalized;
    }

    private ArrayNode normalizeMarks(JsonNode marks, String path, List<KnowledgeContentSchemaIssue> issues) {
        if (!marks.isArray()) {
            throw schemaException("SCHEMA_INVALID_MARKS", path, "Marks must be an array");
        }
        ArrayNode normalizedMarks = JsonNodeFactory.instance.arrayNode();
        for (JsonNode mark : marks) {
            if (!mark.isObject() || mark.path("type").asText("").isBlank()) {
                throw schemaException("SCHEMA_INVALID_MARK", path, "Mark type is required");
            }
            String markType = mark.path("type").asText();
            if (!MARK_TYPES.contains(markType)) {
                ObjectNode safeMark = JsonNodeFactory.instance.objectNode();
                safeMark.put("type", "unsupported");
                safeMark.set("originalMark", sortJson(mark));
                normalizedMarks.add(safeMark);
                issues.add(new KnowledgeContentSchemaIssue("SCHEMA_UNKNOWN_MARK_PRESERVED", path, "Unknown mark preserved", "warning"));
            } else {
                normalizedMarks.add(sortJson(mark));
            }
        }
        return normalizedMarks;
    }

    private KnowledgeContentCanonicalDocument project(JsonNode document, List<KnowledgeContentSchemaIssue> issues) {
        List<KnowledgeContentCanonicalBlock> blocks = new ArrayList<>();
        List<KnowledgeContentCanonicalEmbed> embeds = new ArrayList<>();
        StringBuilder plainText = new StringBuilder();
        StringBuilder markdown = new StringBuilder();
        JsonNode content = document.path("content");
        for (int index = 0; index < content.size(); index++) {
            JsonNode node = content.get(index);
            UUID blockId = uuidFromNode(node);
            collectNode(node, blockId, null, index, blocks, embeds, plainText, markdown);
        }
        return new KnowledgeContentCanonicalDocument(
            document,
            document.path("schemaVersion").asInt(KnowledgeContentCanonicalModels.CURRENT_SCHEMA_VERSION),
            checksum(document),
            List.copyOf(blocks),
            trimTrailingNewline(plainText),
            trimTrailingNewline(markdown),
            List.copyOf(embeds),
            List.copyOf(issues)
        );
    }

    private void collectNode(
        JsonNode node,
        UUID blockId,
        UUID parentId,
        int sortOrder,
        List<KnowledgeContentCanonicalBlock> blocks,
        List<KnowledgeContentCanonicalEmbed> embeds,
        StringBuilder plainText,
        StringBuilder markdown
    ) {
        String type = node.path("type").asText("unsupported");
        String nodePlainText = nodeText(node);
        String nodeMarkdown = nodeMarkdown(node);
        Map<String, Object> embedMetadata = embedMetadata(node);
        blocks.add(new KnowledgeContentCanonicalBlock(blockId, parentId, sortOrder, type, node, nodePlainText, nodeMarkdown, embedMetadata));
        if (!nodePlainText.isBlank()) {
            plainText.append(nodePlainText).append('\n');
        }
        if (!nodeMarkdown.isBlank()) {
            markdown.append(nodeMarkdown).append('\n');
        }
        if (!embedMetadata.isEmpty()) {
            embeds.add(new KnowledgeContentCanonicalEmbed(
                blockId,
                String.valueOf(embedMetadata.getOrDefault("objectType", "")),
                parseUuid(String.valueOf(embedMetadata.getOrDefault("objectId", ""))),
                String.valueOf(embedMetadata.getOrDefault("targetRoute", "")),
                String.valueOf(embedMetadata.getOrDefault("title", ""))
            ));
        }
        JsonNode children = node.get("content");
        if (children != null && children.isArray()) {
            for (int index = 0; index < children.size(); index++) {
                JsonNode child = children.get(index);
                if (!"text".equals(child.path("type").asText())) {
                    collectNode(child, uuidFromNode(child), blockId, index, blocks, embeds, plainText, markdown);
                }
            }
        }
    }

    private String nodeText(JsonNode node) {
        if ("text".equals(node.path("type").asText())) {
            return node.path("text").asText("");
        }
        JsonNode content = node.get("content");
        if (content == null || !content.isArray()) {
            return "";
        }
        return java.util.stream.StreamSupport.stream(content.spliterator(), false)
            .map(this::nodeText)
            .filter(value -> !value.isBlank())
            .collect(Collectors.joining(" "))
            .trim();
    }

    private String nodeMarkdown(JsonNode node) {
        String type = node.path("type").asText("unsupported");
        String text = nodeText(node);
        return switch (type) {
            case "heading" -> "# " + text;
            case "bulletList" -> text;
            case "orderedList" -> text;
            case "blockquote" -> text.lines().map(line -> "> " + line).collect(Collectors.joining("\n"));
            case "codeBlock" -> "```\n" + text + "\n```";
            case "horizontalRule" -> "---";
            case "table" -> text;
            case "embed" -> "[对象] " + text;
            default -> text;
        };
    }

    private Map<String, Object> embedMetadata(JsonNode node) {
        if (!"embed".equals(node.path("type").asText())) {
            return Map.of();
        }
        JsonNode object = node.path("attrs").path("object");
        if (!object.isObject()) {
            return Map.of();
        }
        Map<String, Object> values = objectMapper.convertValue(object, STRING_OBJECT_MAP);
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private String extractText(JsonNode source) {
        if (source == null) {
            return "";
        }
        if (source.has("text")) {
            return source.path("text").asText("");
        }
        if (source.has("attrs") && source.path("attrs").has("originalContent")) {
            return source.path("attrs").path("originalContent").asText("");
        }
        return "";
    }

    private List<KnowledgeContentBlockDraft> parseMarkdown(String markdown) {
        String value = markdown == null ? "" : markdown.replace("\r\n", "\n");
        if (value.isBlank()) {
            return List.of(new KnowledgeContentBlockDraft("paragraph", "", 0));
        }
        List<KnowledgeContentBlockDraft> result = new ArrayList<>();
        boolean inCode = false;
        StringBuilder code = new StringBuilder();
        for (String line : value.split("\n", -1)) {
            if (line.trim().startsWith("```")) {
                if (inCode) {
                    result.add(new KnowledgeContentBlockDraft("code_block", code.toString(), result.size()));
                    code.setLength(0);
                }
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                if (code.length() > 0) {
                    code.append('\n');
                }
                code.append(line);
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.matches("^#{1,6}\\s+.*")) {
                int level = trimmed.indexOf(' ');
                KnowledgeContentBlockDraft block = new KnowledgeContentBlockDraft("heading", trimmed.substring(level + 1), result.size());
                result.add(withAttrs(block, Map.of("level", Math.max(1, Math.min(6, level)))));
            } else if (trimmed.matches("^[-*]?[ ]*\\[[ xX]\\][ ]+.*")) {
                boolean checked = trimmed.matches("^[-*]?[ ]*\\[[xX]\\].*");
                result.add(withAttrs(new KnowledgeContentBlockDraft("task", trimmed.replaceFirst("^[-*]?[ ]*\\[[ xX]\\][ ]+", ""), result.size()), Map.of("checked", checked)));
            } else if (trimmed.matches("^[-*+]\\s+.*")) {
                result.add(new KnowledgeContentBlockDraft("bullet_list", trimmed.substring(2), result.size()));
            } else if (trimmed.matches("^\\d+[.)]\\s+.*")) {
                result.add(new KnowledgeContentBlockDraft("ordered_list", trimmed.replaceFirst("^\\d+[.)]\\s+", ""), result.size()));
            } else if (trimmed.startsWith(">")) {
                result.add(new KnowledgeContentBlockDraft("quote", trimmed.substring(1).trim(), result.size()));
            } else if (trimmed.matches("^([-*_])(?:[ ]*\\1){2,}$")) {
                result.add(new KnowledgeContentBlockDraft("divider", "", result.size()));
            } else {
                result.add(new KnowledgeContentBlockDraft("paragraph", line, result.size()));
            }
        }
        if (inCode) {
            result.add(new KnowledgeContentBlockDraft("code_block", code.toString(), result.size()));
        }
        return result.isEmpty() ? List.of(new KnowledgeContentBlockDraft("paragraph", "", 0)) : result;
    }

    private KnowledgeContentBlockDraft withAttrs(KnowledgeContentBlockDraft block, Map<String, Object> attrs) {
        return new KnowledgeContentBlockDraft(
            block.id(), block.parentId(), block.blockType(), block.content(), block.sortOrder(), block.schemaVersion(),
            attrs, block.richContent(), block.plainText(), block.anchorId(), block.deleted()
        );
    }

    private String normalizeLegacyType(String type) {
        String value = type == null || type.isBlank() ? "paragraph" : type;
        return TYPE_ALIASES.getOrDefault(value, switch (value) {
            case "code", "code_block" -> "codeBlock";
            case "list", "bullet_list", "bulleted_list" -> "bulletList";
            case "ordered_list" -> "orderedList";
            case "task", "todo", "task_item" -> "taskItem";
            case "quote" -> "blockquote";
            case "divider" -> "horizontalRule";
            case "embed_object", "base_view", "issue_embed", "message_embed", "file_embed", "link", "link_card" -> "embed";
            default -> value;
        });
    }

    private UUID deterministicId(UUID itemId, int index, String type, String value) {
        String seed = "colla-kb-canonical:" + (itemId == null ? "none" : itemId) + ":" + index + ":" + type + ":" + value;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private UUID uuidFromNode(JsonNode node) {
        return parseUuid(node.path("attrs").path("blockId").asText(null));
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize canonical document", exception);
        }
    }

    private JsonNode sortJson(JsonNode value) {
        if (value == null || value.isValueNode()) {
            return value == null ? JsonNodeFactory.instance.nullNode() : value;
        }
        if (value.isArray()) {
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> array.add(sortJson(item)));
            return array;
        }
        ObjectNode object = JsonNodeFactory.instance.objectNode();
        TreeSet<String> names = new TreeSet<>();
        value.fieldNames().forEachRemaining(names::add);
        for (String name : names) {
            object.set(name, sortJson(value.get(name)));
        }
        return object;
    }

    private String trimTrailingNewline(StringBuilder value) {
        return value.toString().replaceFirst("\\n+$", "");
    }

    private RuntimeException schemaException(String code, String path, String message) {
        return new KnowledgeContentSchemaException(code, path, message);
    }

    public static final class KnowledgeContentSchemaException extends IllegalArgumentException {
        private final String code;
        private final String path;

        public KnowledgeContentSchemaException(String code, String path, String message) {
            super(message);
            this.code = code;
            this.path = path;
        }

        public String code() {
            return code;
        }

        public String path() {
            return path;
        }
    }
}
