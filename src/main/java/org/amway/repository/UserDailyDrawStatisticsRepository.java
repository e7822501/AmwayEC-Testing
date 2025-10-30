package org.amway.repository;

import org.amway.entity.UserDailyDrawStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface UserDailyDrawStatisticsRepository extends JpaRepository<UserDailyDrawStatistics, Long> {
    
    Optional<UserDailyDrawStatistics> findByUserIdAndActivityIdAndDrawDate(
        Long userId, 
        Long activityId, 
        LocalDate drawDate
    );
}
