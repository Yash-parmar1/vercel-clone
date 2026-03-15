package org.parent.upload.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeployResponse {
    private String deploymentId;
    private String status;
    private String deploymentUrl;
}