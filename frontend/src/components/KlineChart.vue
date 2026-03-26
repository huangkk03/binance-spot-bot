<template>
  <div class="kline-chart-wrapper">
    <div ref="chartRef" class="chart-container"></div>
    <div class="chart-loading" v-if="loading">加载中...</div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  symbol: {
    type: String,
    required: true
  }
})

const chartRef = ref(null)
const loading = ref(false)
let chart = null
let updateTimer = null

async function fetchKlineData(symbol, interval = '1m', limit = 500) {
  try {
    const response = await fetch(`https://api.binance.com/api/v3/klines?symbol=${symbol}&interval=${interval}&limit=${limit}`)
    const data = await response.json()
    return data.map(item => ({
      time: item[0],
      open: parseFloat(item[1]),
      high: parseFloat(item[2]),
      low: parseFloat(item[3]),
      close: parseFloat(item[4]),
      volume: parseFloat(item[5])
    }))
  } catch (e) {
    console.error('Failed to fetch kline data:', e)
    return []
  }
}

function calculateMA(data, period) {
  const ma = []
  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) {
      ma.push('-')
    } else {
      let sum = 0
      for (let j = 0; j < period; j++) {
        sum += data[i - j].close
      }
      ma.push((sum / period).toFixed(2))
    }
  }
  return ma
}

function calculateBollingerBands(data, period = 20, multiplier = 2) {
  const upper = []
  const middle = calculateMA(data, period)
  const lower = []

  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) {
      upper.push('-')
      lower.push('-')
    } else {
      let sum = 0
      for (let j = 0; j < period; j++) {
        sum += Math.pow(data[i - j].close - parseFloat(middle[i]), 2)
      }
      const stdDev = Math.sqrt(sum / period)
      const middleValue = parseFloat(middle[i])
      upper.push((middleValue + multiplier * stdDev).toFixed(2))
      lower.push((middleValue - multiplier * stdDev).toFixed(2))
    }
  }

  return { upper, lower, middle }
}

function calculateVolumeMA(data, period = 5) {
  const ma = []
  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) {
      ma.push('-')
    } else {
      let sum = 0
      for (let j = 0; j < period; j++) {
        sum += data[i - j].volume
      }
      ma.push((sum / period).toFixed(2))
    }
  }
  return ma
}

