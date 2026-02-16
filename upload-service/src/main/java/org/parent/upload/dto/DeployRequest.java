package org.parent.upload.dto;

import lombok.Data;

@Data
public class DeployRequest {
    private String projectId;
    private String repoUrl;
}