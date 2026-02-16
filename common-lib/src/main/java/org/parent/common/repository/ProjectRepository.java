package org.parent.common.repository;

import org.parent.common.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {
    List<Project> findByOwnerId(String ownerId);
    Optional<Project> findByOwnerIdAndId(String ownerId, String projectId);
    Optional<Project> findByCustomDomain(String customDomain);
}
