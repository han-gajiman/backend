package com.hangajiman.global.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.hangajiman.global.response.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return toResponse(e.getErrorCode(), e.getMessage());
    }

    // 클라이언트 요청이 잘못된 경우 (값 검증 실패, JSON 형식·Content-Type 오류, 파라미터 누락·타입 오류, 지원하지 않는 메서드)
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpRequestMethodNotSupportedException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidInput(Exception e) {
        return toResponse(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage());
    }

    // 없는 주소
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
        return toResponse(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return toResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.name(), message));
    }
}
