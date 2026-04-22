package com.skku.milkyway.api.traffic.support;

/**
 * 서울시 교통량 Open API 호출 또는 파싱 중 발생한 예외를 감싸는 런타임 예외.
 */
public class TrafficApiException extends RuntimeException {

    public TrafficApiException(String message) {
        super(message);
    }

    public TrafficApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
