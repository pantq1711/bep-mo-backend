package com.bepmo.media.entity;

public enum MediaResourceType {
    IMAGE("image"),
    VIDEO("video");

    private final String cloudinaryValue;

    MediaResourceType(String cloudinaryValue) {
        this.cloudinaryValue = cloudinaryValue;
    }

    public String cloudinaryValue() {
        return cloudinaryValue;
    }
}
