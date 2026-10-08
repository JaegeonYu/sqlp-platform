package dev.sqlp.course;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, UUID> {

	List<Course> findByOwnerGroupIdOrderByCreatedAtDesc(UUID ownerGroupId);

}
