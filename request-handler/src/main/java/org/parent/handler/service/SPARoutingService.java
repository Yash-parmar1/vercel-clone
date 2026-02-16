package org.parent.handler.service;

import org.parent.common.service.R2Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SPARoutingService {

    @Autowired
    private R2Service r2Service;

    /**
     * Handle SPA routing (for React Router, Vue Router, etc.)
     * 
     * If /about doesn't exist as a file, try:
     * 1. /about.html
     * 2. /about/index.html
     * 3. /index.html (SPA fallback)
     */
    public String resolveSPAPath(String deploymentId, String requestedPath) {
        // Try exact path first
        if (fileExists(deploymentId, requestedPath)) {
            return requestedPath;
        }

        // Try with .html extension
        String htmlPath = requestedPath + ".html";
        if (fileExists(deploymentId, htmlPath)) {
            return htmlPath;
        }

        // Try /path/index.html
        String indexPath = requestedPath + "/index.html";
        if (fileExists(deploymentId, indexPath)) {
            return indexPath;
        }

        // Fallback to /index.html (for SPA client-side routing)
        log.debug("SPA fallback for: {}", requestedPath);
        return "/index.html";
    }

    private boolean fileExists(String deploymentId, String path) {
        try {
            String r2Key = "built/" + deploymentId + path;
            return r2Service.fileExists(r2Key);
        } catch (Exception e) {
            return false;
        }
    }
}
