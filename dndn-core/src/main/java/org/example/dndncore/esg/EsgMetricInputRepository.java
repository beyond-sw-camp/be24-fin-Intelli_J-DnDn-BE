package org.example.dndncore.esg;

import org.example.dndncore.esg.model.EsgMetricInput;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EsgMetricInputRepository extends JpaRepository<EsgMetricInput, Long> {

    List<EsgMetricInput> findAllByProject_IdxAndReportDate(Long projectIdx, LocalDate reportDate);
}
