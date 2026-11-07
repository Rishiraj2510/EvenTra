package com.eventra.volunteer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VolunteerRepository extends JpaRepository<Volunteer, UUID> {
    List<Volunteer> findByEventId(UUID eventId);
    Optional<Volunteer> findByEventIdAndUserId(UUID eventId, UUID userId);
    boolean existsByEventIdAndEmailIgnoreCase(UUID eventId, String email);
    long countByEventId(UUID eventId);
    List<Volunteer> findByUserId(UUID userId);
}

