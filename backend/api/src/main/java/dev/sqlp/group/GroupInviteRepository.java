package dev.sqlp.group;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface GroupInviteRepository extends JpaRepository<GroupInvite, UUID> {

	Optional<GroupInvite> findByTokenHash(String tokenHash);

	/** 사용 횟수를 원자적으로 늘리기 위해 행을 잠근다 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from GroupInvite i where i.tokenHash = ?1")
	Optional<GroupInvite> findByTokenHashForUpdate(String tokenHash);

	List<GroupInvite> findByGroupIdOrderByCreatedAtDesc(UUID groupId);

}
