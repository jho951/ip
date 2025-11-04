package com.constant;
/**
 * @author YJH
 * 공통 에러 코드
 * code/string/message 제공
 */
public enum ErrorCode {

    /** 공통(시작 코드 : C)  */
    BAD_REQUEST("C001", "잘못된 요청입니다."),
    VALIDATION_ERROR("C002", "유효성 검증에 실패했습니다."),
    NOT_FOUND("C005", "대상을 찾을 수 없습니다."),
    CONFLICT("C006", "현재 상태와 충돌합니다."),
    INTERNAL_ERROR("C999", "내부 오류가 발생했습니다."),

    /** IP(시작 코드 : IP) */
    INVALID_IP_FORMAT("IP001", "IP 형식이 올바르지 않습니다."),
    ALLOW_FILE_NOT_FOUND("IP002", "허용 IP 파일을 찾을 수 없습니다."),
    USER_IP_NOT_FOUND("IP003", "접속 IP를 찾을 수 없습니다."),

    /** 환경설정(시작코드 : ENV) */
    ENV_VARIABLE_MISSING("ENV001", "필수 환경변수가 없습니다.");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
}
