package com.justify.core;

import java.util.Locale;

/** UI language for questions and labels. The rendered justification is always English (D22). */
public enum Language {
    EN, AR;

    /** Parses a tag or an Accept-Language header ("ar", "ar-SA", "en-US,en;q=0.9"). Defaults to EN. */
    public static Language parse(String value) {
        if (value == null) {
            return EN;
        }
        String first = value.split(",")[0].strip().toLowerCase(Locale.ROOT);
        return first.startsWith("ar") ? AR : EN;
    }
}
