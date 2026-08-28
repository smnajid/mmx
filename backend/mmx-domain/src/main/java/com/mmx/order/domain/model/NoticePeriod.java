package com.mmx.order.domain.model;

public enum NoticePeriod {
    _24H("24H"),
    _48H("48H");

    private final String code;

    NoticePeriod(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static java.util.Optional<NoticePeriod> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalized = code.trim();
        for (NoticePeriod np : values()) {
            if (np.code.equals(normalized)) {
                return java.util.Optional.of(np);
            }
        }
        return java.util.Optional.empty();
    }
}
