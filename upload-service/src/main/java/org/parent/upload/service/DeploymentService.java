package org.parent.upload.service;

import org.parent.common.service.QueueService;
import org.parent.common.service.R2Service;
import org.parent.common.entity.Deployment;
import org.parent.common.entity.Project;
import org.parent.common.entity.User;
import org.parent.common.model.DeploymentStatus;
import org.parent.common.repository.JpaDeploymentRepository;
import org.parent.common.repository.ProjectRepository;
import org.parent.common.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class DeploymentService {

    @Autowired
    private R2Service r2Service;

    @Autowired
    private QueueService queueService;

    @Autowired
    private JpaDeploymentRepository deploymentRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    // Cache directory for cloned repos, keyed by repoUrl
    private static final Path REPO_CACHE_DIR = Path.of(System.getProperty("java.io.tmpdir"), "repo-cache");
    // Per-repo locks to avoid concurrent git operations on the same repo
    private final ConcurrentHashMap<String, ReentrantLock> repoLocks = new ConcurrentHashMap<>();

    /**
     * Clone or pull a git repository. Uses a local cache so repeated deploys
     * of the same repo only do a fast `git pull` instead of a full clone.
     * Returns a COPY of the repo (so the cache stays clean for next time).
     */
    private File cloneOrPull(String repoUrl, String deploymentId) throws Exception {
        // Deterministic cache key from the repo URL
        String cacheKey = repoUrl.replaceAll("[^a-zA-Z0-9._-]", "_");
        File cachedRepo = REPO_CACHE_DIR.resolve(cacheKey).toFile();

        ReentrantLock lock = repoLocks.computeIfAbsent(repoUrl, k -> new ReentrantLock());
        lock.lock();
        try {
            if (cachedRepo.exists() && new File(cachedRepo, ".git").exists()) {
                // Cached repo exists — do a fast pull
                log.info("[{}] Cached repo found, pulling latest changes...", deploymentId);
                try (Git git = Git.open(cachedRepo)) {
                    git.reset().setMode(ResetCommand.ResetType.HARD).call();
                    git.clean().setForce(true).setCleanDirectories(true).call();
                    git.pull().call();
                    log.info("[{}] Pull completed (fast update)", deploymentId);
                } catch (GitAPIException e) {
                    // If pull fails (e.g. force-pushed), delete cache and re-clone
                    log.warn("[{}] Pull failed ({}), re-cloning...", deploymentId, e.getMessage());
                    FileUtils.deleteDirectory(cachedRepo);
                    freshClone(repoUrl, cachedRepo, deploymentId);
                }
            } else {
                // No cache — full clone
                freshClone(repoUrl, cachedRepo, deploymentId);
            }
        } finally {
            lock.unlock();
        }

        // Copy cached repo to a temp directory for this deployment
        File deployDir = Files.createTempDirectory("deploy-" + deploymentId).toFile();
        FileUtils.copyDirectory(cachedRepo, deployDir);
        log.info("[{}] Copied cached repo to {}", deploymentId, deployDir.getAbsolutePath());
        return deployDir;
    }

    private void freshClone(String repoUrl, File targetDir, String deploymentId) throws GitAPIException {
        log.info("[{}] Cloning repository: {}", deploymentId, repoUrl);
        targetDir.getParentFile().mkdirs();
        Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(targetDir)
                .call();
        log.info("[{}] Clone completed", deploymentId);
    }

    @Async
    public void processDeployment(String repoUrl, String deploymentId, String projectId, String userId) {
        File tempDir = null;

        try {
            // Load User and Project
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            Project project = projectRepository.findByOwnerIdAndId(userId, projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            // Save deployment record immediately as QUEUED
            Deployment deployment = new Deployment();
            deployment.setId(deploymentId);
            deployment.setProject(project);
            deployment.setUser(user);
            deployment.setStatus(DeploymentStatus.QUEUED);
            deployment.setDeploymentUrl("http://" + deploymentId + ".vercel-clone.com");
            deployment.setS3SourcePath("raw/" + deploymentId);
            deploymentRepository.save(deployment);
            log.info("[{}] Deployment record created (QUEUED)", deploymentId);

            // 1. Clone or pull repository (cached)
            tempDir = cloneOrPull(repoUrl, deploymentId);

            // 2. Upload to R2
            log.info("[{}] Uploading to R2...", deploymentId);
            deployment.setStatus(DeploymentStatus.UPLOADING);
            deploymentRepository.save(deployment);

            r2Service.uploadDirectory(tempDir.getAbsolutePath(), "raw/" + deploymentId);
            log.info("[{}] Upload completed", deploymentId);

            deployment.setStatus(DeploymentStatus.UPLOADED);
            deploymentRepository.save(deployment);

            // 3. Add to build queue
            queueService.addToBuildQueue(deploymentId);
            log.info("[{}] Added to build queue", deploymentId);

        } catch (Exception e) {
            log.error("[{}] Deployment failed: {}", deploymentId, e.getMessage(), e);
            // Update deployment status to FAILED if record exists
            try {
                deploymentRepository.findById(deploymentId).ifPresent(d -> {
                    d.setStatus(DeploymentStatus.FAILED);
                    d.setErrorMessage(e.getMessage());
                    deploymentRepository.save(d);
                });
            } catch (Exception ex) {
                log.error("[{}] Failed to update deployment status: {}", deploymentId, ex.getMessage());
            }
        } finally {
            // Cleanup the deployment copy (NOT the cache)
            if (tempDir != null) {
                try {
                    FileUtils.deleteDirectory(tempDir);
                } catch (Exception e) {
                    log.warn("[{}] Failed to cleanup temp directory", deploymentId);
                }
            }
        }
    }
}