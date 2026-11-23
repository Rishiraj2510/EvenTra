package com.eventra.certificate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, UUID> {
    Optional<Certificate> findByEventIdAndVolunteerId(UUID eventId, UUID volunteerId);
    boolean existsByEventIdAndVolunteerId(UUID eventId, UUID volunteerId);
}

