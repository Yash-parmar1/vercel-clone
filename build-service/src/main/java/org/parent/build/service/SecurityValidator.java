package org.parent.build.service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class SecurityValidator {

    private static final List<String> SUSPICIOUS_PATTERNS = Arrays.asList(
            "eval(",
            "exec(",
            "child_process",
            "rm -rf",
            "curl",
            "wget",
            "/etc/passwd",
            "process.env"
    );

    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final long MAX_TOTAL_SIZE = 500 * 1024 * 1024; // 500MB

    /**
     * Validate project before building
     */
    public void validateProject(String projectPath) throws SecurityException {
        File projectDir = new File(projectPath);

        // Check total size
        long totalSize = calculateDirectorySize(projectDir);
        if (totalSize > MAX_TOTAL_SIZE) {
            throw new SecurityException(
                    "Project size exceeds limit: " + totalSize + " bytes (max: " + MAX_TOTAL_SIZE + ")"
            );
        }

        // Scan for suspicious content
        scanForSuspiciousContent(projectDir);

        log.info("Security validation passed for: {}", projectPath);
    }

    /**
     * Calculate total directory size
     */
    private long calculateDirectorySize(File directory) {
        long size = 0;
        File[] files = directory.listFiles();
        
        if (files != null) {
            for (File file : files) {
                if (file.getName().equals("node_modules") || file.getName().equals(".git")) {
                    continue; // Skip these directories
                }
                
                if (file.isFile()) {
                    size += file.length();
                    
                    // Check individual file size
                    if (file.length() > MAX_FILE_SIZE) {
                        throw new SecurityException(
                                "File too large: " + file.getName() + " (" + file.length() + " bytes)"
                        );
                    }
                } else if (file.isDirectory()) {
                    size += calculateDirectorySize(file);
                }
            }
        }
        
        return size;
    }

    /**
     * Scan files for suspicious content
     */
    private void scanForSuspiciousContent(File directory) {
        File[] files = directory.listFiles();
        
        if (files != null) {
            for (File file : files) {
                if (file.getName().equals("node_modules") || file.getName().equals(".git")) {
                    continue;
                }
                
                if (file.isFile() && isTextFile(file)) {
                    scanFile(file);
                } else if (file.isDirectory()) {
                    scanForSuspiciousContent(file);
                }
            }
        }
    }

    /**
     * Scan individual file
     */
    private void scanFile(File file) {
        try {
            String content = Files.readString(Path.of(file.getAbsolutePath()));
            
            for (String pattern : SUSPICIOUS_PATTERNS) {
                if (content.contains(pattern)) {
                    log.warn("Suspicious pattern '{}' found in: {}", pattern, file.getName());
                    // Don't throw exception, just log warning
                    // In production, you might want to flag for manual review
                }
            }
        } catch (IOException e) {
            log.warn("Could not scan file: {}", file.getName());
        }
    }

    /**
     * Check if file is text (to avoid scanning binaries)
     */
    private boolean isTextFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".js") || name.endsWith(".ts") || 
               name.endsWith(".jsx") || name.endsWith(".tsx") ||
               name.endsWith(".json") || name.endsWith(".html") ||
               name.endsWith(".css") || name.endsWith(".sh");
    }
}
