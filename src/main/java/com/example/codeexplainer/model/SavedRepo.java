package com.example.codeexplainer.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "saved_repos")
public class SavedRepo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false)
    private String name;

    @Column(name = "saved_at")
    private LocalDateTime savedAt;

    public SavedRepo() {}

    public SavedRepo(String url, String name) {
        this.url = url;
        this.name = name;
        this.savedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getUrl() { return url; }
    public String getName() { return name; }
    public LocalDateTime getSavedAt() { return savedAt; }

    public void setId(Long id) { this.id = id; }
    public void setUrl(String url) { this.url = url; }
    public void setName(String name) { this.name = name; }
    public void setSavedAt(LocalDateTime savedAt) { this.savedAt = savedAt; }
}