# 電商抽獎系統 - 技術架構與實現說明

## 一、系統架構設計

### 1.1 整體架構
```
┌─────────────┐
│   用戶端     │
│  (Browser)  │
└──────┬──────┘
       │ HTTPS
       ▼
┌─────────────────────────────────────┐
│         Nginx (負載均衡)             │
└──────────┬──────────────────────────┘
           │
    ┌──────┴──────┐
    ▼             ▼
┌─────────┐  ┌─────────┐
│ App     │  │ App     │  (多實例水平擴展)
│ Server  │  │ Server  │
│ (8080)  │  │ (8081)  │
└────┬────┘  └────┬────┘
     │            │
     └──────┬─────┘
            │
    ┌───────┴────────┐
    ▼                ▼
┌─────────┐    ┌──────────┐
│  MySQL  │    │  Redis   │
│ (3306)  │    │  (6379)  │
└─────────┘    └──────────┘
```

### 1.2 技術棧分層

**表現層 (Presentation Layer)**
- Spring MVC
- RESTful API
- Swagger/OpenAPI 3.0 (API文檔)

**業務邏輯層 (Business Layer)**
- Spring Service
- 抽獎核心算法
- 機率計算引擎
- 風控邏輯

**數據訪問層 (Data Access Layer)**
- Spring Data JPA
- Hibernate ORM
- Repository Pattern

**基礎設施層 (Infrastructure Layer)**
- Spring Security + JWT (認證授權)
- Redisson (分佈式鎖)
- Redis (緩存)
- MySQL (持久化存儲)

## 二、核心功能實現

### 2.1 抽獎核心算法

#### 機率選擇算法 (輪盤賭算法)

```java
private Prize selectPrizeByProbability(List<Prize> prizes) {
    double random = Math.random();  // 生成 [0, 1) 隨機數
    double cumulativeProbability = 0.0;
    
    for (Prize prize : prizes) {
        cumulativeProbability += prize.getProbability().doubleValue();
        if (random <= cumulativeProbability) {
            return prize;
        }
    }
    return prizes.get(prizes.size() - 1); // 容錯處理
}
```

**示例：**
- iPhone: 10% (0.0 - 0.1)
- AirPods: 20% (0.1 - 0.3)
- 購物金: 30% (0.3 - 0.6)
- 銘謝惠顧: 40% (0.6 - 1.0)

隨機數落在哪個區間，就中哪個獎品。

### 2.2 並發控制方案

#### 2.2.1 分佈式鎖（Redisson）

**目的：** 防止同一用戶併發重複抽獎

```java
String lockKey = "lottery:draw:{userId}:{activityId}";
RLock lock = redissonClient.getLock(lockKey);
boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);
```

**鎖粒度：** 用戶+活動維度
**超時設置：** 獲取鎖等待10秒，持有鎖最多30秒

#### 2.2.2 數據庫悲觀鎖

**目的：** 防止獎品庫存超抽

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Prize p WHERE p.id = :prizeId")
Optional<Prize> findByIdWithLock(@Param("prizeId") Long prizeId);
```

在更新庫存前，先使用 `SELECT ... FOR UPDATE` 鎖定記錄。

### 2.3 數據一致性保證

#### 事務管理

```java
@Transactional(rollbackFor = Exception.class)
public DrawResponse draw(Long userId, DrawRequest request) {
    // 所有操作在同一事務中
    // 任何異常都會回滾
}
```

#### 統計數據維護

使用 `user_draw_statistics` 表快速查詢用戶抽獎次數，避免每次都統計 `draw_records` 表。

```sql
SELECT total_draws FROM user_draw_statistics 
WHERE user_id = ? AND activity_id = ?
```

### 2.4 防超抽機制

**流程：**

1. 檢查用戶剩餘次數
2. 獲取分佈式鎖
3. 根據機率選擇獎品
4. 使用悲觀鎖查詢獎品
5. 檢查庫存 > 0
6. 扣減庫存 (remaining_stock - 1)
7. 記錄抽獎結果
8. 更新統計數據
9. 釋放鎖

**關鍵點：**
- 第5步如果庫存不足，降級為"銘謝惠顧"
- 使用數據庫約束 `CHECK (remaining_stock >= 0)` 作為最後防線

## 三、API設計

### 3.1 RESTful API 規範

| 方法   | 路徑                        | 描述           |
|--------|----------------------------|----------------|
| POST   | /api/auth/login            | 用戶登入        |
| GET    | /api/activities            | 獲取活動列表    |
| GET    | /api/activities/{id}       | 獲取活動詳情    |
| POST   | /api/lottery/draw          | 執行抽獎        |
| GET    | /api/lottery/history       | 查詢抽獎歷史    |
| GET    | /api/lottery/remaining-draws | 查詢剩餘次數  |

### 3.2 認證機制

**JWT Token 結構：**

```json
{
  "sub": "user1",
  "userId": 2,
  "role": "USER",
  "iat": 1697700000,
  "exp": 1697786400
}
```

**使用方式：**

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 3.3 統一響應格式

```json
{
  "success": true,
  "message": "操作成功",
  "data": { ... },
  "timestamp": "2025-10-19T14:30:00"
}
```

## 四、數據庫設計

### 4.1 核心表關係

```
users (用戶表)
  │
  ├─→ user_draw_statistics (統計表)
  │     └─→ lottery_activities (活動表)
  │
  └─→ draw_records (記錄表)
        ├─→ lottery_activities
        └─→ prizes (獎品表)
              └─→ lottery_activities