async function updateChart() {
  if (!chart) return
  
  loading.value = true
  const data = await fetchKlineData(props.symbol, '1m', 500)
  loading.value = false

  if (data.length === 0) return

  const times = data.map(d => new Date(d.time).toLocaleString('zh-CN'))
  const ohlc = data.map(d => [d.open, d.close, d.low, d.high])
  const volumes = data.map(d => d.volume)

  const ma5 = calculateMA(data, 5)
  const ma10 = calculateMA(data, 10)
  const ma20 = calculateMA(data, 20)
  const ma60 = calculateMA(data, 60)

  const bb = calculateBollingerBands(data, 20, 2)
  const volMA5 = calculateVolumeMA(data, 5)

  const upColor = '#0ecb81'
  const downColor = '#f6465d'

  const option = {
    backgroundColor: '#1a1f2e',
    animation: false,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: 'rgba(26, 31, 46, 0.95)',
      borderColor: '#2a3042',
      textStyle: { color: '#e0e6ed' }
    },
    legend: {
      data: ['MA5', 'MA10', 'MA20', 'MA60', 'BB Upper', 'BB Lower', 'Volume MA5'],
      bottom: 10,
      textStyle: { color: '#848e9c' }
    },
    grid: [
      { left: '10%', right: '8%', top: '8%', height: '55%' },
      { left: '10%', right: '8%', top: '68%', height: '20%' }
    ],
    xAxis: [
      { type: 'category', data: times, gridIndex: 0, axisLine: { lineStyle: { color: '#2a3042' } }, axisLabel: { color: '#848e9c', fontSize: 10, rotate: 30 }, splitLine: { show: false } },
      { type: 'category', data: times, gridIndex: 1, axisLine: { lineStyle: { color: '#2a3042' } }, axisLabel: { show: false }, splitLine: { show: false } }
    ],
    yAxis: [
      { type: 'value', scale: true, gridIndex: 0, splitLine: { lineStyle: { color: '#2a3042', type: 'dashed' } }, axisLine: { lineStyle: { color: '#2a3042' } }, axisLabel: { color: '#848e9c' } },
      { type: 'value', gridIndex: 1, splitLine: { lineStyle: { color: '#2a3042', type: 'dashed' } }, axisLine: { lineStyle: { color: '#2a3042' } }, axisLabel: { color: '#848e9c' } }
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1], start: 70, end: 100 },
      { type: 'slider', xAxisIndex: [0, 1], bottom: 40, height: 20 }
    ],
    series: [
      {
        name: 'K线',
        type: 'candlestick',
        data: ohlc,
        xAxisIndex: 0,
        yAxisIndex: 0,
        itemStyle: {
          color: upColor,
          color0: downColor,
          borderColor: upColor,
          borderColor0: downColor
        }
      },
      { name: 'MA5', type: 'line', data: ma5, xAxisIndex: 0, yAxisIndex: 0, smooth: true, showSymbol: false, lineStyle: { width: 1, color: '#f0b90b' } },
      { name: 'MA10', type: 'line', data: ma10, xAxisIndex: 0, yAxisIndex: 0, smooth: true, showSymbol: false, lineStyle: { width: 1, color: '#8b5cf6' } },
      { name: 'MA20', type: 'line', data: ma20, xAxisIndex: 0, yAxisIndex: 0, smooth: true, showSymbol: false, lineStyle: { width: 1, color: '#06b6d4' } },
      { name: 'MA60', type: 'line', data: ma60, xAxisIndex: 0, yAxisIndex: 0, smooth: true, showSymbol: false, lineStyle: { width: 1, color: '#ec4899' } },
      { name: 'BB Upper', type: 'line', data: bb.upper, xAxisIndex: 0, yAxisIndex: 0, smooth: true, showSymbol: false, lineStyle: { width: 1, color: '#8470ff', type: 'dashed' } },
      { name: 'BB Lower', type: 'line', data: bb.lower, xAxisIndex: 0, yAxisIndex: 0, smooth: true, showSymbol: false, lineStyle: { width: 1, color: '#8470ff', type: 'dashed' } },
      {
        name: 'Volume',
        type: 'bar',
        data: volumes,
        xAxisIndex: 1,
        yAxisIndex: 1,
        itemStyle: {
          color: (params) => {
            const candlestickData = ohlc[params.dataIndex]
            return candlestickData[1] >= candlestickData[0] ? upColor : downColor
          }
        }
      },
      { name: 'Volume MA5', type: 'line', data: volMA5, xAxisIndex: 1, yAxisIndex: 1, smooth: true, showSymbol: false, lineStyle: { width: 1, color: '#f0b90b' } }
    ]
  }

  chart.setOption(option, true)
}

function initChart() {
  if (!chartRef.value) return
  
  chart = echarts.init(chartRef.value, 'dark')
  
  nextTick(() => {
    updateChart()
  })
  
  updateTimer = setInterval(() => {
    updateChart()
  }, 30000)
}

function handleResize() {
  if (chart) {
    chart.resize()
  }
}

watch(() => props.symbol, () => {
  nextTick(() => {
    if (chart) {
      chart.dispose()
    }
    initChart()
  })
})

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
  
  const observer = new MutationObserver(() => {
    nextTick(() => {
      if (chart) {
        chart.resize()
      }
    })
  })
  
  observer.observe(document.body, { childList: true, subtree: true })
})

onUnmounted(() => {
  if (updateTimer) clearInterval(updateTimer)
  if (chart) chart.dispose()
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.kline-chart-wrapper {
  width: 100%;
  height: 70vh;
  min-height: 600px;
  position: relative;
  background: #1a1f2e;
  border-radius: 8px;
  overflow: hidden;
}

.chart-container {
  width: 100%;
  height: 100%;
}

.chart-loading {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: rgba(26, 31, 46, 0.95);
  padding: 1rem 2rem;
  border-radius: 8px;
  color: #f0b90b;
  z-index: 10;
}
</style>
