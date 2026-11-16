<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Star, TrendCharts } from '@element-plus/icons-vue'
import { getRelationship, getRelationshipLogs } from '@/shared/api/relationship'

const stages = [
  { code: 'S0', name: '初识', threshold: 0 },
  { code: 'S1', name: '熟悉', threshold: 150 },
  { code: 'S2', name: '亲近', threshold: 400 },
  { code: 'S3', name: '信赖', threshold: 700 },
  { code: 'S4', name: '羁绊', threshold: 1000 }
]

const loading = ref(false)
const logsLoading = ref(false)
const relationship = ref(null)
const logs = ref([])
const logTotal = ref(0)
const logPage = ref(1)
const logSize = ref(10)

const stageIndex = computed(() => {
  const code = relationship.value?.stageCode || 'S0'
  return Math.max(0, stages.findIndex((item) => item.code === code))
})

const intimacyTotal = computed(() => Number(relationship.value?.intimacyTotal || 0))
const progressPercent = computed(() => Number(relationship.value?.progressPercent || 0))
const todayGained = computed(() => Number(relationship.value?.todayGained || 0))
const dailyCap = computed(() => Number(relationship.value?.dailyCap || 40))
const todayPercent = computed(() =>
  Math.min(100, Math.round((todayGained.value / dailyCap.value) * 100))
)
const nextStage = computed(() => {
  const code = relationship.value?.nextStageCode
  return stages.find((item) => item.code === code) || null
})

async function loadRelationship() {
  loading.value = true
  try {
    relationship.value = await getRelationship()
  } catch (error) {
    ElMessage.error(error.message || '加载关系数据失败')
  } finally {
    loading.value = false
  }
}

async function loadLogs() {
  logsLoading.value = true
  try {
    const data = await getRelationshipLogs({ page: logPage.value, size: logSize.value })
    logs.value = data?.records || []
    logTotal.value = data?.total || 0
  } catch (error) {
    ElMessage.error(error.message || '加载成长记录失败')
  } finally {
    logsLoading.value = false
  }
}

function handlePageChange(page) {
  logPage.value = page
  loadLogs()
}

function formatDelta(value) {
  const number = Number(value || 0)
  if (number > 0) return `+${number.toFixed(2)}`
  if (number < 0) return number.toFixed(2)
  return '0.00'
}

function deltaType(value) {
  const number = Number(value || 0)
  if (number > 0) return 'success'
  if (number < 0) return 'danger'
  return 'info'
}

function reasonType(reason) {
  if (reason === 'LEVEL_UP') return 'primary'
  if (reason === 'ADJUST') return 'warning'
  if (reason === 'DAILY_BONUS') return 'success'
  return 'info'
}

onMounted(async () => {
  await Promise.all([loadRelationship(), loadLogs()])
})
</script>

