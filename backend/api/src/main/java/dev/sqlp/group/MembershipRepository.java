package dev.sqlp.group;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MembershipRepository extends JpaRepository<Membership, Membership.Key> {

	@Query("select m from Membership m where m.id.groupId = ?1 order by m.joinedAt")
	List<Membership> findByGroupId(UUID groupId);

	@Query("select m from Membership m where m.id.userId = ?1 order by m.joinedAt")
	List<Membership> findByUserId(UUID userId);

	@Query("select count(m) from Membership m where m.id.groupId = ?1")
	long countByGroupId(UUID groupId);

}
