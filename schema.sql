
-- ============================================
-- 電商抽獎系統數據庫設計
-- ============================================

--DDL
-- 1. 用戶表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用戶表';

-- 2. 抽獎活動表
CREATE TABLE IF NOT EXISTS lottery_activities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    max_draws_per_user INT NOT NULL DEFAULT 1 COMMENT '每個用戶最多抽獎次數',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, INACTIVE, ENDED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_time_range (start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抽獎活動表';

-- 3. 獎品表
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

-- 4. 抽獎記錄表
CREATE TABLE IF NOT EXISTS draw_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    prize_id BIGINT,
    draw_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_winning BOOLEAN NOT NULL DEFAULT FALSE,
    prize_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' COMMENT 'COMPLETED, FAILED, CANCELLED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (activity_id) REFERENCES lottery_activities(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (prize_id) REFERENCES prizes(id) ON DELETE SET NULL,
    INDEX idx_user_activity (user_id, activity_id),
    INDEX idx_activity_time (activity_id, draw_time),
    INDEX idx_user_time (user_id, draw_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抽獎記錄表';

-- 5. 用戶抽獎次數統計表 (用於快速查詢)
CREATE TABLE IF NOT EXISTS user_draw_statistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    total_draws INT NOT NULL DEFAULT 0 COMMENT '總抽獎次數',
    winning_draws INT NOT NULL DEFAULT 0 COMMENT '中獎次數',
    last_draw_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_activity (user_id, activity_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (activity_id) REFERENCES lottery_activities(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_activity_id (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用戶抽獎統計表';

-- ============================================
-- 測試數據 (DML)
-- ============================================

-- 插入測試用戶 (密碼: password123, 使用BCrypt加密)
INSERT INTO users (username, password, email, role) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@lottery.com', 'ADMIN'),
('user1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'user1@test.com', 'USER'),
('user2', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'user2@test.com', 'USER');

-- 插入測試抽獎活動
INSERT INTO lottery_activities (name, description, start_time, end_time, max_draws_per_user, status) VALUES
('雙11狂歡抽獎', '雙11購物節限定抽獎活動', '2025-11-01 00:00:00', '2025-11-11 23:59:59', 5, 'ACTIVE'),
('新年大抽獎', '新年限定豪華抽獎', '2025-12-25 00:00:00', '2026-01-05 23:59:59', 3, 'ACTIVE');

-- 插入測試獎品 (活動1: 雙11狂歡抽獎)
INSERT INTO prizes (activity_id, name, description, total_stock, remaining_stock, probability, prize_type) VALUES
(1, 'iPhone 15 Pro', '最新款iPhone 15 Pro 256GB', 10, 10, 0.01, 'PHYSICAL'),
(1, 'AirPods Pro', 'Apple AirPods Pro 第二代', 50, 50, 0.05, 'PHYSICAL'),
(1, '100元購物金', '平台通用購物金', 500, 500, 0.14, 'VIRTUAL'),
(1, '銘謝惠顧', '謝謝參與', 999999, 999999, 0.80, 'NO_PRIZE');

-- 插入測試獎品 (活動2: 新年大抽獎)
INSERT INTO prizes (activity_id, name, description, total_stock, remaining_stock, probability, prize_type) VALUES
(2, 'MacBook Air', 'Apple MacBook Air M2', 5, 5, 0.005, 'PHYSICAL'),
(2, 'iPad Pro', 'iPad Pro 11吋', 20, 20, 0.02, 'PHYSICAL'),
(2, '500元現金券', '現金抵用券', 200, 200, 0.075, 'VIRTUAL'),
(2, '銘謝惠顧', '謝謝參與', 999999, 999999, 0.90, 'NO_PRIZE');

-- 驗證機率總和
SELECT
    activity_id,
    SUM(probability) as total_probability
FROM prizes
GROUP BY activity_id;