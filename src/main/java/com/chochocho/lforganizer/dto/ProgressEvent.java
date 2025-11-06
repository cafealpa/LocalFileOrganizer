package com.chochocho.lforganizer.dto;

public record ProgressEvent(
        String level,
        String message
) {

    public static final String CONNECT = "CONNECT";
    public static final String INFO = "INFO";
    public static final String WARN = "WARN";
    public static final String ERROR = "ERROR";
    public static final String COMPLETE = "COMPLETE";

    /**
     * 연결 성공 이벤트를 생성합니다.
     */
    public static ProgressEvent connect(String message) {
        return new ProgressEvent(CONNECT, message);
    }

    /**
     * 일반 정보 이벤트를 생성합니다.
     */
    public static ProgressEvent info(String message) {
        return new ProgressEvent(INFO, message);
    }

    /**
     * 경고 이벤트를 생성합니다.
     */
    public static ProgressEvent warn(String message) {
        return new ProgressEvent(WARN, message);
    }

    /**
     * 오류 이벤트를 생성합니다.
     */
    public static ProgressEvent error(String message) {
        return new ProgressEvent(ERROR, message);
    }

    /**
     * 작업 완료 이벤트를 생성합니다.
     */
    public static ProgressEvent complete(String message) {
        return new ProgressEvent(COMPLETE, message);
    }
}