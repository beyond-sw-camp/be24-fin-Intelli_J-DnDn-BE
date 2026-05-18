package org.example.dndncore.project.repository;

import org.example.dndncore.project.model.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findFirstByNameContaining(String fragment);
}