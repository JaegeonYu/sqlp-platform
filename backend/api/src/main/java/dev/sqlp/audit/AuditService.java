package dev.sqlp.audit;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 보안 관련 이벤트 기록. 호출한 트랜잭션이 롤백돼도 남도록 별도 트랜잭션으로 저장한다.
 */
@Service
public class AuditService {

	private final AuditLogRepository repository;

	public AuditService(AuditLogRepository repository) {
		this.repository = repository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(UUID actorId, String action, Object target, String detail) {
		String targetText = (target != null) ? target.toString() : null;
		this.repository.save(new AuditLog(actorId, action, targetText, currentIp(), truncate(detail)));
	}

	private static String currentIp() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			return attributes.getRequest().getRemoteAddr();
		}
		return null;
	}

	private static String truncate(String value) {
		return (value != null && value.length() > 500) ? value.substring(0, 500) : value;
	}

}
