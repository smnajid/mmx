package com.mmx.order.domain.service;

import java.util.regex.Pattern;

/** Derives acronym base from institution display name for code generation. */
public final class InstitutionCodeAcronym {

    private static final String FALLBACK = "INST";
    private static final Pattern SINGLE_WORD = Pattern.compile("^[A-Za-z0-9]{2,6}$");
    private static final Pattern WORD_SPLIT = Pattern.compile("[^A-Za-z0-9]+");

    private InstitutionCodeAcronym() {}

    public static String deriveAcronym(String displayName) {
        if (displayName == null) {
            return FALLBACK;
        }
        String normalized = displayName.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return FALLBACK;
        }
        if (!normalized.contains(" ")) {
            if (SINGLE_WORD.matcher(normalized).matches()) {
                return normalized.toUpperCase();
            }
            String letters = lettersOnly(normalized);
            if (!letters.isEmpty()) {
                return cap(letters.toUpperCase());
            }
            return FALLBACK;
        }
        String[] words = WORD_SPLIT.split(normalized);
        StringBuilder acronym = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            char first = Character.toUpperCase(word.charAt(0));
            if (Character.isLetterOrDigit(first)) {
                acronym.append(first);
            }
        }
        if (acronym.isEmpty()) {
            return FALLBACK;
        }
        return cap(acronym.toString());
    }

    private static String lettersOnly(String token) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String cap(String value) {
        return value.length() <= 6 ? value : value.substring(0, 6);
    }
}
