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

#### 表現層 (Presentation Layer)
- Spring MVC
- RESTful API
- Swagger/OpenAPI 3.0 (API文檔)

#### 業務邏輯層 (Business Layer)
- Spring Service
- 抽獎核心算法
- 機率計算引擎
- 風控邏輯

#### 數據訪問層 (Data Access Layer)
- Spring Data JPA
- Hibernate ORM
- Repository Pattern

#### 基礎設施層 (Infrastructure Layer)
- Spring Security + JWT (認證授權)
- Redisson (分佈式鎖)
- Redis (緩存)
- MySQL (持久化存儲)

---

## 二、核心功能實現

### 2.1 抽獎核心算法

#### 機率選擇算法 (輪盤賭算法)

**算法原理：**

將所有獎品的機率累加，生成一個 [0, 1) 的隨機數，看隨機數落在哪個區間，就選中哪個獎品。

**實現代碼：**

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
    
    // 容錯處理：返回最後一個（通常是銘謝惠顧）
    return prizes.get(prizes.size() - 1);
}
```

**機率分布示例：**

| 獎品 | 機率 | 累積機率區間 |
|------|------|-------------|
| iPhone | 10% | [0.0, 0.1) |
| AirPods | 20% | [0.1, 0.3) |
| 購物金 | 30% | [0.3, 0.6) |
| 銘謝惠顧 | 40% | [0.6, 1.0) |

**示例：**
- 隨機數 = 0.05 → 中 iPhone
- 隨機數 = 0.25 → 中 AirPods
- 隨機數 = 0.75 → 中 銘謝惠顧

---

### 2.2 並發控制方案

#### 三層防護機制

```
用戶發起抽獎請求
    ↓
[第一層] 分佈式鎖 (Redisson)
  └─ 防止同一用戶併發重複抽獎
  └─ 鎖粒度：userId + activityId
  └─ 超時設置：等待 10s，持有 30s
    ↓
[第二層] 數據庫悲觀鎖
  └─ SELECT ... FOR UPDATE
  └─ 防止獎品庫存超抽
  └─ 原子性操作：檢查 + 扣減
    ↓
[第三層] 事務管理
  └─ @Transactional
  └─ 任何異常自動回滾
  └─ 保證統計數據一致性
    ↓
  ✅ 100% 一致性保證
```

#### 2.2.1 分佈式鎖（Redisson）

**目的：** 防止同一用戶併發重複抽獎

**實現：**

```java
String lockKey = String.format("lottery:draw:%d:%d", userId, activityId);
RLock lock = redissonClient.getLock(lockKey);

boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);
if (!isLocked) {
    throw new BusinessException("系統繁忙，請稍後再試");
}

