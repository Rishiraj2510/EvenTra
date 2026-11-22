package com.eventra.plan;

import com.eventra.task.Task;
import com.eventra.volunteer.Volunteer;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assignments")
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "volunteer_id", nullable = false)
    private Volunteer volunteer;

    @Column(name = "slot_number", nullable = false)
    private int slotNumber;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal fit;

    @Column(name = "skill_score", nullable = false, precision = 6, scale = 3)
    private BigDecimal skillScore;

    @Column(name = "experience_score", nullable = false, precision = 6, scale = 3)
    private BigDecimal experienceScore;

    @Column(name = "performance_score", nullable = false, precision = 6, scale = 3)
    private BigDecimal performanceScore;

    @Column(name = "load_score", nullable = false, precision = 6, scale = 3)
    private BigDecimal loadScore;

    @Column(name = "interest_score", nullable = false, precision = 6, scale = 3)
    private BigDecimal interestScore;

    @Column(nullable = false)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> flags = new ArrayList<>();

    @Column(nullable = false)
    private boolean locked = false;

    public Assignment() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public Volunteer getVolunteer() {
        return volunteer;
    }

    public void setVolunteer(Volunteer volunteer) {
        this.volunteer = volunteer;
    }

    public int getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(int slotNumber) {
        this.slotNumber = slotNumber;
    }

    public BigDecimal getFit() {
        return fit;
    }

    public void setFit(BigDecimal fit) {
        this.fit = fit;
    }

    public BigDecimal getSkillScore() {
        return skillScore;
    }

    public void setSkillScore(BigDecimal skillScore) {
        this.skillScore = skillScore;
    }

    public BigDecimal getExperienceScore() {
        return experienceScore;
    }

    public void setExperienceScore(BigDecimal experienceScore) {
        this.experienceScore = experienceScore;
    }

    public BigDecimal getPerformanceScore() {
        return performanceScore;
    }

    public void setPerformanceScore(BigDecimal performanceScore) {
        this.performanceScore = performanceScore;
    }

    public BigDecimal getLoadScore() {
        return loadScore;
    }

    public void setLoadScore(BigDecimal loadScore) {
        this.loadScore = loadScore;
    }

    public BigDecimal getInterestScore() {
        return interestScore;
    }

    public void setInterestScore(BigDecimal interestScore) {
        this.interestScore = interestScore;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getFlags() {
        return flags;
    }

    public void setFlags(List<String> flags) {
        this.flags = flags;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Assignment that = (Assignment) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
