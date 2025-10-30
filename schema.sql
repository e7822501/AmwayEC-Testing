-- ============================================
-- 電商抽獎系統數據庫設計（改進版）
-- ============================================

-- 1. 用戶表（新增 VIP 等級等）
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'USER, ADMIN',
    vip_level INT NOT NULL DEFAULT 0 COMMENT 'VIP 等級，影響抽獎次數',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用戶表';

-- 2. 抽獎活動表（新增更多配置）
CREATE TABLE IF NOT EXISTS lottery_activities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    start_time TIMESTAMP NOT NULL COMMENT '活動開始時間',
    end_time TIMESTAMP NOT NULL COMMENT '活動結束時間',
    
    -- 抽獎次數限制配置
    limit_type VARCHAR(20) NOT NULL DEFAULT 'TOTAL' COMMENT 'TOTAL(總次數), DAILY(每日), WEEKLY(每週)',
    max_draws_per_user INT NOT NULL DEFAULT 1 COMMENT '每人抽獎次數上限',
    
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, INACTIVE, ENDED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_time_range (start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抽獎活動表';

-- 3. 獎品表（不變）
CREATE TABLE IF NOT EXISTS prizes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    total_stock INT NOT NULL DEFAULT 0 COMMENT '總庫存',
    remaining_stock INT NOT NULL DEFAULT 0 COMMENT '剩餘庫存',
    probability DECIMAL(10, 6) NOT NULL COMMENT '中獎機率 (0-1之間)',
    prize_type VARCHAR(20) NOT NULL DEFAULT 'PHYSICAL' COMMENT 'PHYSICAL, VIRTUAL, NO_PRIZE',
    image_url VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (activity_id) REFERENCES lottery_activities(id) ON DELETE CASCADE,
    INDEX idx_activity_id (activity_id),
    INDEX idx_remaining_stock (remaining_stock),
    CONSTRAINT chk_probability CHECK (probability >= 0 AND probability <= 1),
    CONSTRAINT chk_stock CHECK (remaining_stock >= 0 AND remaining_stock <= total_stock)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='獎品表';

-- 4. 抽獎記錄表（不變）
CREATE TABLE IF NOT EXISTS draw_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    prize_id BIGINT,
    draw_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    draw_date DATE GENERATED ALWAYS AS (DATE(draw_time)) STORED COMMENT '抽獎日期（用於每日統計）',
    is_winning BOOLEAN NOT NULL DEFAULT FALSE,
    prize_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' COMMENT 'COMPLETED, FAILED, CANCELLED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (activity_id) REFERENCES lottery_activities(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (prize_id) REFERENCES prizes(id) ON DELETE SET NULL,
    INDEX idx_user_activity (user_id, activity_id),
    INDEX idx_user_activity_date (user_id, activity_id, draw_date),
    INDEX idx_activity_time (activity_id, draw_time),
    INDEX idx_user_time (user_id, draw_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抽獎記錄表';

-- 5. 用戶每日抽獎統計表（新增）
CREATE TABLE IF NOT EXISTS user_daily_draw_statistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    draw_date DATE NOT NULL COMMENT '抽獎日期',
    daily_draws INT NOT NULL DEFAULT 0 COMMENT '當日抽獎次數',
    daily_winning_draws INT NOT NULL DEFAULT 0 COMMENT '當日中獎次數',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_activity_date (user_id, activity_id, draw_date),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (activity_id) REFERENCES lottery_activities(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_activity_date (activity_id, draw_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用戶每日抽獎統計表';

-- 6. 活動即時統計快取表（用於管理後台）
CREATE TABLE IF NOT EXISTS activity_realtime_statistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL UNIQUE,
    total_participants INT NOT NULL DEFAULT 0 COMMENT '總參與人數',
    total_draws INT NOT NULL DEFAULT 0 COMMENT '總抽獎次數',
    total_winning_draws INT NOT NULL DEFAULT 0 COMMENT '總中獎次數',
    winning_rate DECIMAL(5, 2) COMMENT '中獎率(%)',
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (activity_id) REFERENCES lottery_activities(id) ON DELETE CASCADE,
    INDEX idx_activity_id (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活動即時統計快取表';

-- ============================================
-- 測試數據 (DML)
-- ============================================

-- 插入測試用戶
INSERT INTO users (username, password, email, role, vip_level) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@lottery.com', 'ADMIN', 0),
('user1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'user1@test.com', 'USER', 0),
('vip_user', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'vip@test.com', 'USER', 3);

-- 插入測試活動（每日限制）
INSERT INTO lottery_activities (name, description, start_time, end_time, limit_type, max_draws_per_user, status) VALUES
('雙11狂歡抽獎', '雙11購物節限定抽獎活動', '2025-11-01 00:00:00', '2025-11-11 23:59:59', 'DAILY', 3, 'ACTIVE'),
('新年大抽獎', '新年限定豪華抽獎', '2025-12-25 00:00:00', '2026-01-05 23:59:59', 'TOTAL', 5, 'ACTIVE');

-- 插入獎品
INSERT INTO prizes (activity_id, name, description, total_stock, remaining_stock, probability, prize_type) VALUES
(1, 'iPhone 15 Pro', '最新款iPhone 15 Pro 256GB', 10, 10, 0.01, 'PHYSICAL'),
(1, 'AirPods Pro', 'Apple AirPods Pro 第二代', 50, 50, 0.05, 'PHYSICAL'),
(1, '100元購物金', '平台通用購物金', 500, 500, 0.14, 'VIRTUAL'),
(1, '銘謝惠顧', '謝謝參與', 999999, 999999, 0.80, 'NO_PRIZE');

-- 初始化活動統計
INSERT INTO activity_realtime_statistics (activity_id, total_participants, total_draws, total_winning_draws, winning_rate) 
SELECT id, 0, 0, 0, 0.00 FROM lottery_activities;
