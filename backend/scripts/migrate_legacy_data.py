"""
数据迁移脚本
从 binance_compound（旧库）读取数据，转换并写入 binance_compound_v2（新库）

用法:
    python manage.py shell < scripts/migrate_legacy_data.py
    或
    python scripts/migrate_legacy_data.py
"""
import os
import sys
import django
from decimal import Decimal
from datetime import datetime

# Django 初始化
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'binance_bot.settings')
django.setup()

from django.db import connections, transaction


# 旧库表 -> 新模型映射
MIGRATION_MAP = {
    # 'legacy_table_name': (legacy_app.NewModel, transform_func)
    'api_accounts': ('accounts', 'ApiAccount', None),
    'cycle_instances': ('trading', 'CycleInstance', None),
    'trade_records': ('trading', 'TradeRecord', None),
    'cycle_open_records': ('trading', 'CycleOpenRecord', None),
    'instance_events': ('trading', 'InstanceEvent', None),
    'price_alerts': ('scanners', 'PriceAlert', None),
    'funding_rate_alerts': ('scanners', 'FundingRateAlert', None),
    'api_config': ('notifications', 'ApiConfig', None),
}


def migrate_table(legacy_table: str, app_label: str, model_name: str):
    """迁移单张表"""
    print(f'\n[Migration] {legacy_table} -> {app_label}.{model_name}')

    from django.apps import apps
    Model = apps.get_model(app_label, model_name)

    legacy_cursor = connections['legacy'].cursor()
    legacy_cursor.execute(f'SELECT * FROM {legacy_table}')
    columns = [col[0] for col in legacy_cursor.description]
    rows = legacy_cursor.fetchall()

    print(f'  Found {len(rows)} rows in legacy.{legacy_table}')

    # 列名映射（旧 -> 新）：删除 is_simulation
    new_columns = [c for c in columns if c != 'is_simulation']
    column_index = {c: columns.index(c) for c in columns}

    if not rows:
        print(f'  No data to migrate')
        return 0

    count = 0
    with transaction.atomic():
        for row in rows:
            # 构造新数据 dict，跳过 is_simulation
            data = {}
            for col in columns:
                if col == 'is_simulation':
                    continue
                if col == 'id' and not col.startswith('legacy_'):
                    # 让新库自动生成 id
                    continue
                value = row[column_index[col]]
                data[col] = value

            # 移除新表不存在的字段
            model_fields = {f.name for f in Model._meta.get_fields()}
            data = {k: v for k, v in data.items() if k in model_fields}

            # 字段名映射（如果需要）
            if 'kline_interval' in data:
                # 旧 PriceAlert 字段名 kline_interval
                pass

            try:
                Model.objects.update_or_create(id=row[column_index['id']], defaults=data)
                count += 1
            except Exception as e:
                print(f'  Error migrating row id={row[column_index["id"]]}: {e}')

    print(f'  Migrated {count}/{len(rows)} rows')
    return count


def main():
    """主迁移流程"""
    print('=' * 60)
    print('Legacy Data Migration: binance_compound -> binance_compound_v2')
    print('=' * 60)

    # 验证双库连接
    try:
        connections['default'].cursor()
        print('[OK] New database (default) connected')
    except Exception as e:
        print(f'[ERROR] Cannot connect to new database: {e}')
        return

    try:
        connections['legacy'].cursor()
        print('[OK] Legacy database (legacy) connected')
    except Exception as e:
        print(f'[WARN] Cannot connect to legacy database: {e}')
        print('  (If legacy DB does not exist, this is normal)')
        return

    total = 0
    for legacy_table, (app_label, model_name, _) in MIGRATION_MAP.items():
        try:
            count = migrate_table(legacy_table, app_label, model_name)
            total += count
        except Exception as e:
            print(f'[ERROR] Failed to migrate {legacy_table}: {e}')

    print('\n' + '=' * 60)
    print(f'Migration completed. Total rows: {total}')
    print('=' * 60)


if __name__ == '__main__':
    main()
