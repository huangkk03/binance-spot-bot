<template>
  <el-menu :default-active="activeMenu" mode="horizontal" router>
    <el-menu-item index="/dashboard">Dashboard</el-menu-item>
    <el-menu-item index="/trading">交易控制台</el-menu-item>
    <el-menu-item index="/strategy">策略配置</el-menu-item>
    <el-menu-item index="/accounts">账户</el-menu-item>
    <el-menu-item index="/history">历史</el-menu-item>
    <el-menu-item index="/settings">设置</el-menu-item>
  </el-menu>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
const route = useRoute()
const activeMenu = computed(() => route.path)
</script>
