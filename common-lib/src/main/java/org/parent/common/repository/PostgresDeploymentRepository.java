package org.parent.common.repository;

import org.parent.common.entity.Deployment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Primary
public class PostgresDeploymentRepository implements DeploymentRepository {

    private final JpaDeploymentRepository jpa;

    @Autowired
    public PostgresDeploymentRepository(JpaDeploymentRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Deployment deployment) {
        jpa.save(deployment);
    }

    @Override
    public Optional<Deployment> findById(String id) {
        return jpa.findById(id);
    }
}
