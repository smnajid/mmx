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
}
