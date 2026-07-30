package com.colla.platform.modules.project.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class ProjectSpaceExperienceBucket {
    private static final int BASIS_POINTS = 10_000;

    private ProjectSpaceExperienceBucket() {
    }

    static int stableBucket(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            long prefix = ByteBuffer.wrap(digest.digest()).getLong();
            return (int) Long.remainderUnsigned(prefix, BASIS_POINTS);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
