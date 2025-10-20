package org.amway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_draw_statistics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "activity_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDrawStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private LotteryActivity activity;

    @Column(name = "total_draws", nullable = false)
    private Integer totalDraws = 0;

    @Column(name = "winning_draws", nullable = false)
    private Integer winningDraws = 0;

    @Column(name = "last_draw_time")
    private LocalDateTime lastDrawTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 增加抽獎次數
     */
    public void incrementDrawCount() {
        this.totalDraws++;
        this.lastDrawTime = LocalDateTime.now();
    }

    /**
     * 增加中獎次數
     */
    public void incrementWinningCount() {
        this.winningDraws++;
    }
}