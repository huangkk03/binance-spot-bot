"""
WSGI config for binance_bot project.
用于 Gunicorn 部署
"""
import os
from django.core.wsgi import get_wsgi_application

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'binance_bot.settings')

application = get_wsgi_application()
