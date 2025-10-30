package org.amway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_daily_draw_statistics", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "activity_id", "draw_date"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDailyDrawStatistics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "activity_id", nullable = false)
    private Long activityId;
    
    @Column(name = "draw_date", nullable = false)
    private LocalDate drawDate;
    
    @Column(name = "daily_draws", nullable = false)
    private Integer dailyDraws = 0;
    
    @Column(name = "daily_winning_draws", nullable = false)
    private Integer dailyWinningDraws = 0;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public void incrementDailyDrawCount() {
        this.dailyDraws++;
    }
    
    public void incrementDailyWinningCount() {
        this.dailyWinningDraws++;
    }
}
