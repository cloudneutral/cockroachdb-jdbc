package io.cockroachdb.jdbc.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Helper for message digests / checksums.
 *
 * @author Kai Niemi
 */
@SuppressWarnings("ALL")
public class Checksum {
    public static Checksum sha256() {
        return new Checksum("SHA-256");
    }

    private final MessageDigest messageDigest;

    public Checksum(String algorithm) {
        try {
            this.messageDigest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Checksum update(byte input) {
        this.messageDigest.update(input);
        return this;
    }

    public Checksum update(byte[] input, int offset, int len) {
        this.messageDigest.update(input, offset, len);
        return this;
    }

    public Checksum update(byte[] input) {
        this.messageDigest.update(input);
        return this;
    }

    public byte[] toDigest() {
        return messageDigest.digest();
    }
}
