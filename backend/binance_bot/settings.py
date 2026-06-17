"""
Django settings for binance_bot project.
Binance Spot Bot - Django Refactor Version
"""
import os
from pathlib import Path
from celery.schedules import crontab

BASE_DIR = Path(__file__).resolve().parent.parent

SECRET_KEY = os.environ.get(
    'DJANGO_SECRET_KEY',
    'django-insecure-change-me-in-production-9d8f7a6b5c4e3d2f1a0b9c8d7e6f5a4b'
)

DEBUG = os.environ.get('DJANGO_DEBUG', 'True').lower() == 'true'

# 测试客户端使用 'testserver' host
ALLOWED_HOSTS = ['*'] if DEBUG else [h.strip() for h in os.environ.get('ALLOWED_HOSTS', 'localhost,127.0.0.1,0.0.0.0,backend,testserver').split(',') if h.strip()]

INSTALLED_APPS = [
    'django.contrib.admin',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',

    # Third party
    'rest_framework',
    'corsheaders',
    'channels',
    'django_celery_beat',

    # Local apps
    'apps.accounts',
    'apps.trading',
    'apps.market',
    'apps.scanners',
    'apps.notifications',
    'apps.reports',
    'apps.ai',
    'apps.strategy',
]

MIDDLEWARE = [
    'corsheaders.middleware.CorsMiddleware',
    'django.middleware.security.SecurityMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
]

ROOT_URLCONF = 'binance_bot.urls'

TEMPLATES = [
    {
        'BACKEND': 'django.template.backends.django.DjangoTemplates',
        'DIRS': [],
        'APP_DIRS': True,
        'OPTIONS': {
            'context_processors': [
                'django.template.context_processors.debug',
                'django.template.context_processors.request',
                'django.contrib.auth.context_processors.auth',
                'django.contrib.messages.context_processors.messages',
            ],
        },
    },
]

WSGI_APPLICATION = 'binance_bot.wsgi.application'
ASGI_APPLICATION = 'binance_bot.asgi.application'

# Database - MySQL (全新库)
DATABASES = {
    'default': {
        'ENGINE': 'django.db.backends.mysql',
        'NAME': os.environ.get('DB_NAME', 'binance_compound_v2'),
        'USER': os.environ.get('DB_USER', 'root'),
        'PASSWORD': os.environ.get('DB_PASSWORD', 'rootpassword'),
        'HOST': os.environ.get('DB_HOST', 'mysql'),
        'PORT': os.environ.get('DB_PORT', '3306'),
        'OPTIONS': {
            'charset': 'utf8mb4',
            'init_command': "SET sql_mode='STRICT_TRANS_TABLES'",
        },
        'CONN_MAX_AGE': 600,
    },
    # 旧库（只读，用于数据迁移）
    'legacy': {
        'ENGINE': 'django.db.backends.mysql',
        'NAME': os.environ.get('LEGACY_DB_NAME', 'binance_compound'),
        'USER': os.environ.get('DB_USER', 'root'),
        'PASSWORD': os.environ.get('DB_PASSWORD', 'rootpassword'),
        'HOST': os.environ.get('DB_HOST', 'mysql'),
        'PORT': os.environ.get('DB_PORT', '3306'),
        'OPTIONS': {
            'charset': 'utf8mb4',
        },
    },
}

# Redis (Celery broker + Channels)
REDIS_HOST = os.environ.get('REDIS_HOST', 'redis')
REDIS_PORT = int(os.environ.get('REDIS_PORT', 6379))
REDIS_URL = f'redis://{REDIS_HOST}:{REDIS_PORT}/0'

CACHES = {
    'default': {
        'BACKEND': 'django.core.cache.backends.redis.RedisCache',
        'LOCATION': REDIS_URL,
    }
}

CHANNEL_LAYERS = {
    'default': {
        'BACKEND': 'channels.layers.InMemoryChannelLayer',
    },
}

# Celery
CELERY_BROKER_URL = REDIS_URL
CELERY_RESULT_BACKEND = REDIS_URL
CELERY_ACCEPT_CONTENT = ['json']
CELERY_TASK_SERIALIZER = 'json'
CELERY_RESULT_SERIALIZER = 'json'
CELERY_TIMEZONE = 'UTC'
CELERY_TASK_TRACK_STARTED = True
CELERY_TASK_TIME_LIMIT = 30 * 60
CELERY_BEAT_SCHEDULER = 'django_celery_beat.schedulers:DatabaseScheduler'

