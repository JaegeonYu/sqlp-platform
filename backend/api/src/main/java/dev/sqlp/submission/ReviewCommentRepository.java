package dev.sqlp.submission;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ReviewCommentRepository extends JpaRepository<ReviewComment, UUID> {

	List<ReviewComment> findBySubmissionIdOrderByCreatedAt(UUID submissionId);

	@Query("select c.submissionId, count(c) from ReviewComment c where c.submissionId in ?1 and c.deleted = false group by c.submissionId")
	List<Object[]> countBySubmissionIds(Collection<UUID> submissionIds);

}
