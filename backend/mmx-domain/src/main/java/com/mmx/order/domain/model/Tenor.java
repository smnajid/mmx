package com.mmx.order.domain.model;

public enum Tenor {
    _1W("1W"),
    _2W("2W"),
    _1M("1M"),
    _3M("3M"),
    _6M("6M"),
    _1Y("1Y");

    private final String code;

    Tenor(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static java.util.Optional<Tenor> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalized = code.trim();
        for (Tenor tenor : values()) {
            if (tenor.code.equals(normalized)) {
                return java.util.Optional.of(tenor);
            }
        }
        return java.util.Optional.empty();
    }
}
