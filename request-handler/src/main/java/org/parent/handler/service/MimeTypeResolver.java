package org.parent.handler.service;


import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class MimeTypeResolver {

    private static final Map<String, MediaType> MIME_TYPES = new HashMap<>();

    static {
        // HTML & CSS
        MIME_TYPES.put(".html", MediaType.TEXT_HTML);
        MIME_TYPES.put(".htm", MediaType.TEXT_HTML);
        MIME_TYPES.put(".css", MediaType.valueOf("text/css"));

        // JavaScript
        MIME_TYPES.put(".js", MediaType.valueOf("application/javascript"));
        MIME_TYPES.put(".mjs", MediaType.valueOf("application/javascript"));
        MIME_TYPES.put(".jsx", MediaType.valueOf("application/javascript"));

        // JSON
        MIME_TYPES.put(".json", MediaType.APPLICATION_JSON);

        // Images
        MIME_TYPES.put(".png", MediaType.IMAGE_PNG);
        MIME_TYPES.put(".jpg", MediaType.IMAGE_JPEG);
        MIME_TYPES.put(".jpeg", MediaType.IMAGE_JPEG);
        MIME_TYPES.put(".gif", MediaType.IMAGE_GIF);
        MIME_TYPES.put(".svg", MediaType.valueOf("image/svg+xml"));
        MIME_TYPES.put(".webp", MediaType.valueOf("image/webp"));
        MIME_TYPES.put(".ico", MediaType.valueOf("image/x-icon"));

        // Fonts
        MIME_TYPES.put(".woff", MediaType.valueOf("font/woff"));
        MIME_TYPES.put(".woff2", MediaType.valueOf("font/woff2"));
        MIME_TYPES.put(".ttf", MediaType.valueOf("font/ttf"));
        MIME_TYPES.put(".otf", MediaType.valueOf("font/otf"));

        // Video
        MIME_TYPES.put(".mp4", MediaType.valueOf("video/mp4"));
        MIME_TYPES.put(".webm", MediaType.valueOf("video/webm"));

        // Other
        MIME_TYPES.put(".xml", MediaType.APPLICATION_XML);
        MIME_TYPES.put(".pdf", MediaType.APPLICATION_PDF);
        MIME_TYPES.put(".txt", MediaType.TEXT_PLAIN);
        MIME_TYPES.put(".md", MediaType.TEXT_MARKDOWN);
    }

    /**
     * Resolve MIME type from file extension
     */
    public MediaType resolve(String path) {
        if (path == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        // Find last dot
        int lastDot = path.lastIndexOf('.');
        if (lastDot == -1) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        String extension = path.substring(lastDot).toLowerCase();
        return MIME_TYPES.getOrDefault(extension, MediaType.APPLICATION_OCTET_STREAM);
    }
}