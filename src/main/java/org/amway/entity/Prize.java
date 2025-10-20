package org.amway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "prizes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Prize {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private LotteryActivity activity;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "total_stock", nullable = false)
    private Integer totalStock = 0;

    @Column(name = "remaining_stock", nullable = false)
    private Integer remainingStock = 0;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal probability;

    @Enumerated(EnumType.STRING)
    @Column(name = "prize_type", nullable = false, length = 20)
    private PrizeType prizeType = PrizeType.PHYSICAL;

    @Column(name = "image_url")
    private String imageUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum PrizeType {
        PHYSICAL,   // 實體獎品
        VIRTUAL,    // 虛擬獎品
        NO_PRIZE    // 銘謝惠顧
    }

    /**
     * 檢查是否還有庫存
     */
    public boolean hasStock() {
        return remainingStock > 0;
    }

    /**
     * 減少庫存
     */
    public void decreaseStock(int count) {
        if (remainingStock >= count) {
            this.remainingStock -= count;
        } else {
            throw new IllegalStateException("庫存不足");
        }
    }
}
