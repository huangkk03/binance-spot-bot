"""
Django management command: 迁移旧库数据到新库
用法: python manage.py migrate_legacy_data
"""
from django.core.management.base import BaseCommand
from django.db import connections, transaction
from django.apps import apps


MIGRATION_MAP = {
    'api_accounts': ('accounts', 'ApiAccount'),
    'cycle_instances': ('trading', 'CycleInstance'),
    'trade_records': ('trading', 'TradeRecord'),
    'cycle_open_records': ('trading', 'CycleOpenRecord'),
    'instance_events': ('trading', 'InstanceEvent'),
    'price_alerts': ('scanners', 'PriceAlert'),
    'funding_rate_alerts': ('scanners', 'FundingRateAlert'),
    'api_config': ('notifications', 'ApiConfig'),
}


class Command(BaseCommand):
    help = '从旧库 binance_compound 迁移数据到新库 binance_compound_v2'

    def add_arguments(self, parser):
        parser.add_argument(
            '--dry-run',
            action='store_true',
            help='只显示迁移计划，不实际写入'
        )

    def handle(self, *args, **options):
        dry_run = options['dry_run']

        self.stdout.write(self.style.SUCCESS('=' * 60))
        self.stdout.write(self.style.SUCCESS('Legacy Data Migration'))
        self.stdout.write(self.style.SUCCESS('=' * 60))

        # 验证连接
        try:
            connections['default'].cursor()
            self.stdout.write(self.style.SUCCESS('[OK] New DB connected'))
        except Exception as e:
            self.stdout.write(self.style.ERROR(f'[ERROR] New DB: {e}'))
            return

        try:
            connections['legacy'].cursor()
            self.stdout.write(self.style.SUCCESS('[OK] Legacy DB connected'))
        except Exception as e:
            self.stdout.write(self.style.WARNING(f'[WARN] Legacy DB: {e}'))
            self.stdout.write('  跳过迁移（无旧库或不可达）')
            return

        total = 0
        for legacy_table, (app_label, model_name) in MIGRATION_MAP.items():
            try:
                count = self._migrate_table(legacy_table, app_label, model_name, dry_run)
                total += count
            except Exception as e:
                self.stdout.write(self.style.ERROR(f'[ERROR] {legacy_table}: {e}'))

        self.stdout.write(self.style.SUCCESS(f'\nDone. Total rows migrated: {total}'))

    def _migrate_table(self, legacy_table, app_label, model_name, dry_run):
        self.stdout.write(f'\n[Processing] {legacy_table} -> {app_label}.{model_name}')

        try:
            Model = apps.get_model(app_label, model_name)
        except LookupError:
            self.stdout.write(self.style.WARNING(f'  Model not found: {app_label}.{model_name}'))
            return 0

        legacy_cursor = connections['legacy'].cursor()
        try:
            legacy_cursor.execute(f'SELECT * FROM {legacy_table}')
        except Exception as e:
            self.stdout.write(f'  Table {legacy_table} not found in legacy DB, skip')
            return 0

        columns = [col[0] for col in legacy_cursor.description]
        rows = legacy_cursor.fetchall()
        self.stdout.write(f'  Found {len(rows)} rows')

        if not rows or dry_run:
            return 0

        model_fields = {f.name for f in Model._meta.get_fields()}
        count = 0

        for row in rows:
            data = {}
            for i, col in enumerate(columns):
                if col == 'is_simulation':
                    continue
                if col not in model_fields:
                    continue
                data[col] = row[i]

            try:
                with transaction.atomic():
                    obj, created = Model.objects.update_or_create(
                        id=row[0],  # 假设 id 在第一列
                        defaults=data
                    )
                    count += 1
            except Exception as e:
                self.stdout.write(self.style.WARNING(f'  Row id={row[0]} error: {e}'))

        self.stdout.write(self.style.SUCCESS(f'  Migrated {count} rows'))
        return count
