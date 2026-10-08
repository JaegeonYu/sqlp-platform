package dev.sqlp.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

	Optional<UserIdentity> findByProviderAndSubject(String provider, String subject);

}
