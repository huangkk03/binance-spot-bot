<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { useCompoundStore } from './stores/compound'

const store = useCompoundStore()

onMounted(() => {
    store.initWebSocket()
})
</script>
