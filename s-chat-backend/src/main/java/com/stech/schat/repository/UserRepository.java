package com.stech.schat.repository;

import com.stech.schat.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPublicId(String publicId);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPublicId(String publicId);

    // Used by login: accepts either a username or an email in one field
    Optional<User> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);
}
