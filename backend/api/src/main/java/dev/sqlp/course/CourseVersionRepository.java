package dev.sqlp.course;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseVersionRepository extends JpaRepository<CourseVersion, UUID> {

	Optional<CourseVersion> findFirstByCourseIdOrderByVersionNoDesc(UUID courseId);

}
