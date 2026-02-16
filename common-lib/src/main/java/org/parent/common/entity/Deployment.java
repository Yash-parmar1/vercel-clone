package org.parent.common.entity;

import lombok.Data;
import org.parent.common.model.DeploymentStatus;

import jakarta.persistence.*;

import java.time.Instant;

@Data
@Entity
@Table(name = "deployments")
public class Deployment {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeploymentStatus status;  // QUEUED, BUILDING, SUCCESS, FAILED

    @Column(nullable = false)
    private String deploymentUrl;

    @Column(name = "s3_source_path")
    private String s3SourcePath;

    @Column(name = "s3_build_path")
    private String s3BuildPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "build_duration_seconds")
    private Long buildDurationSeconds;

    @Column(length = 2000)
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
