package org.example.dndncore.weather;

import org.example.dndncore.weather.model.WeatherInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WeatherInfoRepository extends JpaRepository<WeatherInfo, Long> {

    Optional<WeatherInfo> findByReportDate(LocalDate reportDate);

    List<WeatherInfo> findAllByReportDateBetween(LocalDate startDate, LocalDate endDate);


    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update WeatherInfo w
               set w.locationLabel = :locationLabel,
                   w.todayHeadlineTemp = :todayHeadlineTemp,
                   w.todaySummary = :todaySummary,
                   w.amLabel = :amLabel,
                   w.pmLabel = :pmLabel,
                   w.weeklySummary = :weeklySummary,
                   w.precipitationProbability = :precipitationProbability,
                   w.fineDustValue = :fineDustValue,
                   w.fineDustGrade = :fineDustGrade,
                   w.dashboardJson = :dashboardJson,
                   w.updatedAt = :updatedAt
             where w.reportDate = :reportDate
            """)
    int updateSnapshotByReportDate(
            @Param("reportDate") LocalDate reportDate,
            @Param("locationLabel") String locationLabel,
            @Param("todayHeadlineTemp") String todayHeadlineTemp,
            @Param("todaySummary") String todaySummary,
            @Param("amLabel") String amLabel,
            @Param("pmLabel") String pmLabel,
            @Param("weeklySummary") String weeklySummary,
            @Param("precipitationProbability") String precipitationProbability,
            @Param("fineDustValue") String fineDustValue,
            @Param("fineDustGrade") String fineDustGrade,
            @Param("dashboardJson") String dashboardJson,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update WeatherInfo w set w.updatedAt = :updatedAt where w.reportDate = :reportDate")
    int touchUpdatedAt(@Param("reportDate") LocalDate reportDate, @Param("updatedAt") LocalDateTime updatedAt);
}
