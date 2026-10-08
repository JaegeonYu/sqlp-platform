package dev.sqlp.cohort;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CohortRepository extends JpaRepository<Cohort, UUID> {

	List<Cohort> findByGroupIdOrderByStartsOnDesc(UUID groupId);

}
