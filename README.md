# 電商轉盤抽獎系統

> 一個基於 Spring Boot 3.2 的企業級高併發分散式抽獎系統，完全符合電商平台的高可用、高性能、高一致性需求。

## 📋 目錄

- [系統概述](#系統概述)
- [核心特性](#核心特性)
- [項目結構](#項目結構)
- [技術](#技術)
- [環境要求](#環境要求)
- [API 文檔](#api-文檔)
- [核心實現](#核心實現)
- [數據庫設計](#數據庫設計)
- [測試](#測試)
- [限流策略](#限流策略)

---

## 系統概述

該系統是一個完整的電商轉盤抽獎解決方案，支持：

-  **多獎品配置**：支持 N 種獎品，每種獎品可獨立設定庫存和中獎機率
-  **精準控制**：確保所有獎品機率總和為 100%（含銘謝惠顧）
-  **抽獎**：支持單次/多次連續抽獎，每個活動可自訂次數限制（TOTAL/DAILY/WEEKLY）
- ️**風控防護**：採用三層防護（分佈式鎖 + 悲觀鎖 + 事務），100% 防止重複抽獎和庫存超抽
-  **高併發**：峰值支持 1000+ QPS
-  **分散式部署**：支持水平擴展，多實例無狀態運行
-  **限流**：支持全局限流和用戶維度限流，防止系統過載

---

## 核心特性

### 功能特性

✅ **用戶抽獎功能**
- 單次或多次連續抽獎
- 支持三種限制模式：TOTAL（總次數）、DAILY（每日）、WEEKLY（每週）
- 實時庫存扣減，庫存不足自動降級為銘謝惠顧
- 完整的抽獎歷史記錄
- 實時返回抽獎結果

✅ **用戶認證**
- JWT Token 無狀態認證
- 支持 Refresh Token 刷新
- 登出時立即失效
- 基於角色的訪問控制

✅ **活動管理**
- 查詢活動列表
- 查詢活動詳情和獎品信息
- 實時活動狀態判斷

✅ **數據統計**
- 實時統計用戶抽獎次數和中獎情況
- 活動級別的聚合統計
- Redis 快取加速

### 非功能特性

✅ **高可用性**
- 分佈式鎖（Redisson）防止併發重複抽獎
- 數據庫悲觀鎖防止庫存超抽
- 完整的異常處理和降級策略

✅ **高性能**
- Redis 多層緩存優化熱數據
- HikariCP 連線池管理
- AOP 註解方式的限流控制

✅ **可靠性**
- 完整的事務管理（@Transactional）
- 統計表雙寫保證最終一致性
- 詳細的操作日誌記錄

---

## 項目結構

```
AmwayEC-Testing/
├── src/
│   ├── main/
│   │   ├── java/org/amway/
│   │   │   ├── annotation/
│   │   │   │   └── RateLimit.java                # 限流註解
│   │   │   ├── aspect/
│   │   │   │   └── RateLimitAspect.java          # 限流 AOP 切面
│   │   │   ├── config/
│   │   │   │   ├── RateLimiterConfig.java        # 限流配置
│   │   │   │   ├── RedisConfig.java              # Redis 配置
│   │   │   │   ├── SecurityConfig.java           # Spring Security 配置
│   │   │   │   └── SwaggerConfig.java            # Swagger/OpenAPI 配置
│   │   │   ├── controller/
│   │   │   │   ├── ActivityController.java       # 活動控制器
│   │   │   │   ├── AuthController.java           # 認證控制器
│   │   │   │   └── LotteryController.java        # 用戶抽獎控制器
│   │   │   ├── dto/
│   │   │   │   ├── request/
│   │   │   │   │   ├── DrawRequest.java
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   └── RefreshTokenRequest.java
│   │   │   │   └── response/
│   │   │   │       ├── ActivityResponse.java
│   │   │   │       ├── ApiResponse.java
│   │   │   │       ├── DrawResponse.java
│   │   │   │       ├── DrawResult.java
│   │   │   │       └── PrizeResponse.java
│   │   │   ├── entity/
│   │   │   │   ├── DrawRecord.java
│   │   │   │   ├── LotteryActivity.java
│   │   │   │   ├── Prize.java
│   │   │   │   ├── User.java
│   │   │   │   ├── UserDailyDrawStatistics.java
│   │   │   │   └── UserDrawStatistics.java
│   │   │   ├── exception/
│   │   │   │   ├── enums/
│   │   │   │   │   └── ErrorCode.java            # 錯誤代碼枚舉
│   │   │   │   ├── response/
│   │   │   │   │   └── ErrorResponse.java        # 錯誤響應
│   │   │   │   ├── BusinessException.java        # 業務異常
│   │   │   │   ├── GlobalExceptionHandler.java   # 全局異常處理
│   │   │   ├── repository/
│   │   │   │   ├── DrawRecordRepository.java
│   │   │   │   ├── LotteryActivityRepository.java
│   │   │   │   ├── PrizeRepository.java
│   │   │   │   ├── UserDailyDrawStatisticsRepository.java
│   │   │   │   ├── UserDrawStatisticsRepository.java
│   │   │   │   └── UserRepository.java
│   │   │   ├── security/
│   │   │   │   ├── JwtAuthenticationFilter.java  # JWT 過濾器
│   │   │   │   └── JwtUtil.java                  # JWT 工具
│   │   │   ├── service/
│   │   │   │   ├── ActivityService.java          # 活動服務
│   │   │   │   └── LotteryService.java           # 抽獎核心服務
│   │   │   └── LotteryApplication.java           # Spring Boot 入口
│   │   └── resources/
│   │       ├── application.yml                   # 應用配置
│   │       └── application-test.yml              # 測試配置
│   └── test/
│       ├── java/org/amway/service/
│       │   ├── LotteryServiceIntegrationTest.java # 整合測試
│       │   └── LotteryServiceTest.java           # 單元測試
│       └── resources/
│           └── application-test.yml              # 測試配置
├── build.gradle.kts                              # Gradle 構建配置
├── schema.sql                                     # 數據庫初始化腳本
├── README.md                                     # 本文件                            # 技術架構詳解
└── .gitignore
```

---

## 技術

| 層級 | 技術 | 版本 | 用途 |
|------|-----|------|------|
| 框架 | Spring Boot | 3.2.0 | Web 應用框架 |
| 語言 | Java | 17+ | 編程語言 |
| 數據庫 | MySQL | 8.0+ | 持久化存儲 |
| 快取 | Redis + Redisson | 6.0+ | 分佈式鎖、緩存 |
| 認證 | JWT | 0.11.5 | Token 認證 |
| API | SpringDoc OpenAPI | 2.3.0 | API 文檔 |
| ORM | Spring Data JPA + Hibernate | 3.2.0 | 數據庫訪問 |
| 限流 | Guava RateLimiter | 32.1.3 | 限流控制 |
| 測試 | JUnit 5 + Mockito | 5.10.0 | 單元和整合測試 |
| AOP | Spring AOP | 3.2.0 | 切面編程（限流） |
| 構建 | Gradle | 8.x | 項目構建 |

---

## 環境要求

- JDK 17+
- Gradle 8.x
- MySQL 8.0+
- Redis 6.0+

## API 文檔

### 認證相關

#### 用戶登入

```http
POST /api/auth/login #登入
POST /api/auth/refresh #刷新 Token
POST /api/auth/logout #登出
POST /api/lottery/draw #執行抽獎（支持限流）
GET /api/lottery/history?activityId=1&page=0&size=10 #查詢抽獎歷史
GET /api/lottery/remaining-draws?activityId=1 #查詢剩餘抽獎次數
GET /api/activities?page=0&size=10 #查詢活動列表
GET /api/activities/1 #查詢活動詳情
```

**完整 API 文檔請訪問 Swagger UI：** `http://localhost:8080/swagger-ui.html`

## 核心實現

### 抽獎核心算法（輪盤賭）

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
    return prizes.get(prizes.size() - 1);  // 返回最後一個（通常是銘謝惠顧）
}
```

### 並發控制（三層防護）

```
[第一層] Redis 分佈式鎖 (Redisson)
  └─ 防止同一用戶併發重複抽獎
  └─ 鎖粒度：userId + activityId
  └─ 超時設置：等待 10s，持有 30s

[第二層] 數據庫悲觀鎖
  └─ SELECT ... FOR UPDATE
  └─ 防止獎品庫存超抽
  └─ 原子性操作

[第三層] 事務管理
  └─ @Transactional
  └─ 任何異常自動回滾
  └─ 保證統計數據一致性
```

### 防超抽機制

**完整流程：**

1. 檢查用戶剩餘次數
2. 獲取分佈式鎖（Redisson）
3. 根據機率選擇獎品
4. 使用悲觀鎖查詢獎品
5. 檢查庫存是否充足
6. 原子性扣減庫存
7. 記錄抽獎結果
8. 更新統計數據
9. 釋放鎖

---

## 數據庫設計

### 核心表結構

#### users (用戶表)
```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username)
);
```

#### lottery_activities (活動表)
```sql
CREATE TABLE lottery_activities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    limit_type VARCHAR(20) NOT NULL DEFAULT 'TOTAL',
    max_draws_per_user INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_time (start_time, end_time)
);
```

#### prizes (獎品表)
```sql
CREATE TABLE prizes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    total_stock INT NOT NULL,
    remaining_stock INT NOT NULL,
    probability DECIMAL(10, 6) NOT NULL,
    prize_type VARCHAR(20) NOT NULL,
    image_url VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activity FOREIGN KEY (activity_id) REFERENCES lottery_activities(id),
    CONSTRAINT chk_probability CHECK (probability >= 0 AND probability <= 1),
    CONSTRAINT chk_stock CHECK (remaining_stock >= 0),
    INDEX idx_activity (activity_id),
    INDEX idx_stock (remaining_stock)
);
```

#### draw_records (抽獎記錄表)
```sql
CREATE TABLE draw_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    prize_id BIGINT,
    is_winning BOOLEAN NOT NULL DEFAULT FALSE,
    prize_name VARCHAR(100),
    draw_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    CONSTRAINT fk_activity FOREIGN KEY (activity_id) REFERENCES lottery_activities(id),
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_prize FOREIGN KEY (prize_id) REFERENCES prizes(id),
    INDEX idx_user_activity (user_id, activity_id),
    INDEX idx_activity_time (activity_id, draw_time)
);
```

#### user_draw_statistics (用戶統計表)
```sql
CREATE TABLE user_draw_statistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    total_draws INT NOT NULL DEFAULT 0,
    winning_draws INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_activity FOREIGN KEY (activity_id) REFERENCES lottery_activities(id),
    UNIQUE KEY uk_user_activity (user_id, activity_id)
);
```

#### user_daily_draw_statistics (每日統計表)
```sql
CREATE TABLE user_daily_draw_statistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    draw_date DATE NOT NULL,
    daily_draws INT NOT NULL DEFAULT 0,
    daily_winning_draws INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_activity FOREIGN KEY (activity_id) REFERENCES lottery_activities(id),
    UNIQUE KEY uk_user_activity_date (user_id, activity_id, draw_date)
);
```

---

## 限流策略

### 限流註解 (@RateLimit)

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    // 標記需要限流的方法
}
```

### 限流 AOP 切面

```java
@Aspect
@Component
public class RateLimitAspect {
    
    @Around("@annotation(rateLimit)")
    public Object rateLimit(ProceedingJoinPoint pjp, RateLimit rateLimit) 
            throws Throwable {
        // 1. 獲取全局限流器
        // 2. 檢查是否超過限流
        // 3. 若超過限流，拋出異常
        // 4. 否則執行方法
    }
}
```

### 使用示例

```java
@PostMapping("/draw")
@RateLimit  // 使用限流註解
public ApiResponse<DrawResponse> draw(
        @Valid @RequestBody DrawRequest request,
        Authentication authentication) {
    // 方法執行時自動限流檢查
}
```

### 限流配置

```yaml
# application.yml
rate-limiter:
  global:
    permits-per-second: 1000  # 全局限流：每秒 1000 次
  user:
    permits-per-second: 10    # 用戶維度：每個用戶每秒 10 次
```

---

## 測試

### 運行測試

```bash
# 運行所有測試
./gradlew test

# 運行特定測試類
./gradlew test --tests LotteryServiceTest

# 運行整合測試
./gradlew test --tests LotteryServiceIntegrationTest
```

### 測試用例

#### 單元測試 (LotteryServiceTest)

✅ **testSingleDrawSuccess** - 單次抽獎成功  
✅ **testMultipleDraws** - 多次連續抽獎  
✅ **testInsufficientDraws** - 抽獎次數不足  
✅ **testActivityNotFound** - 活動不存在  
✅ **testActivityEnded** - 活動已結束  
✅ **testPrizeStockDeduction** - 獎品庫存扣減  
✅ **testProbabilityDistribution** - 機率分布驗證（1000 次）  
✅ **testDailyLimitMode** - 每日限制模式  
✅ **testTotalLimitMode** - 總次數限制模式

#### 整合測試 (LotteryServiceIntegrationTest)

✅ **testDailyLimitExceeded** - 每日限制超出  
✅ **testTotalLimitExceeded** - 總次數限制超出  
✅ **testRateLimitExceeded** - 限流測試  
✅ **testConcurrentDrawSafety** - 10 線程並發安全性  
✅ **testActivityEnded** - 活動結束判斷
