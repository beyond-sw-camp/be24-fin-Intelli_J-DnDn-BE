package org.example.dndn.auth.repository;

import org.example.dndn.auth.model.entity.AccountRequest;
import org.example.dndn.auth.model.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountRequestRepository extends JpaRepository<AccountRequest, Long> {

    List<AccountRequest> findAllByOrderByCreatedAtDesc();

    List<AccountRequest> findAllByStatusOrderByCreatedAtDesc(RequestStatus status);
}
