package com.ees.eval.exception;

/**
 * 데이터 업데이트 시 버전(Version) 충돌이 발생할 경우 던져지는 커스텀 예외 클래스입니다.
 * 시스템의 낙관적 락(Optimistic Locking)을 보장하기 위해 사용됩니다.
 */
public class EesOptimisticLockException extends RuntimeException {

    /**
     * 예외 발생 시 메시지를 담아 생성합니다.
     *
     * @param message 예외 상세 설명
     */
    public EesOptimisticLockException(String message) {
        super(message);
    }
}
