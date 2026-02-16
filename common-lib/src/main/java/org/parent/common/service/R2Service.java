package org.parent.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.net.URI;

@Service
@Slf4j
public class R2Service {

    private final S3Client s3Client;

    @Value("${cloudflare.r2.bucket}")
    private String bucketName;

    public R2Service(
            @Value("${cloudflare.r2.endpoint}") String endpoint,
            @Value("${cloudflare.r2.access-key}") String accessKey,
            @Value("${cloudflare.r2.secret-key}") String secretKey) {

        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of("auto"))
                .build();
    }

    /**
     * Upload entire directory to R2
     */
    public void uploadDirectory(String localPath, String r2Prefix) {
        File directory = new File(localPath);
        uploadRecursively(directory, directory, r2Prefix);
    }

    private void uploadRecursively(File file, File baseDir, String r2Prefix) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    // Skip .git and node_modules
                    if (child.getName().equals(".git") || child.getName().equals("node_modules")) {
                        continue;
                    }
                    uploadRecursively(child, baseDir, r2Prefix);
                }
            }
        } else {
            // Calculate relative path
            String relativePath = baseDir.toPath()
                    .relativize(file.toPath())
                    .toString()
                    .replace("\\", "/");

            String r2Key = r2Prefix + "/" + relativePath;

            log.debug("Uploading: {} -> {}", file.getName(), r2Key);

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(r2Key)
                            .build(),
                    RequestBody.fromFile(file)
            );
        }
    }

    /**
     * Download directory from R2
     */
    public void downloadDirectory(String r2Prefix, String localPath) throws Exception {
        File localDir = new File(localPath);
        localDir.mkdirs();

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(r2Prefix)
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(request);

        for (S3Object object : response.contents()) {
            String key = object.key();

            // Skip the prefix itself
            if (key.equals(r2Prefix) || key.equals(r2Prefix + "/")) {
                continue;
            }

            // Calculate local file path
            String relativePath = key.substring(r2Prefix.length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }

            File localFile = new File(localPath, relativePath);
            localFile.getParentFile().mkdirs();

            log.debug("Downloading: {}", key);

            s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build(),
                    localFile.toPath()
            );
        }
    }

    /**
     * Download single file from R2
     */
    public byte[] downloadFile(String r2Key) {
        try {
            var response = s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(r2Key)
                            .build()
            );

            return response.readAllBytes();

        } catch (NoSuchKeyException e) {
            log.error("File not found: {}", r2Key);
            throw new RuntimeException("File not found: " + r2Key);
        } catch (Exception e) {
            log.error("Failed to download file: {}", e.getMessage());
            throw new RuntimeException("Failed to download: " + e.getMessage());
        }
    }

    /**
     * Check if file exists in R2
     */
    public boolean fileExists(String r2Key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(r2Key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}