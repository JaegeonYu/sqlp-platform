package dev.sqlp.common;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 모든 오류를 {code, message} 형태로 응답한다. 예상하지 못한 예외는 내용을 숨기고 로그로만 남긴다.
 */
@RestControllerAdvice
public class ApiErrorHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<Map<String, String>> handleApi(ApiException ex) {
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.status());
		if (ex instanceof RateLimitedException limited) {
			builder.header(HttpHeaders.RETRY_AFTER, Long.toString(limited.retryAfterSeconds()));
		}
		return builder.body(body(ex.code(), ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.findFirst()
			.map((error) -> error.getField() + ": " + error.getDefaultMessage())
			.orElse("입력값이 올바르지 않습니다.");
		return ResponseEntity.badRequest().body(body("VALIDATION", message));
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	ResponseEntity<Map<String, String>> handleUnreadable(Exception ex) {
		return ResponseEntity.badRequest().body(body("VALIDATION", "요청 형식이 올바르지 않습니다."));
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ResponseEntity<Map<String, String>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
		ApiException conflict = ApiException.editConflict();
		return ResponseEntity.status(conflict.status()).body(body(conflict.code(), conflict.getMessage()));
	}

	/** 동시 요청이 같은 유니크 키를 만들려 한 경우 등 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Map<String, String>> handleIntegrity(DataIntegrityViolationException ex) {
		log.warn("데이터 무결성 위반: {}", ex.getMostSpecificCause().getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(body("CONFLICT", "동시에 같은 요청이 처리되었습니다. 새로 불러온 뒤 다시 시도하세요."));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) throws Exception {
		if (ex instanceof ErrorResponse) {
			// 404·405 등 Spring MVC가 이미 상태 코드를 정한 예외는 기본 처리에 맡긴다
			throw ex;
		}
		log.error("처리되지 않은 예외", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(body("INTERNAL", "일시적인 오류가 발생했습니다."));
	}

	private static Map<String, String> body(String code, String message) {
		return Map.of("code", code, "message", message);
	}

}
