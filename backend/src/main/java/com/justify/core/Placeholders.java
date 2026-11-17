package com.justify.core;

import java.util.Locale;
import java.util.Set;

/** Values that look filled in but carry no information. They count as missing. */
public final class Placeholders {

    private static final Set<String> VALUES = Set.of(
            "", "n/a", "na", "none", "null", "nil", "unknown", "not provided", "not specified", "tbd", "tba",
            "-", "--", "?", "...", "not mentioned", "unspecified",
            "غير محدد", "غير معروف", "لا يوجد", "لايوجد", "غير متوفر", "غير مذكور");

    private Placeholders() {
    }

    public static boolean isMissing(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1).strip();
        }
        return VALUES.contains(normalized);
    }
}
