package org.parent.build.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final ObjectMapper objectMapper = new ObjectMapper();

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
        // Use the OS temp directory so the path is valid on both Windows and Linux.
        // On Windows, hardcoding /tmp does not exist as a native path, causing Docker
        // bind mounts to silently mount as empty directories inside the container.
        String tempBase = System.getProperty("java.io.tmpdir").replace("\\", "/");
        if (!tempBase.endsWith("/")) tempBase += "/";
        String tempDir = tempBase + "build-" + deploymentId;

        long startTime = System.currentTimeMillis();

        try {
            // Load Deployment to get Project and User
            Deployment deployment = deploymentRepository.findById(deploymentId)
                    .orElseThrow(() -> new RuntimeException("Deployment not found: " + deploymentId));

            // 1. Download from R2
            log.info("[{}] ⬇️  Downloading source code to {}...", deploymentId, tempDir);
            r2Service.downloadDirectory(deployment.getS3SourcePath(), tempDir);

            // 2. Mark BUILDING
            deployment.setStatus(DeploymentStatus.BUILDING);
            deploymentRepository.save(deployment);

            // 3. Security validation
            log.info("[{}] 🔒 Running security validation...", deploymentId);
            securityValidator.validateProject(tempDir);

            // 4. Detect framework from actual project files
            String framework = detectFramework(tempDir);
            log.info("[{}] 🎯 Detected framework: {}", deploymentId, framework);

            // 5. Build in secure container
            log.info("[{}] 🔨 Building in isolated container...", deploymentId);
            buildExecutor.build(tempDir, framework);

            // 6. Upload built files
            String outputDir = getOutputDirectory(tempDir, framework);
            log.info("[{}] ⬆️  Uploading built files from: {}", deploymentId, outputDir);
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
            markFailed(deploymentId, e.getMessage(), startTime);
        } catch (Exception e) {
            log.error("[{}] ❌ Build failed: {}", deploymentId, e.getMessage(), e);
            markFailed(deploymentId, e.getMessage(), startTime);
        } finally {
            try {
                FileUtils.deleteDirectory(new File(tempDir));
            } catch (Exception e) {
                log.warn("[{}] Failed to cleanup temp directory: {}", deploymentId, tempDir);
            }
        }
    }

    /**
     * Detect the framework by inspecting actual project files.
     *
     * Detection order:
     * 1. requirements.txt / setup.py / pyproject.toml  → python
     * 2. package.json dependencies                      → next, vite, angular, vue, react (priority order)
     * 3. package.json present but no known framework    → node (generic)
     * 4. index.html only                                → static
     */
    private String detectFramework(String path) {
        // Python
        if (new File(path, "requirements.txt").exists() ||
            new File(path, "setup.py").exists() ||
            new File(path, "pyproject.toml").exists()) {
            return "python";
        }

        // Node-based: parse package.json dependencies
        File packageJson = new File(path, "package.json");
        if (packageJson.exists()) {
            try {
                JsonNode root    = objectMapper.readTree(packageJson);
                JsonNode deps    = root.path("dependencies");
                JsonNode devDeps = root.path("devDependencies");

                // Next must be checked before React — Next projects also have "react" in deps
                if (hasKey(deps, "next")           || hasKey(devDeps, "next"))           return "next";
                if (hasKey(deps, "vite")           || hasKey(devDeps, "vite"))           return "vite";
                if (hasKey(deps, "@angular/core")  || hasKey(devDeps, "@angular/core"))  return "angular";
                if (hasKey(deps, "vue")            || hasKey(devDeps, "vue"))            return "vue";
                if (hasKey(deps, "react")          || hasKey(devDeps, "react"))          return "react";

                return "node"; // has package.json but no recognised framework
            } catch (Exception e) {
                log.warn("[detectFramework] Failed to parse package.json at {}: {}", path, e.getMessage());
                return "node";
            }
        }

        // Static site fallback
        if (new File(path, "index.html").exists()) {
            return "static";
        }

        log.warn("[detectFramework] Could not detect framework in {}, defaulting to node", path);
        return "node";
    }

    private boolean hasKey(JsonNode node, String key) {
        return node != null && !node.isMissingNode() && node.has(key);
    }

    private String getOutputDirectory(String basePath, String framework) {
        return switch (framework) {
            case "next"                     -> basePath + "/.next/standalone";
            case "angular"                  -> basePath + "/dist";
            case "react", "vue", "vite"     -> basePath + "/dist";
            case "python"                   -> basePath + "/dist";
            case "static"                   -> basePath;
            default                         -> basePath + "/dist";
        };
    }

    private void markFailed(String deploymentId, String errorMessage, long startTime) {
        deploymentRepository.findById(deploymentId).ifPresent(d -> {
            d.setStatus(DeploymentStatus.BUILD_FAILED);
            d.setCompletedAt(Instant.now());
            d.setErrorMessage(errorMessage);
            d.setBuildDurationSeconds((System.currentTimeMillis() - startTime) / 1000);
            deploymentRepository.save(d);
        });
    }
}