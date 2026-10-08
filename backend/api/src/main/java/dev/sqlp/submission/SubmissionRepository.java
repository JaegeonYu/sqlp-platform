package dev.sqlp.submission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SubmissionRepository extends JpaRepository<Submission, UUID> {

	Optional<Submission> findByCohortIdAndItemIdAndAuthorId(UUID cohortId, UUID itemId, UUID authorId);

	List<Submission> findByCohortIdAndItemId(UUID cohortId, UUID itemId);

	List<Submission> findByCohortId(UUID cohortId);

}