# Celery Beat Schedule
CELERY_BEAT_SCHEDULE = {
    'real-trading-tick': {
        'task': 'apps.trading.tasks.execute_real_tick',
        'schedule': 30.0,  # 30秒
    },
    'rsi-scan': {
        'task': 'apps.scanners.tasks.scan_rsi_indicators',
        'schedule': 60.0,  # 1分钟
    },
    'td-scan': {
        'task': 'apps.scanners.tasks.scan_td_indicators',
        'schedule': 60.0,
    },
    'funding-rate-scan': {
        'task': 'apps.scanners.tasks.scan_funding_rates',
        'schedule': 300.0,  # 5分钟
    },
    'daily-btc-report': {
        'task': 'apps.reports.tasks.generate_daily_btc_report',
        'schedule': crontab(hour=8, minute=0),  # 每天 8:00
    },
}

# Authentication
AUTH_PASSWORD_VALIDATORS = [
    {'NAME': 'django.contrib.auth.password_validation.UserAttributeSimilarityValidator'},
    {'NAME': 'django.contrib.auth.password_validation.MinimumLengthValidator'},
    {'NAME': 'django.contrib.auth.password_validation.CommonPasswordValidator'},
    {'NAME': 'django.contrib.auth.password_validation.NumericPasswordValidator'},
]

LANGUAGE_CODE = 'en-us'
TIME_ZONE = 'UTC'
USE_I18N = True
USE_TZ = True

STATIC_URL = 'static/'
STATIC_ROOT = BASE_DIR / 'staticfiles'

DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'

# CORS
CORS_ALLOW_ALL_ORIGINS = True
CORS_ALLOW_CREDENTIALS = True

# REST Framework
REST_FRAMEWORK = {
    'DEFAULT_AUTHENTICATION_CLASSES': [
        'rest_framework.authentication.SessionAuthentication',
    ],
    'DEFAULT_PERMISSION_CLASSES': [
        'rest_framework.permissions.AllowAny',  # 简化：暂不启用认证
    ],
    'DEFAULT_RENDERER_CLASSES': [
        'rest_framework.renderers.JSONRenderer',
    ],
}

# Binance API 配置
BINANCE_BASE_URL = os.environ.get('BINANCE_BASE_URL', 'https://api.binance.com')
BINANCE_WS_URL = os.environ.get('BINANCE_WS_URL', 'wss://stream.binance.com:9443/ws')
BINANCE_PROXY_URL = os.environ.get('BINANCE_PROXY_URL', '')
BINANCE_TIMEOUT_SECONDS = 10

# 监控的默认币种
DEFAULT_SYMBOLS = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'DOGEUSDT', 'SOLUSDT']

# 资金费率监控配置
FUNDING_RATE = {
    'ENABLED': os.environ.get('FUNDING_RATE_ENABLED', 'true').lower() == 'true',
    'SCAN_INTERVAL_MS': 300000,
    'SYMBOLS': os.environ.get('FUNDING_RATE_SYMBOLS', 'BTCUSDT,ETHUSDT,SOLUSDT,BNBUSDT,DOGEUSDT').split(','),
    'MAIN_SYMBOLS': ['BTCUSDT', 'ETHUSDT'],
    'MAIN_THRESHOLD_LEVEL1': -0.0005,
    'MAIN_THRESHOLD_LEVEL2': -0.001,
    'ALT_THRESHOLD_LEVEL1': -0.001,
    'ALT_THRESHOLD_LEVEL2': -0.002,
    'COOLDOWN_HOURS': 2,
}

# RSI 配置
RSI = {
    'PERIOD': 14,
    'OVERBOUGHT': 80,
    'OVERSOLD': 20,
    'COOLDOWN_MINUTES': {
        '15m': 15,
        '1h': 60,
        '4h': 240,
        '1d': 1440,
    }
}

# 交易策略默认值
TRADING = {
    'TAKE_PROFIT_PCT': 0.03,
    'STOP_LOSS_PCT': 0.10,
    'MAX_ORDERS_PER_TICK': 5,
    'MAX_INSTANCES_PER_SYMBOL': 3,
    'QUOTE_RESERVE': 10,
    'AUTO_TICK_ENABLED': True,
    'AUTO_TICK_INTERVAL_MS': 30000,
}

# 加密配置（API Secret 加密存储）
ENCRYPTION_KEY = os.environ.get(
    'ENCRYPTION_KEY',
    'B1n@nceC0mp0und!'  # TODO: 生产环境必须修改
)

# 日志
LOGGING = {
    'version': 1,
    'disable_existing_loggers': False,
    'formatters': {
        'verbose': {
            'format': '{asctime} [{levelname}] {name}: {message}',
            'style': '{',
        },
    },
    'handlers': {
        'console': {
            'class': 'logging.StreamHandler',
            'formatter': 'verbose',
        },
    },
    'root': {
        'handlers': ['console'],
        'level': 'INFO',
    },
    'loggers': {
        'apps': {
            'handlers': ['console'],
            'level': 'INFO',
            'propagate': False,
        },
    },
}
