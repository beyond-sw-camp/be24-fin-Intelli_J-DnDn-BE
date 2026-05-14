package org.example.dndn.project.repository;

import org.example.dndn.project.model.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findFirstByNameContaining(String fragment);
}