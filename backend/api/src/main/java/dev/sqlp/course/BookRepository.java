package dev.sqlp.course;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface BookRepository extends JpaRepository<Book, UUID> {

}
