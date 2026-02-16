package org.parent.common.controller;

import lombok.extern.slf4j.Slf4j;
import org.parent.common.entity.Project;
import org.parent.common.entity.User;
import org.parent.common.repository.ProjectRepository;
import org.parent.common.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@Slf4j
public class ProjectController {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> createProject(@RequestBody ProjectCreateRequest request, Authentication auth) {
        try {
            String userId = auth.getName();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Project project = new Project();
            project.setName(request.getName());
            project.setDescription(request.getDescription());
            project.setRepositoryUrl(request.getRepositoryUrl());
            project.setFramework(request.getFramework());
            project.setOwner(user);
            project.setIsPublic(request.getIsPublic() != null ? request.getIsPublic() : false);

            project = projectRepository.save(project);
            log.info("Project created: {} by user: {}", project.getId(), userId);

            return ResponseEntity.ok(project);
        } catch (Exception e) {
            log.error("Failed to create project: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Failed to create project: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<Project>> listProjects(Authentication auth) {
        try {
            String userId = auth.getName();
            List<Project> projects = projectRepository.findByOwnerId(userId);
            return ResponseEntity.ok(projects);
        } catch (Exception e) {
            log.error("Failed to list projects: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<?> getProject(@PathVariable String projectId, Authentication auth) {
        try {
            String userId = auth.getName();
            Project project = projectRepository.findByOwnerIdAndId(userId, projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found or access denied"));

            return ResponseEntity.ok(project);
        } catch (Exception e) {
            log.error("Failed to get project: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Failed to get project: " + e.getMessage());
        }
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<?> updateProject(@PathVariable String projectId,
                                           @RequestBody ProjectUpdateRequest request,
                                           Authentication auth) {
        try {
            String userId = auth.getName();
            Project project = projectRepository.findByOwnerIdAndId(userId, projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found or access denied"));

            if (request.getName() != null) {
                project.setName(request.getName());
            }
            if (request.getDescription() != null) {
                project.setDescription(request.getDescription());
            }
            if (request.getFramework() != null) {
                project.setFramework(request.getFramework());
            }
            if (request.getCustomDomain() != null) {
                project.setCustomDomain(request.getCustomDomain());
            }
            if (request.getIsPublic() != null) {
                project.setIsPublic(request.getIsPublic());
            }

            project = projectRepository.save(project);
            log.info("Project updated: {} by user: {}", projectId, userId);

            return ResponseEntity.ok(project);
        } catch (Exception e) {
            log.error("Failed to update project: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Failed to update project: " + e.getMessage());
        }
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<?> deleteProject(@PathVariable String projectId, Authentication auth) {
        try {
            String userId = auth.getName();
            Project project = projectRepository.findByOwnerIdAndId(userId, projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found or access denied"));

            projectRepository.delete(project);
            log.info("Project deleted: {} by user: {}", projectId, userId);

            return ResponseEntity.ok("Project deleted successfully");
        } catch (Exception e) {
            log.error("Failed to delete project: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Failed to delete project: " + e.getMessage());
        }
    }

    public static class ProjectCreateRequest {
        public String name;
        public String description;
        public String repositoryUrl;
        public String framework;
        public Boolean isPublic;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getRepositoryUrl() { return repositoryUrl; }
        public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }

        public String getFramework() { return framework; }
        public void setFramework(String framework) { this.framework = framework; }

        public Boolean getIsPublic() { return isPublic; }
        public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }
    }

    public static class ProjectUpdateRequest {
        public String name;
        public String description;
        public String framework;
        public String customDomain;
        public Boolean isPublic;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getFramework() { return framework; }
        public void setFramework(String framework) { this.framework = framework; }

        public String getCustomDomain() { return customDomain; }
        public void setCustomDomain(String customDomain) { this.customDomain = customDomain; }

        public Boolean getIsPublic() { return isPublic; }
        public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }
    }
}