```

### 4.2 索引設計

**高頻查詢優化：**

1. `user_draw_statistics`
    - UNIQUE KEY (user_id, activity_id) - 快速查詢用戶統計

2. `draw_records`
    - INDEX (user_id, activity_id) - 查詢用戶抽獎歷史
    - INDEX (activity_id, draw_time) - 查詢活動抽獎記錄

3. `prizes`
    - INDEX (activity_id) - 查詢活動獎品
    - INDEX (remaining_stock) - 查詢可用獎品

## 五、性能優化

### 5.1 緩存策略

**Redis 緩存：**

```java
@Cacheable(value = "activity", key = "#activityId")
public ActivityResponse getActivityDetail(Long activityId) {
    // 活動信息緩存1小時
}
```

**緩存內容：**
- 活動基本信息
- 獎品列表
- TTL: 1小時

### 5.2 連接池配置

**HikariCP (數據庫連接池):**

```yaml
hikari:
  maximum-pool-size: 20
  minimum-idle: 5
  connection-timeout: 30000
```

**Redis 連接池:**

```yaml
lettuce:
  pool:
    max-active: 20
    max-idle: 10
    min-idle: 5
```

### 5.3 水平擴展

**無狀態設計：**
- JWT 認證（無需 Session）
- 分佈式鎖（跨實例共享）
- 緩存共享（Redis）

**負載均衡：**
- Nginx 輪詢分發請求
- 支持動態增減實例

## 六、安全設計

### 6.1 認證與授權

**Spring Security 配置：**

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**", "/api/activities/**").permitAll()
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .anyRequest().authenticated()
)
```

### 6.2 輸入驗證

**Jakarta Validation：**

```java
@NotNull(message = "活動ID不能為空")
private Long activityId;

@Min(value = 1, message = "抽獎次數至少為1")
private Integer drawCount;
```

### 6.3 錯誤處理

**全局異常處理器：**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(InsufficientDrawsException.class)
    public ResponseEntity<ApiResponse<Void>> handle(...) {
        // 統一錯誤響應
    }
}
```

## 七、測試策略

### 7.1 單元測試

**測試覆蓋：**
- ✅ 正常抽獎流程
- ✅ 邊界條件（次數用盡、庫存為0）
- ✅ 異常場景（活動不存在、已結束）
- ✅ 機率分布驗證

**Mock 策略：**
- Mock Repository 層
- Mock Redis 分佈式鎖
- 使用 H2 內存數據庫

### 7.2 單元測試

使用 `@SpringBootTest` 測試完整流程。

## 八、部署架構

### 8.1 環境變數配置

**開發環境：**
- DB_URL: localhost
- REDIS_HOST: localhost

**生產環境：**
- DB_URL: mysql-cluster.prod
- REDIS_HOST: redis-cluster.prod
- JWT_SECRET: 使用環境變數注入


## 九、總結

本系統採用 Spring Boot 3.2 + MySQL + Redis 實現了一個**高可用、高併發、高一致性**的電商抽獎系統。

**核心亮點：**

1. ✅ **分佈式架構** - 支持水平擴展
2. ✅ **雙重鎖機制** - 保證數據一致性
3. ✅ **靈活配置** - 環境變數動態切換
4. ✅ **完善的測試** - 單元測試覆蓋核心邏輯
5. ✅ **RESTful API** - 前後端分離
6. ✅ **API 文檔** - Swagger 自動生成
7. ✅ **安全設計** - JWT + 權限控制