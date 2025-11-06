package com.constant;

import java.net.URLConnection;
import java.util.Map;
import java.util.Set;

/**
 * 확장자→MIME 매핑과 텍스트 MIME 판별 유틸.
 * Java 17 기준. 확장자는 항상 소문자로 비교합니다(예: ".json").
 */
public final class MimeConst {
    private MimeConst() {}

    /** 자주 누락되는 확장자 보강 맵(확장자는 소문자, dot 포함) */
    public static final Map<String, String> EXT_TO_MIME = Map.ofEntries(
            Map.entry(".txt",  "text/plain"),
            Map.entry(".log",  "text/plain"),
            Map.entry(".csv",  "text/csv"),
            Map.entry(".json", "application/json"),
            Map.entry(".xml",  "application/xml"),
            Map.entry(".js",   "application/javascript"),
            Map.entry(".css",  "text/css"),
            Map.entry(".svg",  "image/svg+xml"),
            Map.entry(".md",   "text/markdown"),
            Map.entry(".yaml", "application/x-yaml"),
            Map.entry(".yml",  "application/x-yaml"),
            Map.entry(".pdf",  "application/pdf"),
            Map.entry(".png",  "image/png"),
            Map.entry(".jpg",  "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".gif",  "image/gif"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".ico",  "image/x-icon")
    );

    /** 텍스트로 취급할 MIME(접두 text/* 외 추가) */
    public static final Set<String> TEXTUAL_MIME_EXTRA = Set.of(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/sql",
            "application/yaml",
            "application/x-yaml",
            "image/svg+xml" // SVG는 텍스트 기반
    );

    /**
     * 파일명으로 MIME을 추측 (확장자 기반).
     * @param filename 파일명(널/공백 허용)
     * @return 매핑되면 MIME, 아니면 null
     */
    public static String guessByName(String filename) {
        if (filename == null || filename.isBlank()) return null;

        // 1) JDK 내장 추정 (확장자 기반)
        String guessed = URLConnection.guessContentTypeFromName(filename);
        if (guessed != null) return guessed;

        // 2) 로컬 보강 매핑
        String lower = filename.toLowerCase();
        for (Map.Entry<String, String> e : EXT_TO_MIME.entrySet()) {
            if (lower.endsWith(e.getKey())) return e.getValue();
        }
        return null;
        // 필요하면 기본값 "application/octet-stream"은 호출부에서 처리
    }

    /**
     * MIME이 텍스트 계열인지 판정.
     * @param mime MIME 문자열
     * @return true=텍스트로 처리(UTF-8 권장)
     */
    public static boolean isTextual(String mime) {
        if (mime == null) return false;
        if (mime.startsWith("text/")) return true;
        return TEXTUAL_MIME_EXTRA.contains(mime);
    }
}
