package com.constant;

public enum AttributeKeys {
    ALLOWED("ip.allowed"),
    REASON("ip.reason"),
    CLIENT("ip.client");

    private final String key;

    AttributeKeys(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}