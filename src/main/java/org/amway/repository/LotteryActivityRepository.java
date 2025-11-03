package org.amway.repository;

import org.amway.entity.LotteryActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LotteryActivityRepository extends JpaRepository<LotteryActivity, Long> {

    @Query("SELECT a FROM LotteryActivity a WHERE a.status = 'ACTIVE' " +
            "AND a.startTime <= :now AND a.endTime >= :now")
    List<LotteryActivity> findActiveActivities(LocalDateTime now);
}
