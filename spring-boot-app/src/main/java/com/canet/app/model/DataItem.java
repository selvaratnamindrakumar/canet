package com.canet.app.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Example of a typed model for a fixed-schema API (jsonplaceholder todos).
 * The live service uses {@code Map<String, Object>} instead so the UI adapts
 * to any JSON schema (handsets, todos, products …) without code changes.
 * Keep this class if you need compile-time field validation for a known schema.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataItem {

    private int userId;
    private int id;
    private String title;
    private boolean completed;

    public DataItem() {}

    public DataItem(int userId, int id, String title, boolean completed) {
        this.userId = userId;
        this.id = id;
        this.title = title;
        this.completed = completed;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    @Override
    public String toString() {
        return "DataItem{id=" + id + ", userId=" + userId + ", title='" + title + "', completed=" + completed + "}";
    }
}