try {
    // 執行抽獎邏輯
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

**鎖特性：**
- **鎖粒度：** 用戶 + 活動維度（每個用戶在每個活動中獨立加鎖）
- **超時設置：** 獲取鎖等待 10 秒，持有鎖最多 30 秒
- **自動續期：** Redisson 自動延長鎖時間（看門狗機制）

#### 2.2.2 數據庫悲觀鎖

**目的：** 防止獎品庫存超抽

**實現：**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Prize p WHERE p.id = :prizeId")
Optional<Prize> findByIdWithLock(@Param("prizeId") Long prizeId);
```

**SQL 實際執行：**

```sql
SELECT * FROM prizes WHERE id = ? FOR UPDATE;
```

**工作原理：**
1. 事務 A 執行 `SELECT ... FOR UPDATE`，鎖定 `prize_id = 1` 的記錄
2. 事務 B 也想鎖定 `prize_id = 1`，必須等待事務 A 提交或回滾
3. 事務 A 檢查庫存 → 扣減庫存 → 提交事務
4. 事務 B 獲得鎖，此時看到的是事務 A 扣減後的庫存

---

### 2.3 數據一致性保證

#### 2.3.1 事務管理

```java
@Transactional(rollbackFor = Exception.class)
public DrawResponse draw(Long userId, DrawRequest request) {
    // 1. 檢查剩餘次數
    // 2. 執行抽獎
    // 3. 扣減庫存
    // 4. 記錄抽獎結果
    // 5. 更新統計數據
    
    // 以上操作任一步驟出錯，全部回滾
}
```

**回滾場景：**
- 數據庫異常
- 庫存不足（業務異常）
- Redis 連接失敗
- 任何未捕獲的 `RuntimeException`

#### 2.3.2 統計數據維護

**設計思路：**

使用 `user_draw_statistics` 表維護用戶抽獎次數，避免每次都查詢 `draw_records` 表。

**查詢效率對比：**

| 方案 | SQL | 性能 |
|------|-----|------|
| 方案 A | `SELECT COUNT(*) FROM draw_records WHERE user_id = ? AND activity_id = ?` | 慢（全表掃描或索引範圍掃描） |
| 方案 B | `SELECT total_draws FROM user_draw_statistics WHERE user_id = ? AND activity_id = ?` | 快（唯一索引定位） |

**更新策略：**

```java
// 每次抽獎後更新統計表
statistics.incrementDrawCount();
if (result.getIsWinning()) {
    statistics.incrementWinningCount();
}
statisticsRepository.save(statistics);
```

**一致性保證：**
- 統計表和記錄表在同一事務中更新
- 事務回滾時，統計數據也會回滾

---

### 2.4 防超抽機制

#### 完整流程

```java
@Transactional
public DrawResponse draw(Long userId, DrawRequest request) {
    // 1. 檢查用戶剩餘次數
    int remainingDraws = checkAndGetRemainingDraws(user, activity);
    if (remainingDraws < request.getDrawCount()) {
        throw new InsufficientDrawsException();
    }
    
    // 2. 獲取分佈式鎖
    RLock lock = redissonClient.getLock(lockKey);
    lock.tryLock(10, 30, TimeUnit.SECONDS);
    
    try {
        for (int i = 0; i < request.getDrawCount(); i++) {
            // 3. 根據機率選擇獎品
            Prize selectedPrize = selectPrizeByProbability(prizes);
            
            if (selectedPrize.getPrizeType() != Prize.PrizeType.NO_PRIZE) {
                // 4. 使用悲觀鎖查詢獎品
                Prize lockedPrize = prizeRepository.findByIdWithLock(selectedPrize.getId())
                    .orElseThrow();
                
                // 5. 檢查庫存 > 0
                if (lockedPrize.hasStock()) {
                    // 6. 扣減庫存（原子操作）
                    lockedPrize.decreaseStock(1);
                    prizeRepository.save(lockedPrize);
                    
                    // 7. 記錄中獎
                    record.setIsWinning(true);
                    record.setPrize(lockedPrize);
                } else {
                    // 庫存不足，降級為銘謝惠顧
                    record.setIsWinning(false);
                    record.setPrizeName("銘謝惠顧");
                }
            }
            
            // 8. 記錄抽獎結果
            drawRecordRepository.save(record);
        }
        
        // 9. 更新統計數據
        statistics.incrementDrawCount();
        statisticsRepository.save(statistics);
        
    } finally {
        // 10. 釋放鎖
        lock.unlock();
    }
}
```

#### 關鍵防護點

**第 5 步：庫存檢查**
```java
public boolean hasStock() {
    return remainingStock > 0;
}
```

**第 6 步：原子扣減**
```java
public void decreaseStock(int quantity) {
    if (remainingStock < quantity) {
        throw new PrizeStockException("庫存不足");
    }
    this.remainingStock -= quantity;
}
```

**數據庫層約束（最後防線）：**
```sql
CONSTRAINT chk_stock CHECK (remaining_stock >= 0)
```

---

## 三、API 設計

### 3.1 RESTful API 規範

| 方法   | 路徑                            | 描述           | 權限 |
|--------|--------------------------------|----------------|------|
| POST   | `/api/auth/login`              | 用戶登入        | 公開 |
| POST   | `/api/auth/refresh`            | 刷新 Token      | 公開 |
| POST   | `/api/auth/logout`             | 用戶登出        | 認證 |
| GET    | `/api/activities`              | 獲取活動列表    | 公開 |
| GET    | `/api/activities/{id}`         | 獲取活動詳情    | 公開 |
| POST   | `/api/lottery/draw`            | 執行抽獎        | 認證 |
| GET    | `/api/lottery/history`         | 查詢抽獎歷史    | 認證 |
| GET    | `/api/lottery/remaining-draws` | 查詢剩餘次數    | 認證 |
| POST   | `/api/admin/activities`        | 創建活動        | 管理員 |
| PUT    | `/api/admin/prizes/{id}`       | 修改獎品        | 管理員 |

---

### 3.2 認證機制

#### JWT Token 結構

**Payload 內容：**

```json
{
  "sub": "user1",          // 用戶名
  "userId": 2,             // 用戶ID
  "role": "USER",          // 角色
  "iat": 1697700000,       // 簽發時間
  "exp": 1697786400        // 過期時間
}
```

**使用方式：**

```http
GET /api/lottery/history
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMSIsInVzZXJJZCI6Miwicm9sZSI6IlVTRVIiLCJpYXQiOjE2OTc3MDAwMDAsImV4cCI6MTY5Nzc4NjQwMH0.xyz...
```

#### Token 刷新機制

**Access Token：** 短期有效（1 小時）
**Refresh Token：** 長期有效（7 天）

```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response：**

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

#### Token 黑名單機制

**登出流程：**

```java
public void logout(String token, Long userId) {
    // 1. 將 Access Token 加入黑名單
    jwtUtil.blacklistToken(token);
    
    // 2. 撤銷 Refresh Token
    jwtUtil.revokeRefreshToken(userId);
}
```

**Redis 存儲結構：**

```
黑名單: blacklist:{token} → "1"   (TTL: token剩餘有效期)
白名單: refresh_token:{userId} → {refreshToken}  (TTL: 7天)
```

---

### 3.3 統一響應格式

#### 成功響應

```json
{
  "success": true,
  "message": "操作成功",
  "data": {
    "results": [...],
    "drawCount": 3,
    "remainingDraws": 2
  },
  "timestamp": "2025-10-30T21:30:00"
}
```

#### 錯誤響應

```json
{
  "success": false,
  "errorCode": "INSUFFICIENT_DRAWS",
  "message": "剩餘抽獎次數不足，剩餘：0次，請求：1次",
  "path": "/api/lottery/draw",
  "timestamp": "2025-10-30T21:30:00",
  "traceId": "abc123def456"
}
```

#### HTTP Status Code 映射

| 業務錯誤 | HTTP Status | ErrorCode |
|---------|-------------|-----------|
| 參數驗證失敗 | 400 Bad Request | INVALID_PARAMETER |
| 未登入 | 401 Unauthorized | UNAUTHORIZED |
| 權限不足 | 403 Forbidden | FORBIDDEN |
| 資源不存在 | 404 Not Found | RESOURCE_NOT_FOUND |
| 抽獎次數不足 | 422 Unprocessable Entity | INSUFFICIENT_DRAWS |
| 活動未開始 | 422 Unprocessable Entity | ACTIVITY_NOT_ACTIVE |
| 獎品庫存不足 | 422 Unprocessable Entity | PRIZE_OUT_OF_STOCK |
| 請求過於頻繁 | 429 Too Many Requests | RATE_LIMIT_EXCEEDED |
| 系統異常 | 500 Internal Server Error | INTERNAL_ERROR |
| 服務不可用 | 503 Service Unavailable | SERVICE_UNAVAILABLE |

---

## 四、數據庫設計

### 4.1 核心表關係

```
users (用戶表)
  │
  ├─→ user_draw_statistics (統計表)
  │     └─→ lottery_activities (活動表)
  │
  ├─→ user_daily_draw_statistics (每日統計表)
  │     └─→ lottery_activities
  │
  └─→ draw_records (抽獎記錄表)
        ├─→ lottery_activities
        └─→ prizes (獎品表)
              └─→ lottery_activities
```

### 4.2 表結構詳解

#### users (用戶表)

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | BIGINT | 主鍵 |
| username | VARCHAR(50) | 用戶名（唯一） |
| password | VARCHAR(255) | 密碼（BCrypt加密） |
| email | VARCHAR(100) | 郵箱 |
| role | VARCHAR(20) | 角色（USER/ADMIN） |
| vip_level | INT | VIP等級 |
| status | VARCHAR(20) | 狀態（ACTIVE/INACTIVE） |

**索引：**
- PRIMARY KEY (id)
- UNIQUE KEY (username)
- INDEX (email)

#### lottery_activities (活動表)

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | BIGINT | 主鍵 |
| name | VARCHAR(100) | 活動名稱 |
| description | TEXT | 活動描述 |
| start_time | TIMESTAMP | 開始時間 |
| end_time | TIMESTAMP | 結束時間 |
| limit_type | VARCHAR(20) | 限制類型（TOTAL/DAILY/WEEKLY） |
| max_draws_per_user | INT | 每人抽獎次數上限 |
| status | VARCHAR(20) | 狀態（ACTIVE/INACTIVE/ENDED） |

**索引：**
- PRIMARY KEY (id)
- INDEX (status)
- INDEX (start_time, end_time)

#### prizes (獎品表)

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | BIGINT | 主鍵 |
| activity_id | BIGINT | 活動ID（外鍵） |
| name | VARCHAR(100) | 獎品名稱 |
| description | TEXT | 獎品描述 |
| total_stock | INT | 總庫存 |
| remaining_stock | INT | 剩餘庫存 |
| probability | DECIMAL(10,6) | 中獎機率（0-1） |
| prize_type | VARCHAR(20) | 類型（PHYSICAL/VIRTUAL/NO_PRIZE） |
| image_url | VARCHAR(255) | 圖片URL |

**約束：**
- FOREIGN KEY (activity_id) REFERENCES lottery_activities(id)
- CHECK (probability >= 0 AND probability <= 1)
- CHECK (remaining_stock >= 0 AND remaining_stock <= total_stock)

**索引：**
- PRIMARY KEY (id)
- INDEX (activity_id)
- INDEX (remaining_stock)

#### draw_records (抽獎記錄表)

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | BIGINT | 主鍵 |
| activity_id | BIGINT | 活動ID（外鍵） |
| user_id | BIGINT | 用戶ID（外鍵） |
| prize_id | BIGINT | 獎品ID（外鍵，可為NULL） |
| draw_time | TIMESTAMP | 抽獎時間 |
| draw_date | DATE | 抽獎日期（生成列） |
| is_winning | BOOLEAN | 是否中獎 |
| prize_name | VARCHAR(100) | 獎品名稱（冗餘存儲） |
| status | VARCHAR(20) | 狀態（COMPLETED/FAILED） |

**索引：**
- PRIMARY KEY (id)
- INDEX (user_id, activity_id)
- INDEX (user_id, activity_id, draw_date)
- INDEX (activity_id, draw_time)

#### user_draw_statistics (用戶統計表)

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | BIGINT | 主鍵 |
| user_id | BIGINT | 用戶ID（外鍵） |
| activity_id | BIGINT | 活動ID（外鍵） |
| total_draws | INT | 總抽獎次數 |
| winning_draws | INT | 中獎次數 |

**約束：**
- UNIQUE KEY (user_id, activity_id)

**索引：**
- PRIMARY KEY (id)
- UNIQUE KEY (user_id, activity_id)

#### user_daily_draw_statistics (每日統計表)

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | BIGINT | 主鍵 |
| user_id | BIGINT | 用戶ID |
| activity_id | BIGINT | 活動ID |
| draw_date | DATE | 抽獎日期 |
| daily_draws | INT | 當日抽獎次數 |
| daily_winning_draws | INT | 當日中獎次數 |

**約束：**
- UNIQUE KEY (user_id, activity_id, draw_date)

**索引：**
- PRIMARY KEY (id)
- UNIQUE KEY (user_id, activity_id, draw_date)
- INDEX (activity_id, draw_date)

---

### 4.3 索引設計策略

#### 高頻查詢優化

**查詢 1：查詢用戶剩餘抽獎次數**

```sql
SELECT total_draws FROM user_draw_statistics 
WHERE user_id = ? AND activity_id = ?;
```

**索引：** UNIQUE KEY (user_id, activity_id) ✅

---

**查詢 2：查詢用戶抽獎歷史**

```sql
SELECT * FROM draw_records 
WHERE user_id = ? AND activity_id = ? 
ORDER BY draw_time DESC;
```

**索引：** INDEX (user_id, activity_id) ✅

---

**查詢 3：查詢活動獎品列表**

```sql
SELECT * FROM prizes WHERE activity_id = ?;
```

**索引：** INDEX (activity_id) ✅

---

**查詢 4：查詢有庫存的獎品**

```sql
SELECT * FROM prizes 
WHERE activity_id = ? AND remaining_stock > 0;
```

**索引：** INDEX (activity_id, remaining_stock) （複合索引更佳）

---

## 五、性能優化

### 5.1 緩存策略

#### Redis 緩存層次

**L1 緩存：活動基本信息**

```java
@Cacheable(value = "activity", key = "#activityId", unless = "#result == null")
public ActivityResponse getActivityDetail(Long activityId) {
    // 活動信息緩存 1 小時
}
```

**Key：** `activity:1`  
**TTL：** 3600 秒  
**緩存內容：**
```json
{
  "id": 1,
  "name": "雙11抽獎",
  "startTime": "2025-11-01T00:00:00",
  "endTime": "2025-11-11T23:59:59",
  "maxDrawsPerUser": 5
}
```

---

**L2 緩存：獎品列表**

```java
@Cacheable(value = "prizes", key = "#activityId")
public List<Prize> getPrizesByActivity(Long activityId) {
    // 獎品列表緩存 30 分鐘
}
```

**Key：** `prizes:1`  
**TTL：** 1800 秒

---

**緩存失效策略：**

```java
@CacheEvict(value = "prizes", key = "#prize.activity.id")
public Prize updatePrize(Prize prize) {
    // 更新獎品時，清除該活動的獎品緩存
}
```

---

### 5.2 連接池配置

#### HikariCP (數據庫連接池)

**最佳實踐配置：**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20        # 最大連接數
      minimum-idle: 5              # 最小空閒連接
      connection-timeout: 30000    # 連接超時（30秒）
      idle-timeout: 600000         # 空閒超時（10分鐘）
      max-lifetime: 1800000        # 最大生命週期（30分鐘）
      pool-name: LotteryHikariPool
```

**連接數計算公式：**

```
connections = ((core_count × 2) + effective_spindle_count)
```

示例：8核CPU + 1個硬盤 = (8 × 2) + 1 = 17 ≈ 20

---

#### Redis 連接池 (Lettuce)

```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 20    # 最大活躍連接
        max-idle: 10      # 最大空閒連接
        min-idle: 5       # 最小空閒連接
        max-wait: 5000    # 獲取連接最大等待時間（5秒）
    timeout: 5000ms       # Redis 命令超時
```

---

### 5.3 水平擴展設計

#### 無狀態設計原則

**✅ JWT 認證（無需 Session）**

```
傳統 Session：
  用戶登入 → Session 存在 Server A
  下次請求到 Server B → 找不到 Session ❌

JWT Token：
  用戶登入 → 返回 Token
  下次請求到任意 Server → 驗證 Token ✅
```

**✅ 分佈式鎖（跨實例共享）**

```
Server A 獲取鎖: lottery:draw:1:1 → Redis
Server B 嘗試獲取同一把鎖 → 等待 Server A 釋放
```

**✅ 緩存共享（Redis）**

```
Server A 寫入緩存: activity:1 → Redis
Server B 讀取緩存: activity:1 ← Redis
```

---

#### 負載均衡配置 (Nginx)

```nginx
upstream lottery_backend {
    # 輪詢策略
    server 192.168.1.10:8080 weight=1;
    server 192.168.1.11:8080 weight=1;
    server 192.168.1.12:8080 weight=1;
    
    # 健康檢查
    check interval=3000 rise=2 fall=5 timeout=1000;
}

server {
    listen 80;
    server_name lottery.example.com;
    
    location /api/ {
        proxy_pass http://lottery_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

---

#### 動態擴縮容

**擴容步驟：**

1. 啟動新實例：`docker run -d lottery-system:1.0.0`
2. 健康檢查通過後，Nginx 自動加入負載均衡
3. 開始接收流量

**縮容步驟：**

1. Nginx 標記實例為 `down`
2. 等待現有請求處理完成（優雅關閉）
3. 停止實例

---

### 5.4 限流策略

#### 全局限流 (Guava RateLimiter)

```java
@Configuration
public class RateLimiterConfig {
    @Bean
    public RateLimiter globalRateLimiter() {
        return RateLimiter.create(1000.0);  // 每秒最多 1000 次請求
    }
}
```

#### 用戶維度限流

```java
private final ConcurrentHashMap<Long, RateLimiter> userRateLimiters = new ConcurrentHashMap<>();

public void checkUserRateLimit(Long userId) {
    RateLimiter limiter = userRateLimiters.computeIfAbsent(
        userId, 
        k -> RateLimiter.create(1.0)  // 每個用戶每秒最多 1 次
    );
    
    if (!limiter.tryAcquire()) {
        throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
    }
}
```

---

## 六、安全設計

### 6.1 認證與授權

#### Spring Security 配置

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 公開 API
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/activities/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()
                
                // 管理員 API
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                
                // 其他需要認證
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter(), 
                UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

#### JWT 過濾器

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) {
        String token = extractToken(request);
        
        if (token != null && jwtUtil.validateToken(token)) {
            Long userId = jwtUtil.extractUserId(token);
            String role = jwtUtil.extractRole(token);
            
            Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        
        filterChain.doFilter(request, response);
    }
}
```

---

### 6.2 輸入驗證

#### Jakarta Validation

```java
public class DrawRequest {
    
    @NotNull(message = "活動ID不能為空")
    private Long activityId;
    
    @NotNull(message = "抽獎次數不能為空")
    @Min(value = 1, message = "抽獎次數至少為1")
    @Max(value = 10, message = "單次最多抽10次")
    private Integer drawCount;
}
```

**使用：**

```java
@PostMapping("/draw")
public ApiResponse<DrawResponse> draw(
        @Valid @RequestBody DrawRequest request,
        Authentication authentication) {
    // @Valid 自動觸發驗證
}
```

**驗證失敗響應：**

```json
{
  "success": false,
  "errorCode": "INVALID_PARAMETER",
  "message": "參數驗證失敗",
  "details": {
    "drawCount": "抽獎次數至少為1"
  }
}
```

---

### 6.3 錯誤處理

#### 全局異常處理器

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        
        String traceId = UUID.randomUUID().toString().substring(0, 16);
        log.warn("[{}] 業務異常: {}", traceId, ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
            .success(false)
            .errorCode(ex.getErrorCode().getCode())
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .traceId(traceId)
            .build();
        
        return ResponseEntity
            .status(ex.getErrorCode().getHttpStatus())
            .body(response);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        // ... 返回錯誤響應
    }
}
```

---

## 七、測試策略

### 7.1 單元測試

#### 測試覆蓋範圍

**✅ 正常流程測試**

```java
@Test
@DisplayName("測試單次抽獎成功")
void testSingleDrawSuccess() {
    // Arrange
    DrawRequest request = new DrawRequest(1L, 1);
    when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
    
    // Act
    DrawResponse response = lotteryService.draw(1L, request);
    
    // Assert
    assertEquals(1, response.getDrawCount());
    assertEquals(4, response.getRemainingDraws());
}
```

**✅ 邊界條件測試**

```java
@Test
@DisplayName("測試抽獎次數用盡")
void testInsufficientDraws() {
    // 用戶已抽 5 次，再抽應該失敗
    statistics.setTotalDraws(5);
    
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> lotteryService.draw(1L, new DrawRequest(1L, 1))
    );
    
    assertEquals(ErrorCode.INSUFFICIENT_DRAWS, exception.getErrorCode());
}
```

**✅ 異常場景測試**

```java
@Test
@DisplayName("測試活動已結束")
void testActivityEnded() {
    activity.setEndTime(LocalDateTime.now().minusDays(1));
    
    assertThrows(
        BusinessException.class,
        () -> lotteryService.draw(1L, new DrawRequest(1L, 1))
    );
}
```

**✅ 機率分布驗證**

```java
@Test
@DisplayName("測試 1000 次抽獎的機率分布")
void testProbabilityDistribution() {
    // 設定機率：iPhone 10%, AirPods 20%, 銘謝惠顧 70%
    DrawRequest request = new DrawRequest(1L, 1000);
    
    DrawResponse response = lotteryService.draw(1L, request);
    
    long winningCount = response.getResults().stream()
        .filter(DrawResult::getIsWinning)
        .count();
    
    // 驗證中獎率在 20% - 40% 之間（允許 10% 誤差）
    assertTrue(winningCount >= 200 && winningCount <= 400);
}
```

---

### 7.2 整合測試

#### 使用真實環境測試

```java
@SpringBootTest
@ActiveProfiles("test")
class LotteryServiceIntegrationTest {
    
    @Autowired
    private LotteryService lotteryService;
    
    @Test
    @DisplayName("整合測試：並發抽獎安全性")
    void testConcurrentDrawSafety() throws InterruptedException {
        // 10 個線程同時抽獎，庫存只有 5
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    lotteryService.draw(1L, new DrawRequest(1L, 1));
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        
        // 驗證：庫存不會低於 0
        Prize prize = prizeRepository.findById(1L).orElseThrow();
        assertTrue(prize.getRemainingStock() >= 0);
    }
}
```

---

### 7.3 測試覆蓋率

**目標：**
- 代碼覆蓋率：> 85%
- 分支覆蓋率：> 75%
- 行覆蓋率：> 90%

**測試報告生成：**

```bash
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

---

## 八、部署架構

### 8.1 環境變數配置

#### 開發環境 (dev)

```bash
DB_URL=jdbc:mysql://localhost:3306/lottery_db
DB_USERNAME=root
DB_PASSWORD=dev_password
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=dev-secret-key-for-testing
```

#### 測試環境 (test)

```bash
DB_URL=jdbc:mysql://test-mysql:3306/lottery_test
DB_USERNAME=test_user
DB_PASSWORD=test_password
REDIS_HOST=test-redis
JWT_SECRET=test-secret-key
```

#### 生產環境 (prod)

```bash
DB_URL=jdbc:mysql://mysql-cluster.prod:3306/lottery_prod
DB_USERNAME=prod_user
DB_PASSWORD=${MYSQL_PASSWORD}  # 從 Secret 注入
REDIS_HOST=redis-cluster.prod
REDIS_PASSWORD=${REDIS_PASSWORD}
JWT_SECRET=${JWT_SECRET}        # 從 Secret 注入
```

---

### 8.2 Docker 部署

#### Dockerfile

```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

COPY build/libs/lottery-system-1.0.0.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```

#### 構建和運行

```bash
# 構建 JAR
./gradlew clean build

# 構建 Docker 鏡像
docker build -t lottery-system:1.0.0 .

# 運行容器
docker run -d \
  -p 8080:8080 \
  -e DB_URL=jdbc:mysql://mysql:3306/lottery_db \
  -e DB_USERNAME=root \
  -e DB_PASSWORD=password \
  -e REDIS_HOST=redis \
  -e JWT_SECRET=your-secret \
  --name lottery-system \
  lottery-system:1.0.0
```

---

### 8.3 Docker Compose 部署

```yaml
version: '3.8'

services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:mysql://mysql:3306/lottery_db
      DB_USERNAME: root
      DB_PASSWORD: password
      REDIS_HOST: redis
      JWT_SECRET: your-secret-key
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_started

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: lottery_db
    ports:
      - "3306:3306"
    volumes:
      - ./schema.sql:/docker-entrypoint-initdb.d/schema.sql
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data

volumes:
  mysql-data:
  redis-data:
```

**啟動：**

```bash
docker-compose up -d
```

---

### 8.4 Kubernetes 部署

#### Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: lottery-system
spec:
  replicas: 3
  selector:
    matchLabels:
      app: lottery-system
  template:
    metadata:
      labels:
        app: lottery-system
    spec:
      containers:
      - name: app
        image: lottery-system:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: DB_URL
          value: jdbc:mysql://mysql-service:3306/lottery_db
        - name: DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: username
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: password
        - name: REDIS_HOST
          value: redis-service
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: jwt-secret
              key: secret
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
```

#### Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: lottery-system-service
spec:
  type: LoadBalancer
  selector:
    app: lottery-system
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
```

---

## 九、監控與運維

### 9.1 健康檢查

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics
  health:
    redis:
      enabled: true
    db:
      enabled: true
```

**訪問：**

```
http://localhost:8080/actuator/health
```

**響應：**

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "isValid()"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.0.0"
      }
    }
  }
}
```

---

### 9.2 日誌管理

```yaml
logging:
  level:
    root: INFO
    org.amway: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: /var/log/lottery-system/app.log
    max-size: 100MB
    max-history: 30
