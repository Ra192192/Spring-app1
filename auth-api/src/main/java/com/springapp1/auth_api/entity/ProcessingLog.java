package com.springapp1.auth_api.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processing_log")
public class ProcessingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "input_text", nullable = false, columnDefinition = "text")
    private String inputText;

    @Column(name = "output_text", nullable = false, columnDefinition = "text")
    private String outputText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProcessingLog() {
    }

    public ProcessingLog(UUID userId, String inputText, String outputText) {
        this.userId = userId;
        this.inputText = inputText;
        this.outputText = outputText;
        this.createdAt = Instant.now();
    }
}