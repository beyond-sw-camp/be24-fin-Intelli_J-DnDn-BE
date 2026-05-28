package org.example.dndn.domain.worker.repository;

import org.example.dndn.domain.worker.model.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    @Query("select w from Worker w where w.externalCode in :codes")
    List<Worker> findAllByExternalCodeIn(@Param("codes") Collection<String> codes);
}
