package com.hyeja.policyservice.global.exception;

import com.hyeja.policyservice.global.apiPayload.ApiResponse;
import com.hyeja.policyservice.global.apiPayload.dto.ReasonDTO;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.Errors;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class ExceptionAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<Object> handleGeneralException(GeneralException exception) {
        ReasonDTO reason = exception.getErrorReason();
        return ResponseEntity.status(reason.getHttpStatus())
                .body(ApiResponse.onFailure(reason.getCode(), reason.getMessage(), exception.getResult()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        collectValidationErrors(exception.getBindingResult(), errors);
        return validationErrorResponse(exception, errors, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getParameterValidationResults().stream()
                .filter(ParameterErrors.class::isInstance)
                .map(ParameterErrors.class::cast)
                .forEach(parameterErrors -> collectValidationErrors(parameterErrors, errors));

        if (errors.isEmpty()) {
            return super.handleHandlerMethodValidationException(exception, headers, status, request);
        }
        return validationErrorResponse(exception, errors, headers, status, request);
    }

    private void collectValidationErrors(Errors validationErrors, Map<String, String> errors) {
        validationErrors.getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage() == null
                        ? "유효하지 않은 값입니다." : error.getDefaultMessage()));
        validationErrors.getGlobalErrors().forEach(error ->
                errors.putIfAbsent("_global", error.getDefaultMessage() == null
                        ? "입력값을 확인해 주세요." : error.getDefaultMessage()));
    }

    private ResponseEntity<Object> validationErrorResponse(
            Exception exception, Map<String, String> errors, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ErrorStatus error = ErrorStatus.VALIDATION_ERROR;
        return handleExceptionInternal(exception,
                ApiResponse.onFailure(error.getCode(), error.getMessage(), errors),
                headers, status, request);
    }

    // Spring MVC 예외도 기존 HTTP 상태와 헤더를 유지하면서 공통 응답으로 변환합니다.
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (body instanceof ApiResponse<?>) {
            return new ResponseEntity<>(body, headers, status);
        }
        ErrorStatus error = switch (status.value()) {
            case 400 -> ErrorStatus.BAD_REQUEST;
            case 401 -> ErrorStatus.UNAUTHORIZED;
            case 403 -> ErrorStatus.FORBIDDEN;
            case 404 -> ErrorStatus.NOT_FOUND;
            case 405 -> ErrorStatus.METHOD_NOT_ALLOWED;
            case 406 -> ErrorStatus.NOT_ACCEPTABLE;
            case 409 -> ErrorStatus.CONFLICT;
            case 415 -> ErrorStatus.UNSUPPORTED_MEDIA_TYPE;
            case 500 -> ErrorStatus.INTERNAL_SERVER_ERROR;
            default -> null;
        };
        ApiResponse<?> response = error == null
                ? ApiResponse.onFailure("HTTP_" + status.value(), "요청을 처리할 수 없습니다.", null)
                : ApiResponse.onFailure(error.getCode(), error.getMessage(), null);
        return new ResponseEntity<>(response, headers, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception exception) {
        log.error("처리하지 못한 서버 오류", exception);
        ErrorStatus error = ErrorStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(error.getHttpStatus())
                .body(ApiResponse.onFailure(error.getCode(), error.getMessage(), null));
    }
}
