package org.amway.repository;

import org.amway.entity.Prize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrizeRepository extends JpaRepository<Prize, Long> {

    List<Prize> findByActivityId(Long activityId);

    @Query("SELECT p FROM Prize p WHERE p.activity.id = :activityId AND p.remainingStock > 0")
    List<Prize> findAvailablePrizesByActivityId(@Param("activityId") Long activityId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Prize p WHERE p.id = :prizeId")
    Optional<Prize> findByIdWithLock(@Param("prizeId") Long prizeId);
}
