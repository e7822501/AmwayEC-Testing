package org.amway.repository;

import org.amway.entity.DrawRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DrawRecordRepository extends JpaRepository<DrawRecord, Long> {

    List<DrawRecord> findByUserId(Long userId);

    List<DrawRecord> findByUserIdAndActivityId(Long userId, Long activityId);
}
