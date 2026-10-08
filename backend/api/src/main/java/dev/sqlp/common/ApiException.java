package dev.sqlp.common;

import org.springframework.http.HttpStatus;

/**
 * 클라이언트에 그대로 전달해도 되는 오류. message는 사용자에게 보여 줄 문장이며 내부 정보를 담지 않는다.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	public ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatus status() {
		return this.status;
	}

	public String code() {
		return this.code;
	}

	public static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "대상을 찾을 수 없습니다.");
	}

	public static ApiException forbidden() {
		return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다.");
	}

	/** 다른 사람이 먼저 저장한 내용을 덮어쓰려 할 때 */
	public static ApiException editConflict() {
		return new ApiException(HttpStatus.CONFLICT, "EDIT_CONFLICT",
				"다른 사람이 먼저 수정했습니다. 새로 불러온 뒤 다시 저장하세요.");
	}

	public static ApiException badRequest(String code, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message);
	}

}
