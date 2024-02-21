package io.cockroachdb.jdbc.util;

/**
 * Hex encoding and decoding helper.
 *
 * @author Kai Niemi
 */
public abstract class HexUtils {
    private HexUtils() {
    }

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    public static String toHex(byte[] bytes) {
        return new String(toHexChars(bytes));
    }

    public static char[] toHexChars(byte[] bytes) {
        int byteCount = bytes.length;
        char[] result = new char[2 * byteCount];
        int j = 0;
        for (byte b : bytes) {
            result[j++] = HEX_CHARS[(240 & b) >>> 4];
            result[j++] = HEX_CHARS[15 & b];
        }
        return result;
    }

    public static String fromHex(char[] hexChars) {
        return new String(toBytes(hexChars));
    }

    public static byte[] toBytes(char[] hexChars) {
        int len = hexChars.length;
        byte[] r = new byte[len / 2];

        for (int i = 0; i < r.length; i++) {
            int d1 = hexChars[i * 2];
            int d2 = hexChars[i * 2 + 1];
            if ((d1 >= '0') && (d1 <= '9')) {
                d1 -= '0';
            } else if ((d1 >= 'a') && (d1 <= 'f')) {
                d1 -= 'a' - 10;
            } else if ((d1 >= 'A') && (d1 <= 'F')) {
                d1 -= 'A' - 10;
            }

            if ((d2 >= '0') && (d2 <= '9')) {
                d2 -= '0';
            } else if ((d2 >= 'a') && (d2 <= 'f')) {
                d2 -= 'a' - 10;
            } else if ((d2 >= 'A') && (d2 <= 'F')) {
                d2 -= 'A' - 10;
            }
            r[i] = (byte) ((d1 << 4) + d2);
        }

        return r;
    }
}