```

---

## 十、總結

本系統採用 **Spring Boot 3.2 + MySQL + Redis** 實現了一個企業級的電商抽獎平台。

### 核心亮點

#### 1. ✅ 高可用分散式架構
- 無狀態設計，支持水平擴展
- Nginx 負載均衡，動態增減實例
- 多活部署，容錯能力強

#### 2. ✅ 雙重鎖機制保證一致性
- Redis 分佈式鎖防止併發重複抽獎
- 數據庫悲觀鎖防止庫存超抽
- 事務管理保證統計數據準確

#### 3. ✅ 靈活的環境配置
- 所有關鍵配置支持環境變數注入
- 開發、測試、生產環境無縫切換
- Docker/K8s 友好

#### 4. ✅ 完善的測試體系
- 單元測試覆蓋核心邏輯
- 整合測試驗證並發場景
- 機率分布驗證確保公平性

#### 5. ✅ 標準的 RESTful API
- 前後端分離設計
- 統一的響應格式
- Swagger 自動生成文檔

#### 6. ✅ 企業級安全設計
- JWT Token 無狀態認證
- Spring Security 權限控制
- 完整的輸入驗證和錯誤處理

#### 7. ✅ 性能優化
- Redis 多層緩存
- HikariCP 連接池
- 全局 + 用戶雙層限流

---

### 技術選型理由

| 技術 | 選擇理由 |
|------|---------|
| Spring Boot 3.2 | 最新穩定版，性能優化，原生支持 GraalVM |
| MySQL 8.0 | 成熟穩定，事務支持完善，悲觀鎖性能好 |
| Redis 6.0+ | 高性能緩存，Redisson 分佈式鎖穩定 |
| JWT | 無狀態認證，適合分散式系統 |
| Spring Data JPA | 開發效率高，與 Spring 生態整合好 |
| Guava | Google 出品，限流組件成熟穩定 |

---

### 適用場景

本系統適用於以下業務場景：

- 🛒 電商平台促銷抽獎
- 🎮 遊戲平台抽獎活動
- 📱 App 用戶增長活動
- 🏪 線下門店掃碼抽獎
- 🎁 企業年會抽獎系統

---

### 後續擴展方向

1. **用戶畫像與個性化機率**
    - 根據用戶等級調整中獎率
    - VIP 用戶專屬獎池

2. **實時數據大屏**
    - WebSocket 推送實時中獎信息
    - 管理後台即時統計儀表板

3. **多渠道通知**
    - 中獎短信/郵件通知
    - 微信公眾號推送

4. **防作弊升級**
    - 設備指紋識別
    - 行為分析（抽獎間隔、IP 地址）
    - 風控規則引擎

5. **數據分析**
    - 用戶參與度分析
    - 機率分布效果評估
    - A/B 測試支持

---