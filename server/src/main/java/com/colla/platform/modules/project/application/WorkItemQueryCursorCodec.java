package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryCursor;
import com.colla.platform.shared.auth.JwtTokenProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemQueryCursorCodec {
    private static final String SCHEMA = "work-item-query-v1";
    private final JwtTokenProperties properties;

    public WorkItemQueryCursorCodec(JwtTokenProperties properties) {
        this.properties = properties;
    }

    public String encode(
        UUID workspaceId,
        UUID userId,
        UUID spaceId,
        String queryHash,
        UUID anchorId
    ) {
        String value = String.join(
            "|",
            SCHEMA,
            workspaceId.toString(),
            userId.toString(),
            spaceId.toString(),
            queryHash,
            anchorId.toString(),
            Instant.now().toString()
        );
        byte[] body = value.getBytes(StandardCharsets.UTF_8);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(body) + "." + encoder.encodeToString(sign(body));
    }

    public QueryCursor decode(
        String encoded,
        UUID workspaceId,
        UUID userId,
        UUID spaceId,
        String queryHash
    ) {
        try {
            String[] token = encoded.split("\\.", -1);
            if (token.length != 2) throw new IllegalArgumentException("parts");
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] body = decoder.decode(token[0]);
            byte[] supplied = decoder.decode(token[1]);
            if (!MessageDigest.isEqual(sign(body), supplied)) {
                throw new IllegalArgumentException("signature");
            }
            String[] values = new String(body, StandardCharsets.UTF_8).split("\\|", -1);
            if (values.length != 7
                || !SCHEMA.equals(values[0])
                || !workspaceId.toString().equals(values[1])
                || !userId.toString().equals(values[2])
                || !spaceId.toString().equals(values[3])
                || !queryHash.equals(values[4])) {
                throw new IllegalArgumentException("scope");
            }
            return new QueryCursor(values[4], UUID.fromString(values[5]), Instant.parse(values[6]));
        } catch (RuntimeException exception) {
            throw failure("INVALID_QUERY_CURSOR", "Query cursor is invalid or belongs to another scope");
        }
    }

    private byte[] sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                properties.getAccessSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            ));
            return mac.doFinal(body);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("WorkItem query cursor signing is unavailable", exception);
        }
    }
}
