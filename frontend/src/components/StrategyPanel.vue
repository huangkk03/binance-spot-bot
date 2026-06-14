<template>
  <div>
    <h3>策略参数（来自环境变量）</h3>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="止盈 (TAKE_PROFIT_PCT)">3%</el-descriptions-item>
      <el-descriptions-item label="止损 (STOP_LOSS_PCT)">10%</el-descriptions-item>
      <el-descriptions-item label="保留 USDT (QUOTE_RESERVE)">10</el-descriptions-item>
      <el-descriptions-item label="每 Tick 最大订单 (MAX_ORDERS_PER_TICK)">5</el-descriptions-item>
      <el-descriptions-item label="监控币种">BTC, ETH, BNB, ADA, DOGE, SOL</el-descriptions-item>
    </el-descriptions>
    <el-alert type="info" :closable="false" style="margin-top: 1rem;">
      策略参数通过 .env 或 settings.py 修改，需要重启 backend 容器生效。
    </el-alert>
  </div>
</template>
