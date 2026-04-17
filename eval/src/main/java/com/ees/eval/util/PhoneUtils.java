package com.ees.eval.util;

/**
 * 전화번호 처리와 관련된 공통 유틸리티 메서드를 제공합니다.
 */
public final class PhoneUtils {

    private static final String PHONE_REGEX = "^010-\\d{4}-\\d{4}$";

    private PhoneUtils() { /* 유틸리티 클래스는 인스턴스화하지 않습니다. */ }

    /**
     * 전화번호가 '010-XXXX-XXXX' 형식인지 검증합니다.
     *
     * @param phone 검증할 전화번호 문자열
     * @throws IllegalArgumentException 형식이 올바르지 않을 경우 발생
     */
    public static String validate(String phone) {
        if (phone == null || !phone.matches(PHONE_REGEX)) {
            throw new IllegalArgumentException("전화번호 양식이 올바르지 않습니다. (예: 010-1111-2222)");
        }
        return phone;
    }

    /**
     * 숫자만 추출하여 '010-XXXX-XXXX' 형식으로 재포맷합니다.
     * 이미 올바른 형식이면 그대로 반환합니다.
     *
     * @param phone 원본 전화번호 문자열 (하이픈 포함/미포함 모두 허용)
     * @return 'XXX-XXXX-XXXX' 형식의 전화번호, 입력이 null이거나 빈 문자열이면 null 반환
     */
    public static String format(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 11) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
        }
        if (digits.length() == 10) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
        }
        return phone; // 변환 불가 시 원본 반환
    }
}
