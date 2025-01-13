package io.cockroachdb.jdbc.integrationtest.support;

public enum AnsiColor {
    RESET("\033[0m"),

    BLACK("\033[0;30m"),
    RED("\033[0;31m"),
    GREEN("\033[0;32m"),
    YELLOW("\033[0;33m"),
    BLUE("\033[0;34m"),
    PURPLE("\033[0;35m"),
    CYAN("\033[0;36m"),
    WHITE("\033[0;37m"),

    BOLD_BLACK("\033[1;30m"),
    BOLD_RED("\033[1;31m"),
    BOLD_GREEN("\033[1;32m"),
    BOLD_YELLOW("\033[1;33m"),
    BOLD_BLUE("\033[1;34m"),
    BOLD_PURPLE("\033[1;35m"),
    BOLD_CYAN("\033[1;36m"),
    BOLD_WHITE("\033[1;37m"),

    UNDERLINED_BLACK("\033[4;30m"),
    UNDERLINED_RED("\033[4;31m"),
    UNDERLINED_GREEN("\033[4;32m"),
    UNDERLINED_YELLOW("\033[4;33m"),
    UNDERLINED_BLUE("\033[4;34m"),
    UNDERLINED_PURPLE("\033[4;35m"),
    UNDERLINED_CYAN("\033[4;36m"),
    UNDERLINED_WHITE("\033[4;37m"),

    BACKGROUND_BLACK("\033[40m"),
    BACKGROUND_RED("\033[41m"),
    BACKGROUND_GREEN("\033[42m"),
    BACKGROUND_YELLOW("\033[43m"),
    BACKGROUND_BLUE("\033[44m"),
    BACKGROUND_PURPLE("\033[45m"),
    BACKGROUND_CYAN("\033[46m"),
    BACKGROUND_WHITE("\033[47m"),

    BRIGHT_BLACK("\033[0;90m"),
    BRIGHT_RED("\033[0;91m"),
    BRIGHT_GREEN("\033[0;92m"),
    BRIGHT_YELLOW("\033[0;93m"),
    BRIGHT_BLUE("\033[0;94m"),
    BRIGHT_PURPLE("\033[0;95m"),
    BRIGHT_CYAN("\033[0;96m"),
    BRIGHT_WHITE("\033[0;97m"),

    BOLD_BRIGHT_BLACK("\033[1;90m"),
    BOLD_BRIGHT_RED("\033[1;91m"),
    BOLD_BRIGHT_GREEN("\033[1;92m"),
    BOLD_BRIGHT_YELLOW("\033[1;93m"),
    BOLD_BRIGHT_BLUE("\033[1;94m"),
    BOLD_BRIGHT_PURPLE("\033[1;95m"),
    BOLD_BRIGHT_CYAN("\033[1;96m"),
    BOLD_BRIGHT_WHITE("\033[1;97m"),

    BACKGROUND_BRIGHT_BLACK("\033[0;100m"),
    BACKGROUND_BRIGHT_RED("\033[0;101m"),
    BACKGROUND_BRIGHT_GREEN("\033[0;102m"),
    BACKGROUND_BRIGHT_YELLOW("\033[0;103m"),
    BACKGROUND_BRIGHT_BLUE("\033[0;104m"),
    BACKGROUND_BRIGHT_PURPLE("\033[0;105m"),
    BACKGROUND_BRIGHT_CYAN("\033[0;106m"),
    BACKGROUND_BRIGHT_WHITE("\033[0;107m");

    final String code;

    AnsiColor(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return code;
    }
}
