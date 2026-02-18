package org.parent.common.config;

import lombok.extern.slf4j.Slf4j;
import org.parent.common.entity.Role;
import org.parent.common.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class DataInitializer {

    @Bean
    CommandLineRunner initRoles(RoleRepository roleRepository) {
        return args -> {
            if (roleRepository.findByName("ROLE_USER").isEmpty()) {
                Role role = new Role();
                role.setName("ROLE_USER");
                role.setDescription("Default user role");
                roleRepository.save(role);
                log.info("Created default ROLE_USER");
            }
        };
    }
}
