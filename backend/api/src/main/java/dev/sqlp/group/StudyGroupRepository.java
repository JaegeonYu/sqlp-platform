package dev.sqlp.group;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface StudyGroupRepository extends JpaRepository<StudyGroup, UUID> {

}
