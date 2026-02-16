package org.parent.upload.service;

import org.parent.common.service.QueueService;
import org.parent.common.service.R2Service;
import org.parent.common.entity.Deployment;
import org.parent.common.entity.Project;
import org.parent.common.entity.User;
import org.parent.common.model.DeploymentStatus;
import org.parent.common.repository.DeploymentRepository;
import org.parent.common.repository.ProjectRepository;
import org.parent.common.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@Slf4j
public class DeploymentService {

    @Autowired
    private R2Service r2Service;

    @Autowired
    private QueueService queueService;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Async
    public void processDeployment(String repoUrl, String deploymentId, String projectId, String userId) {
        String tempDir = "/tmp/" + deploymentId;

        try {
            // Load User and Project
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            Project project = projectRepository.findByOwnerIdAndId(userId, projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            // 1. Clone repository
            log.info("[{}] Cloning repository: {}", deploymentId, repoUrl);
            Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(new File(tempDir))
                    .call();

            log.info("[{}] Clone completed", deploymentId);

            // 2. Upload to R2
            log.info("[{}] Uploading to R2...", deploymentId);
            r2Service.uploadDirectory(tempDir, "raw/" + deploymentId);
            log.info("[{}] Upload completed", deploymentId);

            // Save initial deployment record (queued)
            Deployment deployment = new Deployment();
            deployment.setId(deploymentId);
            deployment.setProject(project);
            deployment.setUser(user);
            deployment.setStatus(DeploymentStatus.QUEUED);
            deployment.setDeploymentUrl("https://" + deploymentId + ".vercel-clone.com");
            deployment.setS3SourcePath("raw/" + deploymentId);
            deploymentRepository.save(deployment);

            // 3. Add to build queue
            queueService.addToBuildQueue(deploymentId);
            log.info("[{}] Added to build queue", deploymentId);

        } catch (Exception e) {
            log.error("[{}] Deployment failed: {}", deploymentId, e.getMessage(), e);
        } finally {
            // Cleanup
            try {
                FileUtils.deleteDirectory(new File(tempDir));
            } catch (Exception e) {
                log.warn("[{}] Failed to cleanup temp directory", deploymentId);
            }
        }
    }
}