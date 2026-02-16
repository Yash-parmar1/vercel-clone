package org.parent.upload.controllers;

import org.parent.upload.dto.DeployRequest;
import org.parent.upload.dto.DeployResponse;
import org.parent.upload.service.DeploymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@Slf4j
public class DeployController {

    @Autowired
    private DeploymentService deploymentService;

    @PostMapping("/deploy")
    public ResponseEntity<?> deploy(@RequestBody DeployRequest request, Authentication auth) {
        try {
            if (request.getProjectId() == null || request.getProjectId().isEmpty()) {
                return ResponseEntity.badRequest().body("projectId is required");
            }
            if (request.getRepoUrl() == null || request.getRepoUrl().isEmpty()) {
                return ResponseEntity.badRequest().body("repoUrl is required");
            }

            String userId = auth.getName();
            String deploymentId = UUID.randomUUID().toString().substring(0, 8);

            log.info("Received deployment request: {} -> {} for user: {}", request.getRepoUrl(), deploymentId, userId);

            // Start async processing with full context
            deploymentService.processDeployment(request.getRepoUrl(), deploymentId, request.getProjectId(), userId);

            return ResponseEntity.ok(new DeployResponse(
                    deploymentId,
                    "queued",
                    "https://" + deploymentId + ".vercel-clone.com"
            ));
        } catch (Exception e) {
            log.error("Deploy request failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Deployment failed: " + e.getMessage());
        }
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}