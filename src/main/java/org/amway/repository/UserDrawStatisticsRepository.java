package org.amway.repository;

import org.amway.entity.UserDrawStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface UserDrawStatisticsRepository extends JpaRepository<UserDrawStatistics, Long> {

    Optional<UserDrawStatistics> findByUserIdAndActivityId(Long userId, Long activityId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserDrawStatistics s WHERE s.user.id = :userId " +
            "AND s.activity.id = :activityId")
    Optional<UserDrawStatistics> findByUserIdAndActivityIdWithLock(
            @Param("userId") Long userId,
            @Param("activityId") Long activityId
    );
}