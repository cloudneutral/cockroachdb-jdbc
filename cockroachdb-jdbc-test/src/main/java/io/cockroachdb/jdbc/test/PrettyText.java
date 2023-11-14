package io.cockroachdb.jdbc.test;

import java.util.Locale;

import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;

public abstract class PrettyText {
    private PrettyText() {
    }

    public static String happy() {
        return "(ʘ‿ʘ)";
    }

    public static String shrug() {
        return "¯\\_(ツ)_/¯";
    }

    public static String flipTableGently() {
        return "(╯°□°)╯︵ ┻━┻";
    }

    public static String flipTableRoughly() {
        return "(ノಠ益ಠ)ノ彡┻━┻";
    }

    public static String progressBar(int total, int current, String label) {
        double p = (current + 0.0) / (Math.max(1, total) + 0.0);
        int ticks = Math.max(0, (int) (30 * p) - 1);
        return String.format(
                "%4d/%-3d %4.1f%%[%-30s] %s",
                current,
                total,
                p * 100.0,
                new String(new char[ticks]).replace('\0', '#') + ">",
                label);
    }

    public static String rate(String left, int x, String right, int y) {
        return String.format("%s: %d | %s: %d | %d total | %s rate: %.2f%%",
                left,
                x,
                right,
                y,
                x + y,
                left,
                100 - (y / (double) (Math.max(1, x + y))) * 100.0);
    }

    public static String format(AnsiColor color, String pattern, Object... args) {
        return AnsiOutput.toString(color, String.format(Locale.US, pattern, args), AnsiColor.DEFAULT);
    }

    public static void printf(AnsiColor color, String pattern, Object... args) {
        System.out.printf("%s", AnsiOutput.toString(color, String.format(Locale.US, pattern, args), AnsiColor.DEFAULT));
    }

    public static void println(AnsiColor color, String pattern, Object... args) {
        System.out.println(AnsiOutput.toString(color, String.format(Locale.US, pattern, args), AnsiColor.DEFAULT));
    }
}
