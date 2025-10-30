package org.amway.service;

import org.amway.dto.request.DrawRequest;
import org.amway.dto.response.DrawResponse;
import org.amway.dto.response.DrawResult;
import org.amway.entity.*;
import org.amway.exception.BusinessException;
import org.amway.exception.enums.ErrorCode;
import org.amway.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final UserDailyDrawStatisticsRepository dailyStatisticsRepository;

    /**
     * 執行抽獎（入口方法，包含異常處理）
     */
    @Transactional(rollbackFor = Exception.class)
    public DrawResponse draw(Long userId, DrawRequest request) {
        try {
            return executeDrawInternal(userId, request);

        } catch (RedisConnectionFailureException e) {
            log.error("Redis 連線失敗", e);
            throw new BusinessException(ErrorCode.REDIS_ERROR, "快取服務異常，請稍後再試");

        } catch (DataAccessException e) {
            log.error("數據庫異常", e);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "數據庫連線異常，請稍後再試");

        } catch (BusinessException e) {
            // 業務異常直接拋出
            throw e;

        } catch (Exception e) {
            log.error("抽獎系統異常", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "系統異常，請稍後再試");
        }
    }

    /**
     * 執行抽獎的核心邏輯
     */
    private DrawResponse executeDrawInternal(Long userId, DrawRequest request) {
        // 1. 驗證活動
        LotteryActivity activity = validateActivity(request.getActivityId());

        // 2. 獲取用戶
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用戶不存在"));

        // 3. 使用分佈式鎖防止併發
        String lockKey = String.format("lottery:draw:%d:%d", userId, request.getActivityId());
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 嘗試獲取鎖，最多等待 10 秒，鎖自動釋放時間 30 秒
            boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "系統繁忙，請稍後再試");
            }

            // 4. 檢查剩餘抽獎次數
            int remainingDraws = checkAndGetRemainingDraws(user, activity);

            if (remainingDraws < request.getDrawCount()) {
                throw new BusinessException(
                        ErrorCode.INSUFFICIENT_DRAWS,
                        String.format("剩餘抽獎次數不足，剩餘：%d次，請求：%d次", remainingDraws, request.getDrawCount())
                );
            }

            // 5. 執行多次抽獎
            List<DrawResult> results = new ArrayList<>();

            // 根據限制類型更新統計
            if ("DAILY".equals(activity.getLimitType())) {
                // 每日限制模式
                LocalDate today = LocalDate.now();
                UserDailyDrawStatistics dailyStats = getOrCreateDailyStatistics(user, activity, today);

                for (int i = 0; i < request.getDrawCount(); i++) {
                    DrawResult result = executeSingleDraw(user, activity);
                    results.add(result);

                    // 更新每日統計
                    dailyStats.incrementDailyDrawCount();
                    if (result.getIsWinning()) {
                        dailyStats.incrementDailyWinningCount();
                    }
                }

                dailyStatisticsRepository.save(dailyStats);

                // 6. 計算剩餘次數
                int newRemainingDraws = Math.max(0, activity.getMaxDrawsPerUser() - dailyStats.getDailyDraws());

                return DrawResponse.builder()
                        .results(results)
                        .drawCount(request.getDrawCount())
                        .remainingDraws(newRemainingDraws)
                        .build();

            } else {
                // 總次數限制模式（TOTAL）
                UserDrawStatistics statistics = getOrCreateStatistics(user, activity);

                for (int i = 0; i < request.getDrawCount(); i++) {
                    DrawResult result = executeSingleDraw(user, activity);
                    results.add(result);

                    // 更新總統計
                    statistics.incrementDrawCount();
                    if (result.getIsWinning()) {
                        statistics.incrementWinningCount();
                    }
                }

                statisticsRepository.save(statistics);

                // 6. 計算剩餘次數
                int newRemainingDraws = Math.max(0, activity.getMaxDrawsPerUser() - statistics.getTotalDraws());

                return DrawResponse.builder()
                        .results(results)
                        .drawCount(request.getDrawCount())
                        .remainingDraws(newRemainingDraws)
                        .build();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "抽獎被中斷");
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
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "獎品不存在"));

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
     * 根據機率選擇獎品（輪盤賭算法）
     */
    private Prize selectPrizeByProbability(List<Prize> prizes) {
        if (prizes == null || prizes.isEmpty()) {
            return null;
        }

        // 生成 0-1 之間的隨機數
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
     * 檢查並獲取剩餘抽獎次數
     */
    private int checkAndGetRemainingDraws(User user, LotteryActivity activity) {
        String limitType = activity.getLimitType(); // TOTAL, DAILY, WEEKLY

        if ("DAILY".equals(limitType)) {
            // 每日限制
            LocalDate today = LocalDate.now();
            UserDailyDrawStatistics dailyStats = dailyStatisticsRepository
                    .findByUserIdAndActivityIdAndDrawDate(user.getId(), activity.getId(), today)
                    .orElse(null);

            if (dailyStats == null) {
                return activity.getMaxDrawsPerUser();
            }

            return Math.max(0, activity.getMaxDrawsPerUser() - dailyStats.getDailyDraws());

        } else {
            // 總次數限制（TOTAL）
            UserDrawStatistics statistics = statisticsRepository
                    .findByUserIdAndActivityId(user.getId(), activity.getId())
                    .orElse(null);

            if (statistics == null) {
                return activity.getMaxDrawsPerUser();
            }

            return Math.max(0, activity.getMaxDrawsPerUser() - statistics.getTotalDraws());
        }
    }

    /**
     * 驗證活動是否有效
     */
    private LotteryActivity validateActivity(Long activityId) {
        LotteryActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "活動不存在"));

        if (!activity.isActive()) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_ACTIVE, "活動未開始或已結束");
        }

        return activity;
    }

    /**
     * 獲取或創建用戶抽獎統計（總次數模式）
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
     * 獲取或創建用戶每日抽獎統計（每日限制模式）
     */
    private UserDailyDrawStatistics getOrCreateDailyStatistics(User user, LotteryActivity activity, LocalDate date) {
        return dailyStatisticsRepository
                .findByUserIdAndActivityIdAndDrawDate(user.getId(), activity.getId(), date)
                .orElseGet(() -> {
                    UserDailyDrawStatistics newStats = new UserDailyDrawStatistics();
                    newStats.setUserId(user.getId());
                    newStats.setActivityId(activity.getId());
                    newStats.setDrawDate(date);
                    newStats.setDailyDraws(0);
                    newStats.setDailyWinningDraws(0);
                    return dailyStatisticsRepository.save(newStats);
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
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "活動不存在"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用戶不存在"));

        return checkAndGetRemainingDraws(user, activity);
    }
}
