package org.amway.repository;

import org.amway.entity.DrawRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DrawRecordRepository extends JpaRepository<DrawRecord, Long> {

    List<DrawRecord> findByUserId(Long userId);

    List<DrawRecord> findByActivityId(Long activityId);

    List<DrawRecord> findByUserIdAndActivityId(Long userId, Long activityId);

    @Query("SELECT COUNT(d) FROM DrawRecord d WHERE d.user.id = :userId " +
            "AND d.activity.id = :activityId AND d.status = 'COMPLETED'")
    int countByUserIdAndActivityId(@Param("userId") Long userId,
                                   @Param("activityId") Long activityId);
}
