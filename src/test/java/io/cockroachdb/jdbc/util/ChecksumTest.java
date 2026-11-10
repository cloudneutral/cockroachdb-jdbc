package io.cockroachdb.jdbc.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags(value = {
        @Tag("unit-test")
})
public class ChecksumTest {
    @Test
    public void whenCalculatingChecksum_expectSHA256DigestToMatch() {
        Checksum checksum1 = Checksum.sha256();
        checksum1.update("The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8));

        Checksum checksum2 = Checksum.sha256();
        checksum2.update("The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8));

        Assertions.assertArrayEquals(checksum1.toDigest(), checksum2.toDigest());

        Checksum checksum3 = Checksum.sha256();
        checksum3.update("The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8));
        checksum3.update(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));

        byte[] digest = checksum3.toDigest();
        Assertions.assertNotEquals(HexUtils.toHex(checksum1.toDigest()), HexUtils.toHex(digest));
        Assertions.assertNotEquals(HexUtils.toHex(checksum2.toDigest()), HexUtils.toHex(digest));
    }

    @Test
    public void whenComputingDigestRepeatedly_expectNoReset() {
        Checksum checksum = Checksum.sha256();
        checksum.update("first".getBytes(StandardCharsets.UTF_8));
        byte[] digest1 = checksum.toDigest();
        byte[] digest2 = checksum.toDigest();
        Assertions.assertArrayEquals(digest1, digest2);

        checksum.update("second".getBytes(StandardCharsets.UTF_8));

        Checksum expected = Checksum.sha256();
        expected.update("firstsecond".getBytes(StandardCharsets.UTF_8));
        Assertions.assertArrayEquals(expected.toDigest(), checksum.toDigest());
    }
}
