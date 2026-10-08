package dev.sqlp.cohort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CohortSessionRepository extends JpaRepository<CohortSession, UUID> {

	List<CohortSession> findByCohortId(UUID cohortId);

	Optional<CohortSession> findByCohortIdAndChapterId(UUID cohortId, UUID chapterId);

}
