package org.example.dndn.domain.project.repository;

import org.example.dndn.domain.project.model.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findFirstByNameContaining(String fragment);

    List<Project> findAllByActiveTrue();

    long countByActiveTrue();
}
