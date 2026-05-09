package org.example.dndn.auth.repository;

import org.example.dndn.auth.model.entity.SystemUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemUserRepository extends JpaRepository<SystemUser, Long> {

    Optional<SystemUser> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
