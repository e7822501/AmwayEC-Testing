package org.amway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "draw_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrawRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private LotteryActivity activity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prize_id")
    private Prize prize;

    @Column(name = "draw_time")
    private LocalDateTime drawTime;

    @Column(name = "is_winning", nullable = false)
    private Boolean isWinning = false;

    @Column(name = "prize_name", length = 100)
    private String prizeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DrawStatus status = DrawStatus.COMPLETED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum DrawStatus {
        COMPLETED,  // 已完成
        FAILED,     // 失敗
        CANCELLED   // 已取消
    }
}