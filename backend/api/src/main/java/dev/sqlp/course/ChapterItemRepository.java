package dev.sqlp.course;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChapterItemRepository extends JpaRepository<ChapterItem, UUID> {

	List<ChapterItem> findByChapterIdOrderByPosition(UUID chapterId);

	List<ChapterItem> findByChapterIdInOrderByPosition(Collection<UUID> chapterIds);

}
