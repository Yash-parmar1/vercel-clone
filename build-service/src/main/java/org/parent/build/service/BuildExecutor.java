package org.parent.build.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

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

    @org.springframework.beans.factory.annotation.Value("${docker.host:#{null}}")
    private String configuredDockerHost;

    @PostConstruct
    public void init() {
        String dockerHost = resolveDockerHost();
        log.info("Connecting to Docker at: {}", dockerHost);

        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();
        log.info("Config resolved dockerHost to: {}", config.getDockerHost());

        ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .build();

        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
        this.executorService = Executors.newCachedThreadPool();
        log.info("Docker client initialized with host: {}", config.getDockerHost());

        try {
            dockerClient.pingCmd().exec();
            log.info("Docker ping successful - connection verified.");
        } catch (Exception e) {
            log.error("Docker ping failed! Host='{}'. Error: {}", dockerHost, e.getMessage());
        }
    }

    private String resolveDockerHost() {
        if (configuredDockerHost != null && !configuredDockerHost.isBlank()) {
            log.info("Using docker.host from application config: {}", configuredDockerHost);
            return configuredDockerHost;
        }
        String envHost = System.getenv("DOCKER_HOST");
        if (envHost != null && !envHost.isBlank()) {
            log.info("Using DOCKER_HOST from environment: {}", envHost);
            return envHost;
        }
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            String desktopPipe = "npipe:////./pipe/docker_engine";
            log.info("Windows detected, using Docker Desktop pipe: {}", desktopPipe);
            return desktopPipe;
        }
        return "unix:///var/run/docker.sock";
    }

    private String getDockerImage(String framework) {
        return switch (framework.toLowerCase()) {
            case "python"                                       -> "python:3.11-slim";
            case "react", "vue", "angular", "next", "vite",
                 "node"                                         -> "node:18-alpine";
            case "static"                                       -> "node:18-alpine";
            default -> {
                log.warn("Unknown framework '{}', falling back to node:18-alpine", framework);
                yield "node:18-alpine";
            }
        };
    }

    private void pullImageIfMissing(String image) {
        try {
            dockerClient.inspectImageCmd(image).exec();
            log.info("Image already present locally: {}", image);
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.info("Image not found locally, pulling: {} ...", image);
            try {
                dockerClient.pullImageCmd(image)
                        .exec(new PullImageResultCallback())
                        .awaitCompletion(5, TimeUnit.MINUTES);
                log.info("Image pulled successfully: {}", image);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while pulling image: " + image, ie);
            }
        }
    }

    /**
     * Build project in a secure, isolated Docker container.
     *
     * The project is mounted READ-ONLY at /source. At build time the container
     * copies source into /build (a native Linux tmpfs layer), installs dependencies
     * there, and runs the build. This avoids npm hanging due to file locking issues
     * when writing node_modules into a Windows bind-mounted directory.
     *
     * After a successful build the output (e.g. /build/dist) is copied back into
     * the bind-mounted /source/dist so the Java process can upload it from the
     * original projectPath on the host.
     */
    public void build(String projectPath, String framework) throws Exception {
        String containerId = null;
        String image = getDockerImage(framework);

        try {
            // 1. Ensure the right image is available locally
            pullImageIfMissing(image);

            // 2. Create container - source mounted READ-ONLY at /source
            log.info("Creating secure build container (image={}, framework={})...", image, framework);
            containerId = createSecureContainer(projectPath, image);

            // 3. Start container
            dockerClient.startContainerCmd(containerId).exec();
            log.info("Container started: {}", containerId);

            // 4. Copy source into native Linux directory /build to avoid Windows
            //    bind-mount file locking that causes npm install to hang forever
            log.info("Copying source to native Linux layer...");
            executeWithTimeout(containerId, "cp -r /source/. /build/", 2);

            // 5. Install dependencies inside /build (native Linux fs, no locking issues)
            log.info("Installing dependencies (network enabled)...");
            executeWithTimeout(containerId, getInstallCommand(framework), 8);

            // 6. Disable network for the build phase (security)
            log.info("Disabling network access...");
            disableNetworkAccess(containerId);

            // 7. Build inside /build (network disabled, sandboxed)
            log.info("Building project (network disabled)...");
            executeWithTimeout(containerId, getBuildCommand(framework), 5);

            // 8. Copy build output back to /source so the host can read it for upload
            log.info("Copying build output back to host mount...");
            String outputSubdir = getOutputSubdir(framework);
            executeWithTimeout(containerId,
                    "cp -r /build/" + outputSubdir + " /source/" + outputSubdir, 2);

            log.info("Build completed successfully!");

        } catch (TimeoutException e) {
            log.error("Build timeout - killing container");
            throw new RuntimeException("Build exceeded time limit of " + MAX_BUILD_TIME_MINUTES + " minutes");
        } finally {
            cleanupContainer(containerId);
        }
    }

    /**
     * Create container. Source is mounted READ-ONLY at /source.
     * /build is a fresh writable directory inside the container's native Linux layer.
     */
    private String createSecureContainer(String projectPath, String image) {
        HostConfig hostConfig = HostConfig.newHostConfig()
                // Mount source READ-WRITE — npm still writes to native /build (avoids Windows
                // bind-mount locking), but output is copied back here after build
                .withBinds(new Bind(
                        projectPath,
                        new Volume("/source"),
                        AccessMode.rw
                ))
                .withMemory(MEMORY_LIMIT_BYTES)
                .withMemorySwap(MEMORY_LIMIT_BYTES)
                .withCpuQuota(CPU_QUOTA)
                .withCpuPeriod(CPU_PERIOD)
                .withReadonlyRootfs(false)
                .withCapDrop(Capability.ALL)
                .withSecurityOpts(List.of("no-new-privileges"))
                .withNetworkMode("bridge");

        CreateContainerResponse container = dockerClient
                .createContainerCmd(image)
                .withName("build-" + System.currentTimeMillis())
                .withWorkingDir("/build")
                .withHostConfig(hostConfig)
                .withEnv("NODE_ENV=production", "CI=true")
                // Create /build and keep container alive for exec commands
                .withCmd("sh", "-c", "mkdir -p /build && tail -f /dev/null")
                .exec();

        return container.getId();
    }

    /**
     * Returns the output subdirectory name (relative to the build root)
     * so we know what to copy back to /source after the build.
     */
    private String getOutputSubdir(String framework) {
        return switch (framework.toLowerCase()) {
            case "next"  -> ".next";
            default      -> "dist";
        };
    }

    private void disableNetworkAccess(String containerId) {
        try {
            dockerClient.disconnectFromNetworkCmd()
                    .withContainerId(containerId)
                    .withNetworkId("bridge")
                    .withForce(true)
                    .exec();
            log.info("Network access disabled for container: {}", containerId);
        } catch (Exception e) {
            log.warn("Failed to disable network: {}", e.getMessage());
        }
    }

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
                        if (text.contains("error") || text.contains("Error") ||
                            text.contains("warn")  || text.contains("success")) {
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

        var execResponse = dockerClient.inspectExecCmd(execCreateCmd.getId()).exec();
        if (execResponse.getExitCodeLong() != null && execResponse.getExitCodeLong() != 0) {
            log.error("Command failed with output: {}", output);
            throw new RuntimeException("Build command failed: " + command);
        }
    }

    private String getInstallCommand(String framework) {
        return switch (framework.toLowerCase()) {
            case "react", "vue", "angular", "next", "vite", "node" ->
                "if [ -f package-lock.json ]; then npm ci --production=false; else npm install --production=false; fi";
            case "python" -> "pip install -r requirements.txt";
            case "static" -> "echo 'Static site - no install needed'";
            default ->
                "if [ -f package-lock.json ]; then npm ci --production=false; else npm install --production=false; fi";
        };
    }

    private String getBuildCommand(String framework) {
        return switch (framework.toLowerCase()) {
            case "react", "vue", "vite"  -> "npm run build";
            case "angular"               -> "npm run build -- --configuration production";
            case "next"                  -> "npm run build";
            case "python"                -> "python setup.py build";
            case "static"                -> "echo 'Static site - no build needed'";
            default                      -> "npm run build";
        };
    }

    private void cleanupContainer(String containerId) {
        if (containerId != null) {
            try {
                try {
                    dockerClient.stopContainerCmd(containerId).withTimeout(5).exec();
                } catch (Exception e) {
                    // already stopped
                }
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