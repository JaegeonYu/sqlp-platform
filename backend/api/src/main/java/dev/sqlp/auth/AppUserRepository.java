package dev.sqlp.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	@Query("select u from AppUser u where lower(u.email) = lower(?1)")
	Optional<AppUser> findByEmail(String email);

	List<AppUser> findByStatusOrderByCreatedAtAsc(UserStatus status);

	List<AppUser> findAllByOrderByCreatedAtAsc();

	boolean existsBySystemRole(SystemRole systemRole);

}
