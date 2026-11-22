package com.eventra.plan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    @Query("SELECT a FROM Assignment a " +
           "JOIN FETCH a.task t " +
           "JOIN FETCH a.volunteer v " +
           "WHERE a.plan.id = :planId " +
           "ORDER BY t.taskDate ASC, t.startTime ASC, t.title ASC, a.slotNumber ASC, a.id ASC")
    List<Assignment> findByPlanIdWithDetailsOrderByTaskDetails(@Param("planId") UUID planId);

    List<Assignment> findByPlanId(UUID planId);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Assignment a WHERE a.plan.id = :planId")
    void deleteByPlanId(@Param("planId") UUID planId);
}
