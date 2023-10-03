package io.cockroachdb.jdbc.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

public abstract class StreamUtils {
    private StreamUtils() {
    }

    public static int drain(InputStream in) throws IOException {
        Assert.notNull(in, "No InputStream specified");
        byte[] buffer = new byte[8196];
        int byteCount;
        int bytesRead;
        for (byteCount = 0; (bytesRead = in.read(buffer)) != -1; byteCount += bytesRead) {
            // empty
        }
        return byteCount;
    }

    public static int drain(Reader in) throws IOException {
        Assert.notNull(in, "No Reader specified");
        char[] buffer = new char[8196];
        int charCount;
        int charsRead;
        for (charCount = 0; (charsRead = in.read(buffer)) != -1; charCount += charsRead) {
            // empty
        }
        return charCount;
    }
}
