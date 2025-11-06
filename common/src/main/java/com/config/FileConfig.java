package com.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 경로와 파일·폴더 이름을 다룰 때 쓰는 간단 유틸 모음.
 *
 * <p>목적: 사용자가 입력한 폴더/파일명이 상위 폴더로 튀어나가거나(../),
 * 이상한 문자를 넣어 문제가 생기지 않게 막아준다.</p>
 *
 * <h2>무엇을 하나요?</h2>
 * <ul>
 *   <li>{@link #sanitizeName(String)}: 폴더/파일 이름에서 위험한 것들(../, 개행 등) 제거</li>
 *   <li>{@link #resolveBase(Path, String)}: root 밑에 sub 폴더를 붙여 <b>기준 경로</b> 만들기 (벗어나면 root로)</li>
 *   <li>{@link #isSafeUnder(Path, Path)}: 어떤 경로가 기준 경로 안쪽인지 확인</li>
 *   <li>{@link #nvl(String, String)}: 비어 있으면 기본값으로 바꾸기</li>
 *   <li>{@link #enc(String)}: URL에 쓰기 좋게 UTF-8로 인코딩</li>
 * </ul>
 *
 * <h2>간단 예시</h2>
 * <pre>{@code
 * Path root = Path.of("/data");
 * String input = "../uploads//";
 *
 * String name = FileConfig.sanitizeName(input); // "uploads"
 * Path base = FileConfig.resolveBase(root, input); // "/data/uploads"
 *
 * Path save = base.resolve("pic.png");
 * if (!FileConfig.isSafeUnder(save, base)) {
 *     throw new SecurityException("폴더 밖으로 나가려는 경로입니다.");
 * }
 *
 * String folder = FileConfig.nvl(name, "default"); // 비었으면 "default"
 * String q = FileConfig.enc("한글 파일.png");      // URL 파라미터용
 * }</pre>
 */
public final class FileConfig {
    private FileConfig() {}

    /**
     * <ul>
     *   <li>역슬래시(\)를 슬래시(/)로 바꾼다.</li>
     *   <li>".."를 없애 상위 폴더로 빠져나가는 걸 막는다.</li>
     *   <li>개행 문자(\r, \n)를 없앤다.</li>
     *   <li>앞뒤의 슬래시(/)를 없앤다.</li>
     * </ul>
     *
     * <p>문자열만 다듬는다.
     * {@link #resolveBase(Path, String)}와 {@link #isSafeUnder(Path, Path)}로
     * 최종 경로 확인 필요</p>
     *
     * @param s 사용자 입력 이름(널 가능)
     * @return 정리된 이름. {@code null}이면 빈 문자열
     */
    public static String sanitizeName(String s) {
        if (s == null) return "";
        s = s.replace("\\", "/");
        s = s.replace("..", "");
        s = s.replaceAll("[\\r\\n]", "");
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/"))   s = s.substring(0, s.length() - 1);
        return s;
    }

    /**
     * 기준이 될 폴더를 만든다.
     * <ul>
     *   <li>{@code sub}가 있으면: {@code root/sub}로 만든다(이름은 {@link #sanitizeName(String)}로 정리).</li>
     *   <li>만든 경로가 {@code root} 바깥으로 벗어나면: 그냥 {@code root}로 되돌린다.</li>
     *   <li>{@code sub}가 없거나 비면: {@code root}를 그대로 쓴다.</li>
     * </ul>
     *
     * @param root 루트 폴더(반드시 있어야 함)
     * @param sub  하위 폴더 이름(널/공백 가능)
     * @return 안전한 기준 경로(항상 {@code root} 또는 그 하위)
     */
    public static Path resolveBase(Path root, String sub) {
        Path base = (sub != null && !sub.isBlank())
                ? root.resolve(sanitizeName(sub)).normalize()
                : root;
        if (!base.startsWith(root)) base = root;
        return base;
    }

    /**
     * 주어진 경로가 기준 경로 안쪽인지 확인한다.
     * <p>둘 다 절대 경로로 바꿔 비교하므로, 단순 문자열 꼼수(예: {@code ..})를 막을 수 있다.</p>
     *
     * @param candidate 확인할 경로
     * @param base 기준 경로
     * @return {@code true}면 안쪽, {@code false}면 바깥(또는 인자 중 널)
     */
    public static boolean isSafeUnder(Path candidate, Path base) {
        if (candidate == null || base == null) return false;
        return candidate.toAbsolutePath().normalize()
                .startsWith(base.toAbsolutePath().normalize());
    }

    /**
     * 문자열이 비어 있으면 기본값으로 바꿔 준다.
     *
     * @param s 검사할 문자열(널 가능)
     * @param def 기본값
     * @return s가 있으면 s, 없으면 def
     */
    public static String nvl(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }

    /**
     * URL에 쓰기 좋게 UTF-8로 인코딩한다.
     * <p>예) {@code "한글 파일.png"} → {@code %ED%95%9C%EA%B8%80%20%ED%8C%8C%EC%9D%BC.png}</p>
     *
     * @param s 인코딩할 문자열(널이면 예외 발생)
     * @return 인코딩된 문자열
     */
    public static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
