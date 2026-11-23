package com.eventra.feedback;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    Optional<Feedback> findByAssignmentId(UUID assignmentId);

    List<Feedback> findByAssignmentIdIn(Collection<UUID> ids);
}

