-- Binance Compound Pro - MySQL 5.6+ Schema
-- Dual-mode: is_simulation = 1 (simulation) / 0 (real trading)
-- Precision: DECIMAL(32, 16) for all amount/price/quantity fields

CREATE DATABASE IF NOT EXISTS binance_compound
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE binance_compound;

-- ============================================================
-- 1. cycle_instances: Instance state (one instance per deposit)
-- ============================================================
CREATE TABLE cycle_instances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL COMMENT 'Trading pair, e.g. BTCUSDT',
    instance_id INT NOT NULL COMMENT 'Auto-increment ID per symbol',
    cycle_id INT NOT NULL DEFAULT 0 COMMENT 'Current cycle number',
    is_simulation TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=simulation, 0=real',
    
    is_open TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=holding position, 0=no position',
    anchor_price DECIMAL(32, 16) NOT NULL DEFAULT 0 COMMENT 'First entry price',
    reentry_price DECIMAL(32, 16) NOT NULL DEFAULT 0 COMMENT 'Price to wait for reentry',
    cycle_start_price DECIMAL(32, 16) NOT NULL DEFAULT 0 COMMENT 'This cycle entry avg price',
    last_action_price DECIMAL(32, 16) NOT NULL DEFAULT 0 COMMENT 'Last buy avg price',
    base_qty DECIMAL(32, 16) NOT NULL DEFAULT 0 COMMENT 'Current base asset quantity',
    spent_quote DECIMAL(32, 16) NOT NULL DEFAULT 0 COMMENT 'Quote spent on current position',
    quote_amount DECIMAL(32, 16) NOT NULL DEFAULT 0 COMMENT 'Next buy quote amount (compounded)',
    cumulative_profit DECIMAL(32, 16) NOT NULL DEFAULT 0 COMMENT 'Cumulative take profit in USDT',
    
    updated_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_symbol_instance_sim (symbol, instance_id, is_simulation),
    INDEX idx_symbol_sim (symbol, is_simulation),
    INDEX idx_is_open_sim (is_open, is_simulation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 2. trade_records: Each order record (BUY/SELL)
-- ============================================================
CREATE TABLE trade_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(50) NOT NULL COMMENT 'Exchange order ID',
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL COMMENT 'BUY or SELL',
    status VARCHAR(20) NOT NULL,
    is_simulation TINYINT(1) NOT NULL DEFAULT 1,
    executed_qty DECIMAL(32, 16) NOT NULL DEFAULT 0 COMMENT 'Base asset executed quantity',
    cummulative_quote_qty DECIMAL(32, 16) NOT NULL DEFAULT 0 COMMENT 'Cumulative quote quantity',
    avg_price DECIMAL(32, 16) NOT NULL DEFAULT 0,
    payload_json TEXT COMMENT 'Full order response JSON',
    created_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_symbol_sim_created (symbol, is_simulation, created_at_utc),
    INDEX idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 3. sim_accounts: Simulation account balance (dual-mode)
-- ============================================================
CREATE TABLE sim_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset VARCHAR(20) NOT NULL COMMENT 'Asset symbol, e.g. USDT, BTC',
    free_balance DECIMAL(32, 16) NOT NULL DEFAULT 0 COMMENT 'Available balance',
    locked_balance DECIMAL(32, 16) NOT NULL DEFAULT 0 COMMENT 'Locked balance',
    is_simulation TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=simulation, 0=real',
    
    updated_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_asset_sim (asset, is_simulation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 4. cycle_open_records: Each cycle opening record
-- ============================================================
CREATE TABLE cycle_open_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    instance_id INT NOT NULL,
    cycle_id INT NOT NULL,
    is_simulation TINYINT(1) NOT NULL DEFAULT 1,
    start_price DECIMAL(32, 16) NOT NULL COMMENT 'Opening avg price',
    quote_amount DECIMAL(32, 16) NOT NULL COMMENT 'Quote amount used for opening',
    opened_at_utc DATETIME NOT NULL,
    created_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_symbol_instance_sim (symbol, instance_id, is_simulation),
    INDEX idx_opened_at (opened_at_utc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 5. instance_events: Event log for each instance
-- ============================================================
CREATE TABLE instance_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    instance_id INT NOT NULL,
    cycle_id INT NOT NULL,
    is_simulation TINYINT(1) NOT NULL DEFAULT 1,
    event VARCHAR(30) NOT NULL COMMENT 'Event type',
    price DECIMAL(32, 16) NOT NULL DEFAULT 0,
    base_qty DECIMAL(32, 16) NOT NULL DEFAULT 0,
    quote_amount DECIMAL(32, 16) NOT NULL DEFAULT 0,
    note VARCHAR(500) DEFAULT '',
    created_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_symbol_instance_created (symbol, instance_id, created_at_utc),
    INDEX idx_event_type (event, is_simulation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 6. rr_state: Round-robin pointer per quote asset
-- ============================================================
CREATE TABLE rr_state (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quote_asset VARCHAR(20) NOT NULL,
    last_symbol VARCHAR(20) NOT NULL DEFAULT '',
    is_simulation TINYINT(1) NOT NULL DEFAULT 1,
    updated_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_quote_asset_sim (quote_asset, is_simulation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 7. expected_free: Expected free baseline for deposit detection
-- ============================================================
CREATE TABLE expected_free (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset VARCHAR(20) NOT NULL,
    expected_free DECIMAL(32, 16) NOT NULL DEFAULT 0 COMMENT 'Expected free balance baseline',
    is_simulation TINYINT(1) NOT NULL DEFAULT 1,
    updated_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_asset_sim (asset, is_simulation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 8. strategy_config: Strategy parameters (for dynamic adjustment)
-- ============================================================
CREATE TABLE strategy_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(50) NOT NULL,
    config_value VARCHAR(200) NOT NULL,
    is_simulation TINYINT(1) NOT NULL DEFAULT 1,
    updated_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_config_key_sim (config_key, is_simulation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert default config values
INSERT INTO strategy_config (config_key, config_value, is_simulation) VALUES
    ('TAKE_PROFIT_PCT', '0.03', 1),
    ('STOP_LOSS_PCT', '0.10', 1),
    ('BASE_QUOTE_AMOUNT', '0', 1),
    ('MAX_ORDERS_PER_TICK', '5', 1),
    ('QUOTE_RESERVE', '10', 1),
    ('RSI_OVERBOUGHT_DEFAULT', '80', 1),
    ('RSI_OVERSOLD_DEFAULT', '20', 1),
    ('RSI_PERIOD_DEFAULT', '14', 1);

-- ============================================================
-- 9. price_alerts: TD9/TD13 trend alerts
-- ============================================================
CREATE TABLE IF NOT EXISTS price_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL COMMENT 'Trading pair, e.g. BTCUSDT',
    kline_interval VARCHAR(10) NOT NULL COMMENT 'Kline interval, e.g. 1h, 4h',
    alert_type VARCHAR(20) NOT NULL COMMENT 'TD_BUY or TD_SELL',
    td_count INT NOT NULL DEFAULT 0 COMMENT 'Current TD count',
    current_price DECIMAL(32, 16) NOT NULL COMMENT 'Current price when checked',
    trigger_price DECIMAL(32, 16) NOT NULL DEFAULT 0 COMMENT 'Price when triggered',
    triggered TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=triggered, 0=not triggered',
    message VARCHAR(500) DEFAULT '' COMMENT 'Alert message',
    created_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_notified_at DATETIME DEFAULT NULL COMMENT 'Last notification time for cooldown',
    
    INDEX idx_symbol_interval_alert (symbol, kline_interval, alert_type),
    INDEX idx_created_at (created_at_utc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 10. api_config: API configuration (API keys, etc.)
-- ============================================================
CREATE TABLE api_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(50) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    updated_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 11. funding_rate_alerts: Funding rate alerts for bottom-fishing signals
-- ============================================================
CREATE TABLE IF NOT EXISTS funding_rate_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL COMMENT 'Trading pair, e.g. BTCUSDT',
    alert_type VARCHAR(20) NOT NULL COMMENT 'LEVEL_1 or LEVEL_2',
    funding_rate DECIMAL(32, 16) COMMENT 'Funding rate when triggered',
    annualized_rate DECIMAL(32, 16) COMMENT 'Annualized funding rate',
    next_funding_time BIGINT COMMENT 'Next funding time timestamp',
    last_notified_at DATETIME DEFAULT NULL COMMENT 'Last notification time for cooldown',
    created_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_symbol_alert_type (symbol, alert_type),
    INDEX idx_last_notified (last_notified_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 12. api_accounts: Multiple API account support
-- ============================================================
CREATE TABLE api_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_name VARCHAR(50) NOT NULL COMMENT 'Display name for dropdown',
    api_key TEXT NOT NULL COMMENT 'Binance API Key',
    api_secret TEXT NOT NULL COMMENT 'Binance API Secret',
    use_proxy TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=use proxy, 0=no proxy',
    proxy_url VARCHAR(200) DEFAULT '' COMMENT 'Proxy URL',
    testnet TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=testnet, 0=mainnet',
    is_active TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=currently selected',
    created_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
