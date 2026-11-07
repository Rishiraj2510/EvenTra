package com.eventra.registration;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegistrationFormRepository extends JpaRepository<RegistrationForm, UUID> {
    Optional<RegistrationForm> findByEventId(UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RegistrationForm> findWithLockByEventId(UUID eventId);
}

