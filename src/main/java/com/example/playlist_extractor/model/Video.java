package com.example.playlist_extractor.model;

public class Video { // 
    private String title;
    private String id;
    private String link;

    // Construtor, getters e setters
    public Video(String title, String id) {
        this.title = title;
        this.id = id;
        this.link = "https://www.youtube.com/watch?v=" + id;
    }

    public String getTitle() {
        return title;
    }

    public String getId() {
        return id;
    }

    public String getLink() {
        return link;
    }

    @Override
    public String toString() {
        return String.format("('%s', '%s', '%s')", title, id, link);
    }
}