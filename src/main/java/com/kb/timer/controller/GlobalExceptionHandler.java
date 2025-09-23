package com.kb.timer.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 글로벌 예외 처리 핸들러
 * REST API에서 발생하는 예외들을 일관성 있게 처리
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 유효성 검증 실패 예외 처리
     * 
     * @param ex 유효성 검증 예외
     * @return 에러 응답
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidationException(WebExchangeBindException ex) {
        log.warn("유효성 검증 실패: {}", ex.getMessage());
        
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });
        
        Map<String, Object> errorResponse = Map.of(
                "error", "VALIDATION_FAILED",
                "message", "입력값 유효성 검증에 실패했습니다",
                "fieldErrors", fieldErrors,
                "timestamp", Instant.now().toString()
        );
        
        return Mono.just(ResponseEntity.badRequest().body(errorResponse));
    }

    /**
     * 일반적인 런타임 예외 처리
     * 
     * @param ex 런타임 예외
     * @return 에러 응답
     */
    @ExceptionHandler(RuntimeException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleRuntimeException(RuntimeException ex) {
        log.error("런타임 예외 발생: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = Map.of(
                "error", "INTERNAL_ERROR",
                "message", "서버 내부 오류가 발생했습니다",
                "details", ex.getMessage(),
                "timestamp", Instant.now().toString()
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
    }

    /**
     * IllegalArgumentException 처리
     * 
     * @param ex 잘못된 인수 예외
     * @return 에러 응답
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("잘못된 인수: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = Map.of(
                "error", "BAD_REQUEST",
                "message", "잘못된 요청입니다",
                "details", ex.getMessage(),
                "timestamp", Instant.now().toString()
        );
        
        return Mono.just(ResponseEntity.badRequest().body(errorResponse));
    }

    /**
     * 모든 예외에 대한 기본 처리
     * 
     * @param ex 예외
     * @return 에러 응답
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGenericException(Exception ex) {
        log.error("예상치 못한 예외 발생: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = Map.of(
                "error", "UNKNOWN_ERROR",
                "message", "예상치 못한 오류가 발생했습니다",
                "timestamp", Instant.now().toString()
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
    }
}