<template>
  <div class="relationship-view">
    <div class="page-header">
      <div>
        <div class="header-title">关系</div>
        <div class="header-tip">亲密度只增不减，阶段只升不降；每天最多计分 8 个任务。</div>
      </div>
      <el-button :icon="TrendCharts" @click="loadRelationship">刷新</el-button>
    </div>

    <el-card v-loading="loading" shadow="never" class="hero-card">
      <div class="hero-top">
        <div>
          <div class="stage-line">
            <el-icon><Star /></el-icon>
            <span class="stage-name">{{ relationship?.stageName || '初识' }}</span>
            <el-tag size="small" type="info">{{ relationship?.stageCode || 'S0' }}</el-tag>
          </div>
          <div class="total-line">
            <span class="total-number">{{ intimacyTotal.toFixed(1) }}</span>
            <span class="total-unit">/ 1000</span>
          </div>
        </div>
        <el-progress
          type="circle"
          :percentage="progressPercent"
          :width="96"
          :stroke-width="8"
        />
      </div>

      <el-progress :percentage="progressPercent" :stroke-width="10" :show-text="false" />

      <div class="stage-track">
        <div
          v-for="(stage, index) in stages"
          :key="stage.code"
          class="stage-node"
          :class="{ active: index === stageIndex, reached: index <= stageIndex }"
        >
          <div class="stage-dot" />
          <div class="stage-label">{{ stage.name }}</div>
          <div class="stage-threshold">{{ stage.threshold }}</div>
        </div>
      </div>

      <div class="next-line">
        <span v-if="nextStage">
          距离「{{ nextStage.name }}」还差
          <strong>{{ Math.max(0, nextStage.threshold - intimacyTotal).toFixed(1) }}</strong>
        </span>
        <span v-else>已经到达最高阶段「羁绊」。</span>
      </div>
    </el-card>

    <div class="stat-grid">
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">今日已得</div>
        <div class="stat-value">{{ todayGained.toFixed(1) }} <span>/ {{ dailyCap }}</span></div>
        <el-progress :percentage="todayPercent" :show-text="false" :stroke-width="6" />
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">今日计分任务</div>
        <div class="stat-value">
          {{ relationship?.taskCountToday || 0 }} <span>/ {{ relationship?.maxDailyTasks || 8 }}</span>
        </div>
        <div class="stat-hint">超过后仍可回应，只是不再加分</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">连续完成</div>
        <div class="stat-value">{{ relationship?.streakDays || 0 }} <span>天</span></div>
        <div class="stat-hint">最多按 10 天计入连击系数</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">表达上限</div>
        <div class="stat-value small">{{ relationship?.userCapStage || '未设置' }}</div>
        <div class="stat-hint">阶段决定能说多深</div>
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">成长记录</span>
          <span class="card-sub">每次加分、修正、升级都会留痕</span>
        </div>
      </template>

      <div class="table-scroll">
        <el-table v-loading="logsLoading" :data="logs" empty-text="还没有成长记录">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ row.createdAt }}</template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="reasonType(row.reason)" size="small">{{ row.reasonLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变化" width="100">
          <template #default="{ row }">
            <el-tag :type="deltaType(row.delta)" size="small">
              {{ formatDelta(row.delta) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变化后总分" width="130">
          <template #default="{ row }">{{ Number(row.balanceAfter || 0).toFixed(1) }}</template>
        </el-table-column>
        <el-table-column label="关联" min-width="140">
          <template #default="{ row }">
            <span class="muted">{{ row.refType || '—' }}</span>
            <span v-if="row.refId" class="muted"> #{{ row.refId }}</span>
          </template>
        </el-table-column>
        </el-table>
      </div>

      <div class="pagination">
        <el-pagination
          layout="total, prev, pager, next"
          :total="logTotal"
          :page-size="logSize"
          :current-page="logPage"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.relationship-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.header-title {
  font-size: 20px;
  font-weight: 600;
}

.header-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.hero-card :deep(.el-card__body) {
  padding: 22px 24px;
}

.hero-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.stage-line {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-color-primary);
}

.stage-name {
  font-size: 20px;
  font-weight: 700;
}

.total-line {
  margin-top: 8px;
}

.total-number {
  font-size: 40px;
  font-weight: 800;
  line-height: 1;
}

.total-unit {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
}

.stage-track {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  margin-top: 18px;
}

.stage-node {
  position: relative;
  text-align: center;
  color: var(--el-text-color-secondary);
}

.stage-dot {
  width: 12px;
  height: 12px;
  margin: 0 auto 6px;
  border-radius: 50%;
  border: 2px solid var(--el-border-color);
  background: #fff;
}

.stage-node.reached .stage-dot {
  border-color: var(--el-color-primary);
}

.stage-node.active .stage-dot {
  background: var(--el-color-primary);
  box-shadow: 0 0 0 4px var(--el-color-primary-light-8);
}

.stage-node.active .stage-label {
  color: var(--el-color-primary);
  font-weight: 600;
}

.stage-label {
  font-size: 13px;
}

.stage-threshold {
  margin-top: 2px;
  font-size: 11px;
}

.next-line {
  margin-top: 16px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.stat-card :deep(.el-card__body) {
  padding: 16px;
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stat-value {
  margin: 8px 0;
  font-size: 24px;
  font-weight: 700;
}

.stat-value.small {
  font-size: 18px;
}

.stat-value span {
  font-size: 13px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.stat-hint {
  margin-top: 4px;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.card-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.card-title {
  font-weight: 600;
}

.card-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.muted {
  color: var(--el-text-color-placeholder);
}

@media (max-width: 900px) {
  .page-header {
    flex-direction: column;
  }

  .hero-card :deep(.el-card__body) {
    padding: 18px 16px;
  }

  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .card-header {
    flex-wrap: wrap;
  }

  .stage-label {
    font-size: 12px;
  }

  .pagination {
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .hero-top {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .stat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
