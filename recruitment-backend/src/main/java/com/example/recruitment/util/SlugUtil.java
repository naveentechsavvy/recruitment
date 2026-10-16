package com.example.recruitment.util;

import java.text.Normalizer;
import java.util.Locale;

/** Turns a client name into a URL-friendly slug: "Apple Inc." -> "apple-inc". */
public final class SlugUtil {

    private static final int MAX_LENGTH = 80;

    private SlugUtil() { }

    public static String slugify(String text) {
        String s = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")          // drop accents
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")      // anything else becomes a dash
                .replaceAll("^-+|-+$", "");         // no leading or trailing dashes
        if (s.length() > MAX_LENGTH) s = s.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        return s.isEmpty() ? "client" : s;
    }
}
