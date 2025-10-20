package org.amway.service;

import org.amway.dto.request.DrawRequest;
import org.amway.dto.response.DrawResponse;
import org.amway.dto.response.DrawResult;
import org.amway.entity.*;
import org.amway.exception.InsufficientDrawsException;
import org.amway.exception.InvalidActivityException;
import org.amway.exception.PrizeStockException;
import org.amway.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LotteryService {

    private final LotteryActivityRepository activityRepository;
    private final PrizeRepository prizeRepository;
    private final DrawRecordRepository drawRecordRepository;
    private final UserDrawStatisticsRepository statisticsRepository;
    private final UserRepository userRepository;
    private final RedissonClient redissonClient;

    /**
     * 執行抽獎
     */
    @Transactional(rollbackFor = Exception.class)
    public DrawResponse draw(Long userId, DrawRequest request) {
        // 1. 驗證活動
        LotteryActivity activity = validateActivity(request.getActivityId());

        // 2. 獲取用戶
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用戶不存在"));

        // 3. 使用分佈式鎖防止並發問題
        String lockKey = String.format("lottery:draw:%d:%d", userId, request.getActivityId());
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 嘗試獲取鎖，最多等待10秒，鎖30秒後自動釋放
            boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new RuntimeException("系統繁忙，請稍後再試");
            }

            // 4. 檢查用戶抽獎次數
            UserDrawStatistics statistics = getOrCreateStatistics(user, activity);
            int remainingDraws = activity.getMaxDrawsPerUser() - statistics.getTotalDraws();

            if (remainingDraws < request.getDrawCount()) {
                throw new InsufficientDrawsException(
                        String.format("剩餘抽獎次數不足，剩餘：%d次，請求：%d次",
                                remainingDraws, request.getDrawCount())
                );
            }

            // 5. 執行多次抽獎
            List<DrawResult> results = new ArrayList<>();
            for (int i = 0; i < request.getDrawCount(); i++) {
                DrawResult result = executeSingleDraw(user, activity);
                results.add(result);

                // 更新統計
                statistics.incrementDrawCount();
                if (result.getIsWinning()) {
                    statistics.incrementWinningCount();
                }
            }

            statisticsRepository.save(statistics);

            // 6. 計算剩餘次數
            int newRemainingDraws = activity.getMaxDrawsPerUser() - statistics.getTotalDraws();

            return DrawResponse.builder()
                    .results(results)
                    .drawCount(request.getDrawCount())
                    .remainingDraws(newRemainingDraws)
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("抽獎被中斷", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 執行單次抽獎
     */
    private DrawResult executeSingleDraw(User user, LotteryActivity activity) {
        // 1. 獲取所有可用獎品
        List<Prize> prizes = prizeRepository.findByActivityId(activity.getId());

        // 2. 根據機率選擇獎品
        Prize selectedPrize = selectPrizeByProbability(prizes);

        // 3. 創建抽獎記錄
        DrawRecord record = new DrawRecord();
        record.setActivity(activity);
        record.setUser(user);
        record.setDrawTime(LocalDateTime.now());
        record.setStatus(DrawRecord.DrawStatus.COMPLETED);

        boolean isWinning = false;
        String prizeName = "銘謝惠顧";

        // 4. 處理中獎情況
        if (selectedPrize != null && selectedPrize.getPrizeType() != Prize.PrizeType.NO_PRIZE) {
            // 使用悲觀鎖獲取獎品，防止超抽
            Prize lockedPrize = prizeRepository.findByIdWithLock(selectedPrize.getId())
                    .orElseThrow(() -> new PrizeStockException("獎品不存在"));

            if (lockedPrize.hasStock()) {
                lockedPrize.decreaseStock(1);
                prizeRepository.save(lockedPrize);

                record.setPrize(lockedPrize);
                record.setIsWinning(true);
                record.setPrizeName(lockedPrize.getName());

                isWinning = true;
                prizeName = lockedPrize.getName();
            } else {
                // 庫存不足，降級為銘謝惠顧
                log.warn("獎品庫存不足，獎品ID：{}", selectedPrize.getId());
                record.setIsWinning(false);
                record.setPrizeName("銘謝惠顧");
            }
        } else {
            record.setIsWinning(false);
            record.setPrizeName("銘謝惠顧");
        }

        drawRecordRepository.save(record);

        // 5. 構建返回結果
        return DrawResult.builder()
                .recordId(record.getId())
                .isWinning(isWinning)
                .prizeId(selectedPrize != null ? selectedPrize.getId() : null)
                .prizeName(prizeName)
                .prizeType(selectedPrize != null ? selectedPrize.getPrizeType().name() : "NO_PRIZE")
                .prizeDescription(selectedPrize != null ? selectedPrize.getDescription() : null)
                .drawTime(record.getDrawTime())
                .build();
    }

    /**
     * 根據機率選擇獎品
     */
    private Prize selectPrizeByProbability(List<Prize> prizes) {
        if (prizes == null || prizes.isEmpty()) {
            return null;
        }

        // 生成0-1之間的隨機數
        double random = Math.random();
        double cumulativeProbability = 0.0;

        for (Prize prize : prizes) {
            cumulativeProbability += prize.getProbability().doubleValue();
            if (random <= cumulativeProbability) {
                return prize;
            }
        }

        // 如果沒有選中任何獎品，返回最後一個（通常是銘謝惠顧）
        return prizes.get(prizes.size() - 1);
    }

    /**
     * 驗證活動是否有效
     */
    private LotteryActivity validateActivity(Long activityId) {
        LotteryActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new InvalidActivityException("活動不存在"));

        if (!activity.isActive()) {
            throw new InvalidActivityException("活動未開始或已結束");
        }

        return activity;
    }

    /**
     * 獲取或創建用戶抽獎統計
     */
    private UserDrawStatistics getOrCreateStatistics(User user, LotteryActivity activity) {
        return statisticsRepository.findByUserIdAndActivityIdWithLock(
                user.getId(), activity.getId()
        ).orElseGet(() -> {
            UserDrawStatistics newStats = new UserDrawStatistics();
            newStats.setUser(user);
            newStats.setActivity(activity);
            newStats.setTotalDraws(0);
            newStats.setWinningDraws(0);
            return statisticsRepository.save(newStats);
        });
    }

    /**
     * 查詢用戶抽獎歷史
     */
    public List<DrawRecord> getUserDrawHistory(Long userId, Long activityId) {
        if (activityId != null) {
            return drawRecordRepository.findByUserIdAndActivityId(userId, activityId);
        }
        return drawRecordRepository.findByUserId(userId);
    }

    /**
     * 獲取用戶剩餘抽獎次數
     */
    public int getRemainingDraws(Long userId, Long activityId) {
        LotteryActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new InvalidActivityException("活動不存在"));

        UserDrawStatistics statistics = statisticsRepository
                .findByUserIdAndActivityId(userId, activityId)
                .orElse(null);

        if (statistics == null) {
            return activity.getMaxDrawsPerUser();
        }

        return Math.max(0, activity.getMaxDrawsPerUser() - statistics.getTotalDraws());
    }
}