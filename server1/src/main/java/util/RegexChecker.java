package util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * BufferedReader로 입력받은 문자열이 정규식에 일치하는지 확인하는 프로그램입니다.
 */
public class RegexChecker {
    private static final String REGEX_PATTERN = "^\\W$";


    public static void main(String[] args) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            String inputLine;

            System.out.println("확인할 정규식: " + REGEX_PATTERN);
            System.out.print("입력할 문자열 : ");

            while ((inputLine = br.readLine()) != null) {
                if (inputLine.isEmpty()) {
                    System.out.println("입력이 없어 프로그램을 종료합니다.");
                    break;
                }
                boolean isMatch = inputLine.matches(REGEX_PATTERN);
                System.out.println("입력된 문자열: \"" + inputLine + "\"");
                System.out.println("정규식 일치 여부: **" + isMatch + "**\n");
                System.out.println("다음 문자열을 입력하세요 (종료하려면 빈 줄 입력 후 Enter): ");
            }

        } catch (IOException e) {
            System.err.println("입력 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}