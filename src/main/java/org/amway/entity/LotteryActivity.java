package org.amway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "lottery_activities")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LotteryActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    /**
     * 抽獎次數限制類型
     * TOTAL: 活動期間總次數限制
     * DAILY: 每日限制
     * WEEKLY: 每週限制
     */
    @Column(name = "limit_type", nullable = false, length = 20)
    private String limitType = "TOTAL";

    @Column(name = "max_draws_per_user", nullable = false)
    private Integer maxDrawsPerUser = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivityStatus status = ActivityStatus.ACTIVE;

    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Prize> prizes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ActivityStatus {
        ACTIVE,     // 進行中
        INACTIVE,   // 停用
        ENDED       // 已結束
    }

    /**
     * 檢查活動是否正在進行中
     */
    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now();
        return status == ActivityStatus.ACTIVE
                && now.isAfter(startTime)
                && now.isBefore(endTime);
    }
}
