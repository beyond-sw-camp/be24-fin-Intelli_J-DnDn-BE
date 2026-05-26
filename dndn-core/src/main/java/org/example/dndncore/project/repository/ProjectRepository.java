package org.example.dndncore.project.repository;

import org.example.dndncore.project.model.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findFirstByNameContaining(String fragment);

    @Query("SELECT p FROM Project p WHERE LOWER(p.name) LIKE LOWER(CONCAT(:prefix, '%'))")
    List<Project> findByNamePrefixIgnoreCase(@Param("prefix") String prefix);
}
