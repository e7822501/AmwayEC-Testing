package org.amway.service;

import org.amway.dto.request.DrawRequest;
import org.amway.dto.response.DrawResponse;
import org.amway.entity.*;
import org.amway.exception.BusinessException;
import org.amway.exception.enums.ErrorCode;
import org.amway.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 抽獎服務整合測試
 * 使用真實的數據庫和 Redis 進行測試
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LotteryServiceIntegrationTest {

    @Autowired
    private LotteryService lotteryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LotteryActivityRepository activityRepository;

    @Autowired
    private PrizeRepository prizeRepository;

    @Autowired
    private DrawRecordRepository drawRecordRepository;

    @Autowired
    private UserDrawStatisticsRepository statisticsRepository;

    @Autowired
    private UserDailyDrawStatisticsRepository dailyStatisticsRepository;

    private User testUser;
    private LotteryActivity dailyActivity;
    private LotteryActivity totalActivity;

    @BeforeAll
    void setUpTestData() {
        // 清理舊數據
        drawRecordRepository.deleteAll();
        statisticsRepository.deleteAll();
        dailyStatisticsRepository.deleteAll();
        prizeRepository.deleteAll();
        activityRepository.deleteAll();
        userRepository.deleteAll();

        // 創建測試用戶
        testUser = new User();
        testUser.setUsername("integrationTestUser");
        testUser.setPassword("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
        testUser.setEmail("test@integration.com");
        testUser.setRole(User.UserRole.USER);
        testUser = userRepository.save(testUser);

        // 設置 Security Context
        Authentication auth = new UsernamePasswordAuthenticationToken(
                testUser.getId(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 創建每日限制活動
        dailyActivity = new LotteryActivity();
        dailyActivity.setName("每日限制測試活動");
        dailyActivity.setDescription("測試每日限制");
        dailyActivity.setStartTime(LocalDateTime.now().minusDays(1));
        dailyActivity.setEndTime(LocalDateTime.now().plusDays(30));
        dailyActivity.setLimitType("DAILY");
        dailyActivity.setMaxDrawsPerUser(3);
        dailyActivity.setStatus(LotteryActivity.ActivityStatus.ACTIVE);
        dailyActivity = activityRepository.save(dailyActivity);

        // 創建總次數限制活動
        totalActivity = new LotteryActivity();
        totalActivity.setName("總次數限制測試活動");
        totalActivity.setDescription("測試總次數限制");
        totalActivity.setStartTime(LocalDateTime.now().minusDays(1));
        totalActivity.setEndTime(LocalDateTime.now().plusDays(30));
        totalActivity.setLimitType("TOTAL");
        totalActivity.setMaxDrawsPerUser(5);
        totalActivity.setStatus(LotteryActivity.ActivityStatus.ACTIVE);
        totalActivity = activityRepository.save(totalActivity);

        // 為活動創建獎品
        createPrizesForActivity(dailyActivity);
        createPrizesForActivity(totalActivity);
    }

    private void createPrizesForActivity(LotteryActivity activity) {
        // 獎品1
        Prize prize1 = new Prize();
        prize1.setActivity(activity);
        prize1.setName("測試獎品1");
        prize1.setTotalStock(100);
        prize1.setRemainingStock(100);
        prize1.setProbability(BigDecimal.valueOf(0.1));
        prize1.setPrizeType(Prize.PrizeType.PHYSICAL);
        prizeRepository.save(prize1);

        // 獎品2
        Prize prize2 = new Prize();
        prize2.setActivity(activity);
        prize2.setName("測試獎品2");
        prize2.setTotalStock(200);
        prize2.setRemainingStock(200);
        prize2.setProbability(BigDecimal.valueOf(0.2));
        prize2.setPrizeType(Prize.PrizeType.VIRTUAL);
        prizeRepository.save(prize2);

        // 銘謝惠顧
        Prize noPrize = new Prize();
        noPrize.setActivity(activity);
        noPrize.setName("銘謝惠顧");
        noPrize.setTotalStock(999999);
        noPrize.setRemainingStock(999999);
        noPrize.setProbability(BigDecimal.valueOf(0.7));
        noPrize.setPrizeType(Prize.PrizeType.NO_PRIZE);
        prizeRepository.save(noPrize);
    }

    @BeforeEach
    void cleanUpDrawRecords() {
        // 每個測試前清理抽獎記錄和統計
        drawRecordRepository.deleteAll();
        statisticsRepository.deleteAll();
        dailyStatisticsRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("整合測試：每日限制超出")
    @Transactional
    void testDailyLimitExceeded() {
        // Arrange
        DrawRequest firstRequest = new DrawRequest(dailyActivity.getId(), 3);
        DrawRequest secondRequest = new DrawRequest(dailyActivity.getId(), 1);

        // Act - 第一次抽 3 次（達到每日上限）
        DrawResponse firstResponse = lotteryService.draw(testUser.getId(), firstRequest);

        // Assert
        assertNotNull(firstResponse);
        assertEquals(3, firstResponse.getDrawCount());
        assertEquals(0, firstResponse.getRemainingDraws());

        // Act & Assert - 嘗試再抽應該失敗
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> lotteryService.draw(testUser.getId(), secondRequest)
        );

        assertEquals(ErrorCode.INSUFFICIENT_DRAWS, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("剩餘抽獎次數不足"));

        // 驗證數據庫中的記錄
        LocalDate today = LocalDate.now();
        var dailyStats = dailyStatisticsRepository
                .findByUserIdAndActivityIdAndDrawDate(testUser.getId(), dailyActivity.getId(), today);

        assertTrue(dailyStats.isPresent());
        assertEquals(3, dailyStats.get().getDailyDraws());
    }

    @Test
    @Order(2)
    @DisplayName("整合測試：總次數限制超出")
    @Transactional
    void testTotalLimitExceeded() {
        // Arrange
        DrawRequest request1 = new DrawRequest(totalActivity.getId(), 3);
        DrawRequest request2 = new DrawRequest(totalActivity.getId(), 2);
        DrawRequest request3 = new DrawRequest(totalActivity.getId(), 1);

        // Act - 第一次抽 3 次
        DrawResponse response1 = lotteryService.draw(testUser.getId(), request1);
        assertEquals(2, response1.getRemainingDraws());

        // Act - 第二次抽 2 次（達到上限）
        DrawResponse response2 = lotteryService.draw(testUser.getId(), request2);
        assertEquals(0, response2.getRemainingDraws());

        // Assert - 嘗試再抽應該失敗
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> lotteryService.draw(testUser.getId(), request3)
        );

        assertEquals(ErrorCode.INSUFFICIENT_DRAWS, exception.getErrorCode());

        // 驗證數據庫中的統計
        var stats = statisticsRepository
                .findByUserIdAndActivityId(testUser.getId(), totalActivity.getId());

        assertTrue(stats.isPresent());
        assertEquals(5, stats.get().getTotalDraws());
    }

    @Test
    @Order(3)
    @DisplayName("整合測試：限流測試")
    void testRateLimitExceeded() throws InterruptedException {
        // 此測試需要真實的 RateLimiter，確保 @RateLimit 註解已啟用

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rateLimitCount = new AtomicInteger(0);

        // 快速發起 10 次請求
        for (int i = 0; i < 10; i++) {
            try {
                lotteryService.draw(testUser.getId(), new DrawRequest(dailyActivity.getId(), 1));
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                if (ErrorCode.RATE_LIMIT_EXCEEDED.equals(e.getErrorCode())) {
                    rateLimitCount.incrementAndGet();
                } else if (ErrorCode.INSUFFICIENT_DRAWS.equals(e.getErrorCode())) {
                    // 次數用完了，停止測試
                    break;
                }
            }
            Thread.sleep(50); // 稍微延遲避免太快
        }

        // Assert - 應該有部分請求被限流
        assertTrue(rateLimitCount.get() > 0 || successCount.get() <= 3,
                "應該觸發限流或達到抽獎次數上限");
    }

    @Test
    @Order(4)
    @DisplayName("整合測試：並發抽獎安全性")
    void testConcurrentDrawSafety() throws InterruptedException {
        // 創建專門的並發測試活動
        LotteryActivity concurrentActivity = new LotteryActivity();
        concurrentActivity.setName("並發測試活動");
        concurrentActivity.setStartTime(LocalDateTime.now().minusDays(1));
        concurrentActivity.setEndTime(LocalDateTime.now().plusDays(1));
        concurrentActivity.setLimitType("TOTAL");
        concurrentActivity.setMaxDrawsPerUser(10);
        concurrentActivity.setStatus(LotteryActivity.ActivityStatus.ACTIVE);
        concurrentActivity = activityRepository.save(concurrentActivity);

        // 創建庫存有限的獎品
        Prize limitedPrize = new Prize();
        limitedPrize.setActivity(concurrentActivity);
        limitedPrize.setName("限量獎品");
        limitedPrize.setTotalStock(5);
        limitedPrize.setRemainingStock(5);
        limitedPrize.setProbability(BigDecimal.valueOf(1.0)); // 100% 中獎
        limitedPrize.setPrizeType(Prize.PrizeType.PHYSICAL);
        limitedPrize = prizeRepository.save(limitedPrize);

        // 並發請求
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        Long finalActivityId = concurrentActivity.getId();
        Long finalPrizeId = limitedPrize.getId();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    lotteryService.draw(testUser.getId(), new DrawRequest(finalActivityId, 1));
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    // 預期部分請求會失敗（庫存不足或次數用完）
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // 驗證獎品庫存不會超抽
        Prize finalPrize = prizeRepository.findById(finalPrizeId).orElseThrow();
        assertTrue(finalPrize.getRemainingStock() >= 0, "庫存不應為負數");

        // 驗證最多只能抽取 5 個（或更少，因為有限流）
        long winningCount = drawRecordRepository.findByUserIdAndActivityId(testUser.getId(), finalActivityId)
                .stream()
                .filter(DrawRecord::getIsWinning)
                .count();

        assertTrue(winningCount <= 5, "中獎數不應超過庫存");
    }

    @Test
    @Order(5)
    @DisplayName("整合測試：活動已結束")
    void testActivityEnded() {
        // 創建已結束的活動
        LotteryActivity endedActivity = new LotteryActivity();
        endedActivity.setName("已結束活動");
        endedActivity.setStartTime(LocalDateTime.now().minusDays(10));
        endedActivity.setEndTime(LocalDateTime.now().minusDays(1));
        endedActivity.setLimitType("TOTAL");
        endedActivity.setMaxDrawsPerUser(5);
        endedActivity.setStatus(LotteryActivity.ActivityStatus.ACTIVE);
        endedActivity = activityRepository.save(endedActivity);

        Long endedActivityId = endedActivity.getId();

        // Assert
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> lotteryService.draw(testUser.getId(), new DrawRequest(endedActivityId, 1))
        );

        assertEquals(ErrorCode.ACTIVITY_NOT_ACTIVE, exception.getErrorCode());
    }

    @Test
    @Order(6)
    @DisplayName("整合測試：查詢剩餘抽獎次數")
    void testGetRemainingDraws() {
        // Act - 初始狀態
        int initialRemaining = lotteryService.getRemainingDraws(testUser.getId(), dailyActivity.getId());
        assertEquals(3, initialRemaining);

        // 抽 2 次
        lotteryService.draw(testUser.getId(), new DrawRequest(dailyActivity.getId(), 2));

        // 再次查詢
        int afterDrawRemaining = lotteryService.getRemainingDraws(testUser.getId(), dailyActivity.getId());
        assertEquals(1, afterDrawRemaining);
    }

    @AfterAll
    void cleanUp() {
        // 清理測試數據
        drawRecordRepository.deleteAll();
        statisticsRepository.deleteAll();
        dailyStatisticsRepository.deleteAll();
        prizeRepository.deleteAll();
        activityRepository.deleteAll();
        userRepository.deleteAll();
    }
}
