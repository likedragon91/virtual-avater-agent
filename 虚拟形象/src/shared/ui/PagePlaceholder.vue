<script setup>
defineProps({
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    default: '功能开发中 · 后端接口待接入'
  },
  features: {
    type: Array,
    default: () => []
  }
})
</script>

<template>
  <el-card shadow="never" class="placeholder-card">
    <template #header>
      <span class="placeholder-title">{{ title }}</span>
    </template>

    <el-empty :description="description" />

    <div v-if="features.length" class="placeholder-body">
      <p class="placeholder-label">规划中的能力</p>
      <ul class="placeholder-list">
        <li v-for="feature in features" :key="feature">{{ feature }}</li>
      </ul>
    </div>
  </el-card>
</template>

<style scoped>
.placeholder-card {
  min-height: 420px;
}

.placeholder-title {
  font-weight: 600;
}

.placeholder-body {
  max-width: 520px;
  margin: 0 auto 24px;
}

.placeholder-label {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.placeholder-list {
  margin: 0;
  padding-left: 20px;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 2;
}
</style>
