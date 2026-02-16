package org.parent.common.repository;

import org.parent.common.entity.Deployment;
import org.parent.common.model.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JpaDeploymentRepository extends JpaRepository<Deployment, String> {
    List<Deployment> findByProjectId(String projectId);
    List<Deployment> findByUserId(String userId);
    List<Deployment> findByStatus(DeploymentStatus status);
    Optional<Deployment> findByDeploymentUrl(String deploymentUrl);
}
