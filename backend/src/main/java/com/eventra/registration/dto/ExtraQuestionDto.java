package com.eventra.registration.dto;

public class ExtraQuestionDto {

    private String id;
    private String label;

    public ExtraQuestionDto() {
    }

    public ExtraQuestionDto(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
