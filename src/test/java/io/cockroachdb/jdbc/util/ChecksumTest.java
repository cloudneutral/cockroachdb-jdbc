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
}
