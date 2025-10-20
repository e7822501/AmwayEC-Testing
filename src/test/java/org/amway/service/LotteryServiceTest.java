package org.amway.service;

import org.amway.dto.request.DrawRequest;
import org.amway.dto.response.DrawResponse;
import org.amway.dto.response.DrawResult;
import org.amway.entity.*;
import org.amway.exception.InsufficientDrawsException;
import org.amway.exception.InvalidActivityException;
import org.amway.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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

        // 若測試未用到鎖則用 lenient() 包裝
        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
        lenient().when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);
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
        when(prizeRepository.findByActivityId(1L))
                .thenReturn(Arrays.asList(prize1, prize2, noPrize));

        // 使用 lenient() 因為這個 stub 可能不會被調用（取決於機率）
        lenient().when(prizeRepository.findByIdWithLock(anyLong()))
                .thenReturn(Optional.of(prize1));

        when(drawRecordRepository.save(any(DrawRecord.class)))
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
        when(prizeRepository.findByActivityId(1L))
                .thenReturn(Arrays.asList(prize1, prize2, noPrize));
        when(prizeRepository.findByIdWithLock(anyLong()))
                .thenReturn(Optional.of(noPrize));
        when(drawRecordRepository.save(any(DrawRecord.class)))
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
        when(statisticsRepository.findByUserIdAndActivityIdWithLock(1L, 1L))
                .thenReturn(Optional.of(statistics));

        // Act & Assert
        assertThrows(InsufficientDrawsException.class, () -> {
            lotteryService.draw(1L, request);
        });
    }

    @Test
    @DisplayName("測試活動不存在")
    void testActivityNotFound() {
        // Arrange
        DrawRequest request = new DrawRequest(999L, 1);

        when(activityRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InvalidActivityException.class, () -> {
            lotteryService.draw(1L, request);
        });
    }

    @Test
    @DisplayName("測試活動已結束")
    void testActivityEnded() {
        // Arrange
        DrawRequest request = new DrawRequest(1L, 1);
        testActivity.setEndTime(LocalDateTime.now().minusDays(1));

        when(activityRepository.findById(1L)).thenReturn(Optional.of(testActivity));

        // Act & Assert
        assertThrows(InvalidActivityException.class, () -> {
            lotteryService.draw(1L, request);
        });
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
        when(prizeRepository.findByActivityId(1L))
                .thenReturn(Arrays.asList(prize1, prize2, noPrize));
        when(prizeRepository.findByIdWithLock(anyLong()))
                .thenReturn(Optional.of(prize1));
        when(drawRecordRepository.save(any(DrawRecord.class)))
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
}