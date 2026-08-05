package org.example.ai.model;

public class KnowledgeBase {
    private Long id;
    private String name;
    private String description;
    private int docCount;
    private String createdAt;
    private String updatedAt;
    private String uploadDir;

    public KnowledgeBase() {}

    public KnowledgeBase(Long id, String name, String description, int docCount,
                         String createdAt, String updatedAt, String uploadDir) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.docCount = docCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.uploadDir = uploadDir;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getDocCount() { return docCount; }
    public void setDocCount(int docCount) { this.docCount = docCount; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public String getUploadDir() { return uploadDir; }
    public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }
}