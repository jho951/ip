package com.exception;

import com.constant.ErrorCode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 서비스 전역에서 던지는 런타임 예외.
 * - ErrorCode 필수
 * - detail: 사용자, 개발자 메시지
 * - context: 디버깅용 key-value
 */
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;
    private final Map<String, Object> context;

    public AppException(ErrorCode errorCode) {
        this(errorCode, null, null, null);
    }

    public AppException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null, null);
    }

    public AppException(ErrorCode errorCode, String detail, Throwable cause) {
        this(errorCode, detail, cause, null);
    }

    public AppException(ErrorCode errorCode, String detail, Throwable cause, Map<String, ?> context) {
        super(detail == null || detail.isBlank() ? errorCode.defaultMessage() : detail, cause);
        if (errorCode == null) throw new IllegalArgumentException("에러코드가 없습니다.");
        this.errorCode = errorCode;
        this.detail = detail;
        this.context = context == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public String getDetail() { return detail; }
    public Map<String, Object> getContext() { return context; }

    public static AppException of(ErrorCode code) { return new AppException(code); }
    public static AppException of(ErrorCode code, String detail) { return new AppException(code, detail); }
    public static AppException of(ErrorCode code, String detail, Throwable cause) { return new AppException(code, detail, cause); }
    public static AppException of(ErrorCode code, String detail, Map<String, ?> ctx) { return new AppException(code, detail, null, ctx); }
}
