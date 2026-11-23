package com.eventra.feedback.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpsertFeedbackRequest {

    @NotNull(message = "rating is required")
    @Min(value = 1, message = "rating must be between 1 and 5")
    @Max(value = 5, message = "rating must be between 1 and 5")
    private Short rating;

    @Size(max = 300, message = "comment must not exceed 300 characters")
    private String comment;

    public UpsertFeedbackRequest() {
    }

    public UpsertFeedbackRequest(Short rating, String comment) {
        this.rating = rating;
        this.comment = comment;
    }

    public Short getRating() {
        return rating;
    }

    public void setRating(Short rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}

