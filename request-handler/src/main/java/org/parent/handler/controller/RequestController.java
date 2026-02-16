package org.parent.handler.controller;

import org.parent.common.service.R2Service;
import org.parent.handler.service.CacheService;
import org.parent.handler.service.MimeTypeResolver;
import org.parent.handler.service.SPARoutingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@Slf4j
public class RequestController {

    @Autowired
    private R2Service r2Service;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private MimeTypeResolver mimeTypeResolver;

    @Autowired
    private SPARoutingService spaRoutingService;

    /**
     * The "Vercel Magic" Handler
     * 
     * Handles requests like:
     * - my-project.vercel-clone.com/index.html
     * - my-project.vercel-clone.com/static/css/main.css
     * - my-project.vercel-clone.com/about (SPA route)
     * 
     * Serves from R2 with intelligent caching
     */
    @GetMapping("/**")
    public ResponseEntity<byte[]> handleRequest(
            HttpServletRequest request,
            @RequestHeader(value = "Host") String host) {

        long startTime = System.currentTimeMillis();

        try {
            // 1. Extract deployment ID from subdomain
            String deploymentId = extractDeploymentId(host);
            if (deploymentId == null) {
                log.warn("⚠️ Invalid subdomain: {}", host);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Invalid subdomain".getBytes());
            }

            // 2. Get requested path
            String requestedPath = request.getRequestURI();
            if (requestedPath.isEmpty() || requestedPath.equals("/")) {
                requestedPath = "/index.html";
            }

            // 3. Smart path resolution (handles SPA routing)
            String resolvedPath = requestedPath;
            if (!requestedPath.contains(".")) {
                // No extension - could be SPA route like /about, /dashboard, etc.
                resolvedPath = spaRoutingService.resolveSPAPath(deploymentId, requestedPath);
                log.debug("🔀 SPA Route: {} → {}", requestedPath, resolvedPath);
            }

            log.debug("📂 [{}] Request: {} | Resolved: {}", deploymentId, requestedPath, resolvedPath);

            // 4. Build R2 key
            String r2Key = "built/" + deploymentId + resolvedPath;

            // 5. Check cache first
            String cacheKey = deploymentId + ":" + resolvedPath;
            byte[] cachedContent = cacheService.get(cacheKey);

            if (cachedContent != null) {
                long duration = System.currentTimeMillis() - startTime;
                log.info("⚡ Cache HIT: [{}]{} ({}ms)", deploymentId, resolvedPath, duration);
                return buildResponse(resolvedPath, cachedContent, true);
            }

            log.debug("💾 Cache MISS: {}", cacheKey);

            // 6. Fetch from R2
            byte[] content = r2Service.downloadFile(r2Key);

            // 7. Cache for future requests
            cacheService.set(cacheKey, content);

            long duration = System.currentTimeMillis() - startTime;
            log.info("🌐 Served from R2: [{}]{} ({}ms)", deploymentId, resolvedPath, duration);

            return buildResponse(resolvedPath, content, false);

        } catch (Exception e) {
            log.error("❌ Error serving request for host {}: {}", host, e.getMessage());
            return serve404(host);
        }
    }

    /**
     * Custom 404 handler
     * Tries to serve deployment-specific 404.html, falls back to generic message
     */
    private ResponseEntity<byte[]> serve404(String host) {
        try {
            String deploymentId = extractDeploymentId(host);
            if (deploymentId != null) {
                String r2Key = "built/" + deploymentId + "/404.html";
                byte[] notFoundPage = r2Service.downloadFile(r2Key);
                
                log.info("📄 Serving custom 404.html for deployment: {}", deploymentId);
                
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.TEXT_HTML)
                        .body(notFoundPage);
            }
        } catch (Exception e) {
            log.debug("Custom 404.html not found for deployment, using default");
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_PLAIN)
                .body("404 - Not Found".getBytes());
    }

    /**
     * Extract deployment ID from Host header
     * 
     * Examples:
     * - "abc123.vercel-clone.com" → "abc123"
     * - "my-app.vercel-clone.com" → "my-app"
     * - "abc123.localhost:8083" → "abc123"
     */
    private String extractDeploymentId(String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        
        // Remove port if present
        String cleanHost = host.split(":")[0];
        
        // Split by dots and get first part (subdomain)
        String[] parts = cleanHost.split("\\.");
        
        return (parts.length >= 2) ? parts[0] : null;
    }

    /**
     * Build HTTP response with proper headers
     */
    private ResponseEntity<byte[]> buildResponse(String path, byte[] content, boolean fromCache) {
        HttpHeaders headers = new HttpHeaders();
        
        // Set correct Content-Type (CRITICAL for browser rendering)
        MediaType contentType = mimeTypeResolver.resolve(path);
        headers.setContentType(contentType);
        
        // Smart caching strategy
        if (path.contains("/static/") || 
            path.contains("/assets/") || 
            path.contains("/_next/static/") ||
            path.matches(".*\\.[a-f0-9]{8,}\\.(js|css)$")) {  // Hashed files
            // Static assets with hashes - cache for 1 year
            headers.setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());
        } else {
            // HTML and other files - cache for 1 hour
            headers.setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic());
        }

        // Custom headers for debugging and monitoring
        headers.add("X-Cache", fromCache ? "HIT" : "MISS");
        headers.add("X-Deployment-Platform", "Vercel-Clone");
        
        // Security headers
        headers.add("X-Content-Type-Options", "nosniff");
        headers.add("X-Frame-Options", "SAMEORIGIN");
        headers.add("X-XSS-Protection", "1; mode=block");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }
}