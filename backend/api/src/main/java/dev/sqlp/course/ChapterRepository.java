package dev.sqlp.course;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

	List<Chapter> findByCourseVersionIdOrderByPosition(UUID courseVersionId);

	long countByCourseVersionId(UUID courseVersionId);

}
