package com.constant;

public enum HeaderKeys {
    ALLOWED("X-Ip-Allowed"),
    REASON("X-Ip-Reason");

    private final String key;

    HeaderKeys(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}