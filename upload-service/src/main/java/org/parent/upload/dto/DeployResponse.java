package org.parent.upload.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeployResponse {
    private String id;
    private String status;
    private String url;
}