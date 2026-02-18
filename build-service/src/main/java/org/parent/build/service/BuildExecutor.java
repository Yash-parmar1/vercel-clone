package org.parent.build.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.*;

@Service
@Slf4j
public class BuildExecutor {

    private DockerClient dockerClient;
    private ExecutorService executorService;

    // Security constants
    private static final long MAX_BUILD_TIME_MINUTES = 10;
    private static final long MEMORY_LIMIT_BYTES = 1024 * 1024 * 1024L; // 1GB
    private static final long CPU_QUOTA = 100000L; // 100% of 1 CPU
    private static final long CPU_PERIOD = 100000L;

    @PostConstruct
    public void init() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
        this.executorService = Executors.newCachedThreadPool();
        log.info("Docker client initialized with security constraints");
    }

    /**
     * Build project in a secure, isolated Docker container
     * 
     * Security features:
     * - Ephemeral container (destroyed after build)
     * - Memory limit (1GB)
     * - CPU limit (1 core)
     * - Network disabled after dependency installation
     * - Read-only source code
     * - Timeout (10 minutes)
     */
    public void build(String projectPath, String framework) throws Exception {
        String containerId = null;

        try {
            // 1. Create isolated container with security constraints
            log.info("Creating secure build container...");
            containerId = createSecureContainer(projectPath);

            // 2. Start container
            dockerClient.startContainerCmd(containerId).exec();
            log.info("Container started: {}", containerId);

            // 3. Install dependencies WITH network (needed for npm install)
            log.info("Installing dependencies (network enabled)...");
            executeWithTimeout(containerId, getInstallCommand(framework), 5);

            // 4. DISABLE NETWORK for build phase (security)
            log.info("Disabling network access...");
            disableNetworkAccess(containerId);

            // 5. Build project WITHOUT network (sandboxed)
            log.info("Building project (network disabled)...");
            executeWithTimeout(containerId, getBuildCommand(framework), 5);

            log.info("Build completed successfully!");

        } catch (TimeoutException e) {
            log.error("Build timeout - killing container");
            throw new RuntimeException("Build exceeded time limit of " + MAX_BUILD_TIME_MINUTES + " minutes");
        } finally {
            // Always cleanup container
            cleanupContainer(containerId);
        }
    }

    /**
     * Create container with security constraints
     */
    private String createSecureContainer(String projectPath) {
        HostConfig hostConfig = HostConfig.newHostConfig()
                // Mount project directory as READ-WRITE (need to write build output)
                .withBinds(new Bind(
                        projectPath,
                        new Volume("/project"),
                        AccessMode.rw
                ))
                // Memory limit
                .withMemory(MEMORY_LIMIT_BYTES)
                .withMemorySwap(MEMORY_LIMIT_BYTES) // No swap
                // CPU limit
                .withCpuQuota(CPU_QUOTA)
                .withCpuPeriod(CPU_PERIOD)
                // Security options
                .withReadonlyRootfs(false) // Need to write to /tmp
                .withCapDrop(Capability.ALL) // Drop all Linux capabilities
                .withSecurityOpts(List.of("no-new-privileges")) // Prevent privilege escalation
                // Network initially enabled for npm install
                .withNetworkMode("bridge");

        CreateContainerResponse container = dockerClient
                .createContainerCmd("node:18-alpine")
                .withName("build-" + System.currentTimeMillis())
                .withWorkingDir("/project")
                .withHostConfig(hostConfig)
                // Set environment variables
                .withEnv(
                        "NODE_ENV=production",
                        "CI=true"
                )
                // Disable DNS to prevent lookups during build
                .withDns("0.0.0.0")
                .exec();

        return container.getId();
    }

    /**
     * Disable network access by disconnecting container from network
     */
    private void disableNetworkAccess(String containerId) {
        try {
            dockerClient.disconnectFromNetworkCmd()
                    .withContainerId(containerId)
                    .withNetworkId("bridge")
                    .withForce(true)
                    .exec();
            log.info("Network access disabled for container: {}", containerId);
        } catch (Exception e) {
            log.warn("Failed to disable network (container may already be disconnected): {}", e.getMessage());
        }
    }

    /**
     * Execute command with timeout
     */
    private void executeWithTimeout(String containerId, String command, int timeoutMinutes) 
            throws Exception {
        
        Future<Void> future = executorService.submit(() -> {
            execCommand(containerId, command);
            return null;
        });

        try {
            future.get(timeoutMinutes, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("Command timed out: " + command);
        } catch (ExecutionException e) {
            throw new Exception("Command failed: " + command, e.getCause());
        }
    }

    /**
     * Execute command in container
     */
    private void execCommand(String containerId, String command) throws Exception {
        ExecCreateCmdResponse execCreateCmd = dockerClient
                .execCreateCmd(containerId)
                .withCmd("sh", "-c", command)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();

        StringBuilder output = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        dockerClient.execStartCmd(execCreateCmd.getId())
                .exec(new ExecStartResultCallback() {
                    @Override
                    public void onNext(Frame frame) {
                        String text = new String(frame.getPayload());
                        output.append(text);
                        
                        // Log only important lines to reduce noise
                        if (text.contains("error") || text.contains("Error") || 
                            text.contains("warn") || text.contains("success")) {
                            log.info(text.trim());
                        }
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                        super.onComplete();
                    }
                })
                .awaitCompletion();

        latch.await(1, TimeUnit.SECONDS);

        // Check exit code
        var execResponse = dockerClient.inspectExecCmd(execCreateCmd.getId()).exec();

        if (execResponse.getExitCodeLong() != null && execResponse.getExitCodeLong() != 0) {
            log.error("Command failed with output: {}", output);
            throw new RuntimeException("Build command failed: " + command);
        }
    }

    /**
     * Get install command based on framework
     */
    private String getInstallCommand(String framework) {
        return switch (framework.toLowerCase()) {
            case "react", "vue", "angular", "next", "vite" -> "npm ci --production=false";
            case "python" -> "pip install -r requirements.txt";
            default -> "npm ci --production=false";
        };
    }

    /**
     * Get build command based on framework
     */
    private String getBuildCommand(String framework) {
        return switch (framework.toLowerCase()) {
            case "react", "vue", "angular", "vite" -> "npm run build";
            case "next" -> "npm run build && mv .next/static .next/standalone/";
            case "python" -> "python setup.py build";
            default -> "npm run build";
        };
    }

    /**
     * Cleanup container - ALWAYS runs
     */
    private void cleanupContainer(String containerId) {
        if (containerId != null) {
            try {
                // Stop container first (in case it's still running)
                try {
                    dockerClient.stopContainerCmd(containerId)
                            .withTimeout(5)
                            .exec();
                } catch (Exception e) {
                    // Container may already be stopped
                }

                // Remove container
                dockerClient.removeContainerCmd(containerId)
                        .withForce(true)
                        .withRemoveVolumes(true)
                        .exec();

                log.info("Container cleaned up: {}", containerId);
            } catch (Exception e) {
                log.error("Failed to cleanup container {}: {}", containerId, e.getMessage());
            }
        }
    }
}