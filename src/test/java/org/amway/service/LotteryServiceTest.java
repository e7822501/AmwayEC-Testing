package org.amway.service;

import org.amway.dto.request.DrawRequest;
import org.amway.dto.response.DrawResponse;
import org.amway.dto.response.DrawResult;
import org.amway.entity.*;
import org.amway.exception.BusinessException;
import org.amway.exception.enums.ErrorCode;
import org.amway.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("抽獎服務測試")
class LotteryServiceTest {

    @Mock
    private LotteryActivityRepository activityRepository;

    @Mock
    private PrizeRepository prizeRepository;

    @Mock
    private DrawRecordRepository drawRecordRepository;

    @Mock
    private UserDrawStatisticsRepository statisticsRepository;

    @Mock
    private UserDailyDrawStatisticsRepository dailyStatisticsRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @InjectMocks
    private LotteryService lotteryService;

    private User testUser;
    private LotteryActivity testActivity;
    private Prize prize1;
    private Prize prize2;
    private Prize noPrize;

    @BeforeEach
    void setUp() throws Exception {
        // 基本設置
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testUser");
        testUser.setRole(User.UserRole.USER);

        testActivity = new LotteryActivity();
        testActivity.setId(1L);
        testActivity.setName("測試活動");
        testActivity.setStartTime(LocalDateTime.now().minusDays(1));
        testActivity.setEndTime(LocalDateTime.now().plusDays(1));
        testActivity.setMaxDrawsPerUser(5);
        testActivity.setLimitType("TOTAL"); // 默認總次數限制
        testActivity.setStatus(LotteryActivity.ActivityStatus.ACTIVE);

        // 獎品設置
        prize1 = new Prize();
        prize1.setId(1L);
        prize1.setName("iPhone");
        prize1.setTotalStock(10);
        prize1.setRemainingStock(5);
        prize1.setProbability(BigDecimal.valueOf(0.1));
        prize1.setPrizeType(Prize.PrizeType.PHYSICAL);
        prize1.setActivity(testActivity);

        prize2 = new Prize();
        prize2.setId(2L);
        prize2.setName("購物金");
        prize2.setTotalStock(100);
        prize2.setRemainingStock(50);
        prize2.setProbability(BigDecimal.valueOf(0.2));
        prize2.setPrizeType(Prize.PrizeType.VIRTUAL);
        prize2.setActivity(testActivity);

        noPrize = new Prize();
        noPrize.setId(3L);
        noPrize.setName("銘謝惠顧");
        noPrize.setTotalStock(999999);
        noPrize.setRemainingStock(999999);
        noPrize.setProbability(BigDecimal.valueOf(0.7));
        noPrize.setPrizeType(Prize.PrizeType.NO_PRIZE);
        noPrize.setActivity(testActivity);

        // Mock Redis 鎖（使用 lenient）
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    @DisplayName("測試單次抽獎成功")
    void testSingleDrawSuccess() {
        // Arrange
        DrawRequest request = new DrawRequest(1L, 1);
        UserDrawStatistics statistics = new UserDrawStatistics();
        statistics.setTotalDraws(0);
        statistics.setUser(testUser);
        statistics.setActivity(testActivity);

        when(activityRepository.findById(1L)).thenReturn(Optional.of(testActivity));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(statisticsRepository.findByUserIdAndActivityIdWithLock(1L, 1L))
                .thenReturn(Optional.of(statistics));
        when(statisticsRepository.findByUserIdAndActivityId(1L, 1L))
                .thenReturn(Optional.of(statistics));
        when(prizeRepository.findByActivityId(1L))
                .thenReturn(Arrays.asList(prize1, prize2, noPrize));
        when(prizeRepository.findByIdWithLock(anyLong()))
                .thenReturn(Optional.of(prize1));
        when(drawRecordRepository.save(any(DrawRecord.class)))
                .thenAnswer(invocation -> {
                    DrawRecord record = invocation.getArgument(0);
                    record.setId(1L); // 設置 ID
                    return record;
                });
        when(statisticsRepository.save(any(UserDrawStatistics.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        DrawResponse response = lotteryService.draw(1L, request);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getDrawCount());
        assertEquals(1, response.getResults().size());
        assertEquals(4, response.getRemainingDraws());

        verify(statisticsRepository).save(any(UserDrawStatistics.class));
        verify(drawRecordRepository).save(any(DrawRecord.class));
    }

    @Test
    @DisplayName("測試多次抽獎")
    void testMultipleDraws() {
        // Arrange
        DrawRequest request = new DrawRequest(1L, 3);
        UserDrawStatistics statistics = new UserDrawStatistics();
        statistics.setTotalDraws(0);
        statistics.setUser(testUser);
        statistics.setActivity(testActivity);

        when(activityRepository.findById(1L)).thenReturn(Optional.of(testActivity));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(statisticsRepository.findByUserIdAndActivityIdWithLock(1L, 1L))
                .thenReturn(Optional.of(statistics));
        when(statisticsRepository.findByUserIdAndActivityId(1L, 1L))
                .thenReturn(Optional.of(statistics));
        when(prizeRepository.findByActivityId(1L))
                .thenReturn(Arrays.asList(prize1, prize2, noPrize));
        when(prizeRepository.findByIdWithLock(anyLong()))
                .thenReturn(Optional.of(noPrize));
        when(drawRecordRepository.save(any(DrawRecord.class)))
                .thenAnswer(invocation -> {
                    DrawRecord record = invocation.getArgument(0);
                    record.setId(System.currentTimeMillis()); // 設置唯一 ID
                    return record;
                });
        when(statisticsRepository.save(any(UserDrawStatistics.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        DrawResponse response = lotteryService.draw(1L, request);

        // Assert
        assertNotNull(response);
        assertEquals(3, response.getDrawCount());
        assertEquals(3, response.getResults().size());
        assertEquals(2, response.getRemainingDraws());

        verify(drawRecordRepository, times(3)).save(any(DrawRecord.class));
    }

    @Test
    @DisplayName("測試抽獎次數不足")
    void testInsufficientDraws() {
        // Arrange
        DrawRequest request = new DrawRequest(1L, 6);
        UserDrawStatistics statistics = new UserDrawStatistics();
        statistics.setTotalDraws(0);
        statistics.setUser(testUser);
        statistics.setActivity(testActivity);

        when(activityRepository.findById(1L)).thenReturn(Optional.of(testActivity));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(statisticsRepository.findByUserIdAndActivityId(1L, 1L))
                .thenReturn(Optional.of(statistics));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            lotteryService.draw(1L, request);
        });

        assertEquals(ErrorCode.INSUFFICIENT_DRAWS, exception.getErrorCode());
    }

    @Test
    @DisplayName("測試活動不存在")
    void testActivityNotFound() {
        // Arrange
        DrawRequest request = new DrawRequest(999L, 1);

        when(activityRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            lotteryService.draw(1L, request);
        });

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("測試活動已結束")
    void testActivityEnded() {
        // Arrange
        DrawRequest request = new DrawRequest(1L, 1);
        testActivity.setEndTime(LocalDateTime.now().minusDays(1));

        when(activityRepository.findById(1L)).thenReturn(Optional.of(testActivity));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            lotteryService.draw(1L, request);
        });

        assertEquals(ErrorCode.ACTIVITY_NOT_ACTIVE, exception.getErrorCode());
    }

    @Test
    @DisplayName("測試獎品庫存扣減")
    void testPrizeStockDeduction() {
        // Arrange
        DrawRequest request = new DrawRequest(1L, 1);
        UserDrawStatistics statistics = new UserDrawStatistics();
        statistics.setTotalDraws(0);
        statistics.setUser(testUser);
        statistics.setActivity(testActivity);

        // 強制保證會中獎 prize1
        prize1.setProbability(BigDecimal.valueOf(1.0));
        prize2.setProbability(BigDecimal.valueOf(0.0));
        noPrize.setProbability(BigDecimal.valueOf(0.0));

        prize1.setRemainingStock(5);

        when(activityRepository.findById(1L)).thenReturn(Optional.of(testActivity));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(statisticsRepository.findByUserIdAndActivityIdWithLock(1L, 1L))
                .thenReturn(Optional.of(statistics));
        when(statisticsRepository.findByUserIdAndActivityId(1L, 1L))
                .thenReturn(Optional.of(statistics));
        when(prizeRepository.findByActivityId(1L))
                .thenReturn(Arrays.asList(prize1, prize2, noPrize));
        when(prizeRepository.findByIdWithLock(anyLong()))
                .thenReturn(Optional.of(prize1));
        when(drawRecordRepository.save(any(DrawRecord.class)))
                .thenAnswer(invocation -> {
                    DrawRecord record = invocation.getArgument(0);
                    record.setId(1L);
                    return record;
                });
        when(statisticsRepository.save(any(UserDrawStatistics.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        lotteryService.draw(1L, request);

        // Assert
        assertEquals(4, prize1.getRemainingStock(), "庫存應該被扣 1");
        verify(prizeRepository).save(prize1);
    }

    @Test
    @DisplayName("測試機率分布")
    void testProbabilityDistribution() {
        // Arrange
        DrawRequest request = new DrawRequest(1L, 1000);
        UserDrawStatistics statistics = new UserDrawStatistics();
        statistics.setTotalDraws(0);
        statistics.setUser(testUser);
        statistics.setActivity(testActivity);

        testActivity.setMaxDrawsPerUser(1000);
        prize1.setRemainingStock(100);
        prize2.setRemainingStock(200);

        when(activityRepository.findById(1L)).thenReturn(Optional.of(testActivity));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(statisticsRepository.findByUserIdAndActivityIdWithLock(1L, 1L))
                .thenReturn(Optional.of(statistics));
        when(statisticsRepository.findByUserIdAndActivityId(1L, 1L))
                .thenReturn(Optional.of(statistics));
        when(prizeRepository.findByActivityId(1L))
                .thenReturn(Arrays.asList(prize1, prize2, noPrize));
        when(prizeRepository.findByIdWithLock(anyLong()))
                .thenAnswer(invocation -> {
                    double random = Math.random();
                    if (random < 0.1) return Optional.of(prize1);
                    if (random < 0.3) return Optional.of(prize2);
                    return Optional.of(noPrize);
                });
        when(drawRecordRepository.save(any(DrawRecord.class)))
                .thenAnswer(invocation -> {
                    DrawRecord record = invocation.getArgument(0);
                    record.setId(System.currentTimeMillis());
                    return record;
                });
        when(statisticsRepository.save(any(UserDrawStatistics.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        DrawResponse response = lotteryService.draw(1L, request);

        // Assert
        assertNotNull(response);
        assertEquals(1000, response.getResults().size());

        long winningCount = response.getResults().stream()
                .filter(DrawResult::getIsWinning)
                .count();

        // 驗證中獎率在合理範圍內 (10% + 20% = 30%, 允許±10%誤差)
        assertTrue(winningCount >= 200 && winningCount <= 400,
                "中獎次數應在200-400之間，實際：" + winningCount);
    }

    @Test
    @DisplayName("測試每日限制模式")
    void testDailyLimitMode() {
        // Arrange
        testActivity.setLimitType("DAILY");
        testActivity.setMaxDrawsPerUser(3);

        DrawRequest request = new DrawRequest(1L, 3);
        LocalDate today = LocalDate.now();

        UserDailyDrawStatistics dailyStats = new UserDailyDrawStatistics();
        dailyStats.setUserId(1L);
        dailyStats.setActivityId(1L);
        dailyStats.setDrawDate(today);
        dailyStats.setDailyDraws(0);
        dailyStats.setDailyWinningDraws(0);

        when(activityRepository.findById(1L)).thenReturn(Optional.of(testActivity));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(dailyStatisticsRepository.findByUserIdAndActivityIdAndDrawDate(1L, 1L, today))
                .thenReturn(Optional.of(dailyStats));
        when(prizeRepository.findByActivityId(1L))
                .thenReturn(Arrays.asList(prize1, prize2, noPrize));
        when(prizeRepository.findByIdWithLock(anyLong()))
                .thenReturn(Optional.of(noPrize));
        when(drawRecordRepository.save(any(DrawRecord.class)))
                .thenAnswer(invocation -> {
                    DrawRecord record = invocation.getArgument(0);
                    record.setId(System.currentTimeMillis());
                    return record;
                });
        when(dailyStatisticsRepository.save(any(UserDailyDrawStatistics.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        DrawResponse response = lotteryService.draw(1L, request);

        // Assert
        assertNotNull(response);
        assertEquals(3, response.getDrawCount());
        assertEquals(0, response.getRemainingDraws(), "每日次數應該用完");
        assertEquals(3, dailyStats.getDailyDraws());

        verify(dailyStatisticsRepository).save(any(UserDailyDrawStatistics.class));
    }

    @Test
    @DisplayName("測試總次數限制模式")
    void testTotalLimitMode() {
        // Arrange
        testActivity.setLimitType("TOTAL");
        testActivity.setMaxDrawsPerUser(5);

        DrawRequest request1 = new DrawRequest(1L, 3);
        UserDrawStatistics statistics = new UserDrawStatistics();
        statistics.setTotalDraws(0);
        statistics.setUser(testUser);
        statistics.setActivity(testActivity);

        when(activityRepository.findById(1L)).thenReturn(Optional.of(testActivity));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(statisticsRepository.findByUserIdAndActivityIdWithLock(1L, 1L))
                .thenReturn(Optional.of(statistics));
        when(statisticsRepository.findByUserIdAndActivityId(1L, 1L))
                .thenReturn(Optional.of(statistics));
        when(prizeRepository.findByActivityId(1L))
                .thenReturn(Arrays.asList(prize1, prize2, noPrize));
        when(prizeRepository.findByIdWithLock(anyLong()))
                .thenReturn(Optional.of(noPrize));
        when(drawRecordRepository.save(any(DrawRecord.class)))
                .thenAnswer(invocation -> {
                    DrawRecord record = invocation.getArgument(0);
                    record.setId(System.currentTimeMillis());
                    return record;
                });
        when(statisticsRepository.save(any(UserDrawStatistics.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act - 第一次抽 3 次
        DrawResponse response1 = lotteryService.draw(1L, request1);

        // Assert
        assertNotNull(response1);
        assertEquals(3, response1.getDrawCount());
        assertEquals(2, response1.getRemainingDraws(), "剩餘 2 次");

        // Act - 第二次抽 2 次
        DrawRequest request2 = new DrawRequest(1L, 2);
        DrawResponse response2 = lotteryService.draw(1L, request2);

        // Assert
        assertNotNull(response2);
        assertEquals(2, response2.getDrawCount());
        assertEquals(0, response2.getRemainingDraws(), "總次數用完");
        assertEquals(5, statistics.getTotalDraws());
    }
}
