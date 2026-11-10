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

    /**
     * Compute the digest of all input so far without resetting this checksum, so that
     * it can be updated further and compared again (e.g. across multiple retry attempts).
     *
     * @return the digest bytes
     */
    public byte[] toDigest() {
        try {
            return ((MessageDigest) messageDigest.clone()).digest();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Message digest not cloneable: " + messageDigest.getAlgorithm(), e);
        }
    }
}
