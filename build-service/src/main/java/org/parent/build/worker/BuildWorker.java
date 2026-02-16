package org.parent.build.worker;

import org.parent.build.service.BuildExecutor;
import org.parent.build.service.SecurityValidator;
import org.parent.common.service.QueueService;
import org.parent.common.service.R2Service;
import org.parent.common.repository.DeploymentRepository;
import org.parent.common.entity.Deployment;
import org.parent.common.model.DeploymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.time.Instant;

@Component
@Slf4j
public class BuildWorker {

    @Autowired
    private QueueService queueService;

    @Autowired
    private R2Service r2Service;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private BuildExecutor buildExecutor;

    @Autowired
    private SecurityValidator securityValidator;

    @PostConstruct
    public void startWorker() {
        new Thread(() -> {
            log.info("🔐 Secure build worker started");

            while (true) {
                try {
                    String deploymentId = queueService.getNextBuild();

                    if (deploymentId != null) {
                        log.info("[{}] 🚀 Processing build", deploymentId);
                        processBuild(deploymentId);
                    }

                } catch (Exception e) {
                    log.error("Build worker error: {}", e.getMessage(), e);
                }
            }
        }, "secure-build-worker").start();
    }

    private void processBuild(String deploymentId) {
        String tempDir = "/tmp/build-" + deploymentId;
        long startTime = System.currentTimeMillis();

        try {
            // Load Deployment to get Project and User
            Deployment deployment = deploymentRepository.findById(deploymentId)
                    .orElseThrow(() -> new RuntimeException("Deployment not found: " + deploymentId));

            // 1. Download from R2
            log.info("[{}] ⬇️  Downloading source code...", deploymentId);
            r2Service.downloadDirectory(deployment.getS3SourcePath(), tempDir);

            // 2. Mark BUILDING
            deployment.setStatus(DeploymentStatus.BUILDING);
            deploymentRepository.save(deployment);

            // 3. Security validation
            log.info("[{}] 🔒 Running security validation...", deploymentId);
            securityValidator.validateProject(tempDir);

            // 4. Detect framework
            String framework = detectFramework(tempDir);
            log.info("[{}] 🎯 Detected framework: {}", deploymentId, framework);

            // 5. Build in secure container
            log.info("[{}] 🔨 Building in isolated container...", deploymentId);
            buildExecutor.build(tempDir, framework);

            // 6. Upload built files
            String outputDir = getOutputDirectory(tempDir, framework);
            log.info("[{}] ⬆️  Uploading built files...", deploymentId);
            String s3BuildPath = "built/" + deploymentId;
            r2Service.uploadDirectory(outputDir, s3BuildPath);

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.info("[{}] ✅ Build completed in {}s", deploymentId, duration);

            // 7. Mark build success
            deployment.setStatus(DeploymentStatus.BUILD_SUCCESS);
            deployment.setCompletedAt(Instant.now());
            deployment.setBuildDurationSeconds(duration);
            deployment.setS3BuildPath(s3BuildPath);
            deploymentRepository.save(deployment);

        } catch (SecurityException e) {
            log.error("[{}] 🚫 Security validation failed: {}", deploymentId, e.getMessage());
            deploymentRepository.findById(deploymentId).ifPresent(d -> {
                d.setStatus(DeploymentStatus.BUILD_FAILED);
                d.setCompletedAt(Instant.now());
                d.setErrorMessage(e.getMessage());
                d.setBuildDurationSeconds((System.currentTimeMillis() - startTime) / 1000);
                deploymentRepository.save(d);
            });
        } catch (Exception e) {
            log.error("[{}] ❌ Build failed: {}", deploymentId, e.getMessage(), e);
            deploymentRepository.findById(deploymentId).ifPresent(d -> {
                d.setStatus(DeploymentStatus.BUILD_FAILED);
                d.setCompletedAt(Instant.now());
                d.setErrorMessage(e.getMessage());
                d.setBuildDurationSeconds((System.currentTimeMillis() - startTime) / 1000);
                deploymentRepository.save(d);
            });
        } finally {
            // Cleanup
            try {
                FileUtils.deleteDirectory(new File(tempDir));
            } catch (Exception e) {
                log.warn("[{}] Failed to cleanup temp directory", deploymentId);
            }
        }
    }

    private String detectFramework(String path) {
        File packageJson = new File(path, "package.json");
        if (packageJson.exists()) {
            // TODO: Parse package.json to detect React, Next.js, Vue, etc.
            return "react";
        }
        return "static";
    }

    private String getOutputDirectory(String basePath, String framework) {
        return switch (framework) {
            case "react", "vue" -> basePath + "/dist";
            case "vite" -> basePath + "/dist";
            case "next" -> basePath + "/.next/standalone";
            case "angular" -> basePath + "/dist";
            default -> basePath;
        };
    }
}