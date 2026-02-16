package org.parent.common.repository;

import org.parent.common.entity.Deployment;
import java.util.Optional;

public interface DeploymentRepository {
    void save(Deployment deployment);
    Optional<Deployment> findById(String id);
}
