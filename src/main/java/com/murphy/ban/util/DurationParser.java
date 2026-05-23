package com.murphy.ban.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;
    private static final long WEEK = 7L * DAY;
    private static final long MONTH = 30L * DAY;

    // `mo` is listed first so it wins over `m` (minutes) in the alternation.
    private static final Pattern UNIT_PATTERN = Pattern.compile("(\\d+)(mo|[smhdw])");

    private DurationParser() {
    }

    public static long parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Duration input cannot be null");
        }
        String trimmed = input.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Duration input cannot be empty");
        }
        if (trimmed.equals("permanent") || trimmed.equals("perm")) {
            return -1L;
        }

        Matcher matcher = UNIT_PATTERN.matcher(trimmed);
        long total = 0L;
        int lastEnd = 0;
        boolean matched = false;
        while (matcher.find()) {
            if (matcher.start() != lastEnd) {
                throw new IllegalArgumentException("Invalid duration format: " + input);
            }
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid number in duration: " + input, ex);
            }
            total = Math.addExact(total, Math.multiplyExact(amount, unitMillis(matcher.group(2))));
            lastEnd = matcher.end();
            matched = true;
        }
        if (!matched || lastEnd != trimmed.length()) {
            throw new IllegalArgumentException("Invalid duration format: " + input);
        }
        return total;
    }

    public static String format(long millis) {
        if (millis < 0L) {
            return "Permanent";
        }
        if (millis < SECOND) {
            return "0 seconds";
        }

        long remaining = millis;
        long weeks = remaining / WEEK;
        remaining %= WEEK;
        long days = remaining / DAY;
        remaining %= DAY;
        long hours = remaining / HOUR;
        remaining %= HOUR;
        long minutes = remaining / MINUTE;
        remaining %= MINUTE;
        long seconds = remaining / SECOND;

        StringBuilder sb = new StringBuilder();
        appendUnit(sb, weeks, "week");
        appendUnit(sb, days, "day");
        appendUnit(sb, hours, "hour");
        appendUnit(sb, minutes, "minute");
        appendUnit(sb, seconds, "second");

        return sb.length() == 0 ? "0 seconds" : sb.toString();
    }

    public static String formatExpiry(long expiresAt) {
        if (expiresAt == -1L) {
            return "Never";
        }
        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0L) {
            return "Expired";
        }
        return format(remaining);
    }

    private static long unitMillis(String unit) {
        return switch (unit) {
            case "s" -> SECOND;
            case "m" -> MINUTE;
            case "h" -> HOUR;
            case "d" -> DAY;
            case "w" -> WEEK;
            case "mo" -> MONTH;
            default -> throw new IllegalArgumentException("Unknown duration unit: " + unit);
        };
    }

    private static void appendUnit(StringBuilder sb, long amount, String name) {
        if (amount <= 0L) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(amount).append(' ').append(name);
        if (amount != 1L) {
            sb.append('s');
        }
    }
}