<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Star, Timer, Warning } from '@element-plus/icons-vue'
import {
  archiveTask,
  createIdempotencyKey,
  createTask,
  listOccurrences,
  listTasks,
  respondOccurrence,
  updateTask
} from '@/shared/api/task'
import { getRelationship } from '@/shared/api/relationship'
import { useNotificationStore } from '@/shared/stores/notification'

const router = useRouter()
const notificationStore = useNotificationStore()

const loading = ref(false)
const tasks = ref([])
const taskTotal = ref(0)
const taskPage = ref(1)
const taskSize = ref(10)
const taskStatus = ref('ACTIVE')

const occurrences = ref([])
const occurrenceLoading = ref(false)
const relationship = ref(null)

const taskDialogVisible = ref(false)
const taskDialogMode = ref('create')
const editingTaskId = ref(null)
const taskFormRef = ref()
const taskSubmitting = ref(false)
const taskForm = reactive({
  title: '',
  note: '',
  dueAt: '',
  priority: 2,
  rrule: 'NONE',
  responseWindowMinutes: 120,
  allowNote: true
})
const taskRules = {
  title: [{ required: true, message: '请输入任务标题', trigger: 'blur' }],
  dueAt: [{ required: true, message: '请选择截止时间', trigger: 'change' }]
}

const responseDialogVisible = ref(false)
const responseSubmitting = ref(false)
const responseOccurrence = ref(null)
const responseCorrecting = ref(false)
const responseResult = ref(null)
const responseForm = reactive({
  completionRatio: 0,
  note: '',
  touched: false,
  idempotencyKey: ''
})
const sliderMarks = {
  0: '没动',
  25: '一点',
  50: '一半',
  75: '大半',
  100: '完成'
}

let pollTimer = null
let dueTimer = null
let unsubscribeNotifications = null

const activeOccurrences = computed(() =>
  occurrences.value.filter((item) => item.canRespond || item.canCorrect)
)
const stageName = computed(() => relationship.value?.stageName || '初识')
const intimacyTotal = computed(() => Number(relationship.value?.intimacyTotal || 0))
const progressPercent = computed(() => Number(relationship.value?.progressPercent || 0))
const todayGained = computed(() => Number(relationship.value?.todayGained || 0))
const dailyCap = computed(() => Number(relationship.value?.dailyCap || 40))
const todayPercent = computed(() =>
  Math.min(100, Math.round((todayGained.value / dailyCap.value) * 100))
)

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatDateTime(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function startOfToday() {
  const date = new Date()
  date.setHours(0, 0, 0, 0)
  return date
}

function occurrenceFrom() {
  return formatDateTime(new Date(startOfToday().getTime() - 24 * 60 * 60 * 1000))
}

function occurrenceTo() {
  return formatDateTime(new Date(startOfToday().getTime() + 2 * 24 * 60 * 60 * 1000 - 1000))
}

async function loadTasks() {
  loading.value = true
  try {
    const data = await listTasks({
      page: taskPage.value,
      size: taskSize.value,
      status: taskStatus.value
    })
    tasks.value = data?.records || []
    taskTotal.value = data?.total || 0
  } catch (error) {
    ElMessage.error(error.message || '加载任务失败')
  } finally {
    loading.value = false
  }
}

function clearDueTimer() {
  if (dueTimer) {
    clearTimeout(dueTimer)
    dueTimer = null
  }
}

/**
 * 在最近一次“还没到点”的 occurrence 到点时主动刷新一次。
 * 轮询是兜底；这个定时器让按钮在到点后立刻出现，而不是等下一次轮询。
 */
function scheduleNextDueRefresh() {
  clearDueTimer()
  const now = Date.now()
  let nearest = Infinity

  for (const item of occurrences.value) {
    if (item.status === 'RESPONDED' || item.status === 'CANCELED' || item.canRespond) continue
    const at = new Date(String(item.occurAt || '').replace(' ', 'T')).getTime()
    if (Number.isNaN(at) || at <= now) continue
    if (at < nearest) nearest = at
  }

  if (!Number.isFinite(nearest)) return
  const delay = Math.min(Math.max(nearest - now + 1500, 1000), 2147483647)
  dueTimer = setTimeout(() => {
    dueTimer = null
    loadOccurrences({ silent: true })
  }, delay)
}

async function loadOccurrences({ silent = false } = {}) {
  if (!silent) occurrenceLoading.value = true
  try {
    const data = await listOccurrences({
      from: occurrenceFrom(),
      to: occurrenceTo(),
      status: 'PENDING,RINGING,MISSED,RESPONDED',
      limit: 100
    })
    occurrences.value = data || []
    scheduleNextDueRefresh()
  } catch (error) {
    if (!silent) ElMessage.error(error.message || '加载待回应任务失败')
  } finally {
    if (!silent) occurrenceLoading.value = false
  }
}

function handleTaskNotification(notification) {
  if (notification?.type === 'TASK_REMINDER') {
    loadOccurrences({ silent: true })
  }
}

function handleVisibilityChange() {
  // 后台标签页的定时器会被浏览器节流，回到页面时补一次
  if (document.visibilityState === 'visible') {
    loadOccurrences({ silent: true })
  }
}

async function loadRelationship() {
  try {
    relationship.value = await getRelationship()
  } catch (error) {
    ElMessage.error(error.message || '加载关系数据失败')
  }
}

async function refreshAll() {
  await Promise.all([loadTasks(), loadOccurrences(), loadRelationship()])
}

function resetTaskForm() {
  const nextHour = new Date(Date.now() + 60 * 60 * 1000)
  Object.assign(taskForm, {
    title: '',
    note: '',
    dueAt: formatDateTime(nextHour),
    priority: 2,
    rrule: 'NONE',
    responseWindowMinutes: 120,
    allowNote: true
  })
  editingTaskId.value = null
  taskDialogMode.value = 'create'
}

function openCreate() {
  resetTaskForm()
  taskDialogVisible.value = true
}

function normalizeRruleForForm(rrule) {
  if (!rrule) return 'NONE'
  const value = String(rrule).toUpperCase()
  if (value.includes('FREQ=DAILY')) return 'DAILY'
  if (value.includes('FREQ=WEEKLY')) return 'WEEKLY'
  if (value.includes('FREQ=MONTHLY')) return 'MONTHLY'
  return value
}

function openEdit(task) {
  taskDialogMode.value = 'edit'
  editingTaskId.value = task.id
  Object.assign(taskForm, {
    title: task.title || '',
    note: task.note || '',
    dueAt: task.dueAt || formatDateTime(new Date(Date.now() + 60 * 60 * 1000)),
    priority: task.priority || 2,
    rrule: normalizeRruleForForm(task.rrule),
    responseWindowMinutes: task.responseWindowMinutes || 120,
    allowNote: task.allowNote !== false
  })
  taskDialogVisible.value = true
}

async function submitTask() {
  try {
    await taskFormRef.value.validate()
  } catch {
    return
  }

  taskSubmitting.value = true
  try {
    const payload = {
      title: taskForm.title.trim(),
      note: taskForm.note || '',
      dueAt: taskForm.dueAt,
      priority: taskForm.priority,
      rrule: taskForm.rrule,
      responseWindowMinutes: taskForm.responseWindowMinutes,
      allowNote: taskForm.allowNote
    }
    if (taskDialogMode.value === 'create') {
      await createTask(payload)
      ElMessage.success('任务已创建')
    } else {
      await updateTask(editingTaskId.value, payload)
      ElMessage.success('任务已更新')
    }
    taskDialogVisible.value = false
    await refreshAll()
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    taskSubmitting.value = false
  }
}

async function handleArchive(task) {
  try {
    await ElMessageBox.confirm(`归档「${task.title}」后不再提醒，历史回应会保留。`, '归档任务', {
      type: 'warning',
      confirmButtonText: '归档',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await archiveTask(task.id)
    ElMessage.success('已归档')
    await refreshAll()
  } catch (error) {
    ElMessage.error(error.message || '归档失败')
  }
}

async function handleRestore(task) {
  try {
    await updateTask(task.id, { status: 'ACTIVE' })
    ElMessage.success('已恢复到进行中')
    await refreshAll()
  } catch (error) {
    ElMessage.error(error.message || '恢复失败')
  }
}

function switchStatus(status) {
  if (taskStatus.value === status) return
  taskStatus.value = status
  taskPage.value = 1
  loadTasks()
}

function handlePageChange(page) {
  taskPage.value = page
  loadTasks()
}

function openResponse(occurrence, correcting = false) {
  responseOccurrence.value = occurrence
  responseCorrecting.value = correcting || occurrence.canCorrect
  responseResult.value = null
  responseForm.completionRatio = responseCorrecting.value
    ? Number(occurrence.completionRatio || 0)
    : 0
  responseForm.note = occurrence.responseNote || ''
  responseForm.touched = responseCorrecting.value
  responseForm.idempotencyKey = createIdempotencyKey()
  responseDialogVisible.value = true
}

function onSliderChange() {
  responseForm.touched = true
}

async function submitResponse() {
  if (!responseForm.touched) {
    ElMessage.warning('请先拖动滑块选择完成度')
    return
  }
  if (!responseOccurrence.value?.id) return

  responseSubmitting.value = true
  try {
    const result = await respondOccurrence(responseOccurrence.value.id, {
      completionRatio: responseForm.completionRatio,
      note: responseForm.note,
      idempotencyKey: responseForm.idempotencyKey
    })
    responseResult.value = result
    ElMessage.success(result.corrected ? '修正成功' : '已记录，谢谢你的回应')
    await Promise.all([loadOccurrences(), loadRelationship()])
  } catch (error) {
    ElMessage.error(error.message || '提交失败')
  } finally {
    responseSubmitting.value = false
  }
}

function closeResponse() {
  responseDialogVisible.value = false
  responseOccurrence.value = null
  responseResult.value = null
}

function priorityTagType(priority) {
  if (priority === 1) return 'info'
  if (priority === 3) return 'danger'
  return 'warning'
}

function occurrenceTagType(status) {
  if (status === 'RINGING') return 'warning'
  if (status === 'MISSED') return 'danger'
  if (status === 'RESPONDED') return 'success'
  return 'info'
}

function formatDelta(value) {
  const number = Number(value || 0)
  if (number > 0) return `+${number.toFixed(2)}`
  return number.toFixed(2)
}

onMounted(async () => {
  unsubscribeNotifications = notificationStore.subscribe(handleTaskNotification)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  await refreshAll()
  pollTimer = setInterval(() => loadOccurrences({ silent: true }), 30_000)
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  clearDueTimer()
  if (unsubscribeNotifications) {
    unsubscribeNotifications()
    unsubscribeNotifications = null
  }
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <div class="task-view">
    <div class="page-header">
      <div>
        <div class="header-title">任务</div>
        <div class="header-tip">自己布置闹钟，到点自评完成度；0% 也算一次认真回应。</div>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建任务</el-button>
      </div>
    </div>

    <el-card v-if="relationship" shadow="never" class="relationship-card">
      <div class="relationship-main">
        <div class="relationship-left">
          <div class="relationship-stage">
            <el-icon><Star /></el-icon>
            <span>{{ stageName }}</span>
          </div>
          <div class="relationship-total">
            <span class="number">{{ intimacyTotal.toFixed(1) }}</span>
            <span class="unit">/ 1000 亲密度</span>
          </div>
          <el-progress :percentage="progressPercent" :stroke-width="8" :show-text="false" />
          <div class="relationship-sub">
            今日已得 {{ todayGained.toFixed(1) }} / {{ dailyCap }}
            <span class="dot">·</span>
            连续 {{ relationship.streakDays || 0 }} 天
            <span class="dot">·</span>
            今日计分 {{ relationship.taskCountToday || 0 }} / {{ relationship.maxDailyTasks || 8 }}
          </div>
        </div>
        <div class="relationship-right">
          <el-progress
            type="circle"
            :percentage="todayPercent"
            :width="76"
            :stroke-width="7"
          />
          <el-button link type="primary" @click="router.push('/relationship')">
            查看关系
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">待回应</span>
          <el-tag v-if="activeOccurrences.length" type="warning" size="small">
            {{ activeOccurrences.length }} 条
          </el-tag>
        </div>
      </template>

      <div v-loading="occurrenceLoading">
        <div v-if="!activeOccurrences.length" class="empty-line">
          现在没有需要回应的任务，按自己的节奏来就好。
        </div>
        <div v-else class="occurrence-list">
          <div v-for="item in activeOccurrences" :key="item.id" class="occurrence-item">
            <div class="occurrence-main">
              <div class="occurrence-title">
                {{ item.title }}
                <el-tag :type="occurrenceTagType(item.status)" size="small">
                  {{ item.statusLabel }}
                </el-tag>
                <el-tag v-if="item.late" type="danger" size="small">补评</el-tag>
                <el-tag :type="priorityTagType(item.priority)" size="small">
                  {{ item.priorityLabel }}优先级
                </el-tag>
              </div>
              <div class="occurrence-meta">
                <el-icon><Timer /></el-icon>
                {{ item.occurAt }}
                <span v-if="item.status === 'RESPONDED'">
                  · 已评 {{ item.completionRatio }}%
                </span>
              </div>
            </div>
            <div class="occurrence-actions">
              <el-button
                v-if="item.canRespond"
                type="primary"
                size="small"
                @click="openResponse(item, false)"
              >
                回应
              </el-button>
              <el-button
                v-else-if="item.canCorrect"
                size="small"
                @click="openResponse(item, true)"
              >
                修正
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">任务列表</span>
          <div class="tab-buttons">
            <el-button
              size="small"
              :type="taskStatus === 'ACTIVE' ? 'primary' : ''"
              @click="switchStatus('ACTIVE')"
            >
              进行中
            </el-button>
            <el-button
              size="small"
              :type="taskStatus === 'ARCHIVED' ? 'primary' : ''"
              @click="switchStatus('ARCHIVED')"
            >
              已归档
            </el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="tasks" empty-text="还没有任务">
        <el-table-column label="任务" min-width="220">
          <template #default="{ row }">
            <div class="task-title">{{ row.title }}</div>
            <div v-if="row.note" class="task-note">{{ row.note }}</div>
          </template>
        </el-table-column>
        <el-table-column label="截止时间" width="170">
          <template #default="{ row }">{{ row.dueAt }}</template>
        </el-table-column>
        <el-table-column label="优先级" width="90">
          <template #default="{ row }">
            <el-tag :type="priorityTagType(row.priority)" size="small">
              {{ row.priorityLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="重复" width="90">
          <template #default="{ row }">{{ row.rruleLabel }}</template>
        </el-table-column>
        <el-table-column label="下一次" width="170">
          <template #default="{ row }">
            <span v-if="row.nextOccurrenceAt">{{ row.nextOccurrenceAt }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="taskStatus === 'ACTIVE'"
              link
              type="danger"
              :icon="Delete"
              @click="handleArchive(row)"
            >
              归档
            </el-button>
            <el-button v-else link type="primary" @click="handleRestore(row)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          layout="total, prev, pager, next"
          :total="taskTotal"
          :page-size="taskSize"
          :current-page="taskPage"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="taskDialogVisible"
      :title="taskDialogMode === 'create' ? '新建任务' : '编辑任务'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="taskFormRef" :model="taskForm" :rules="taskRules" label-width="96px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="taskForm.title" maxlength="200" show-word-limit placeholder="例如：交周报" />
        </el-form-item>
        <el-form-item label="截止时间" prop="dueAt">
          <el-date-picker
            v-model="taskForm.dueAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择日期时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="taskForm.priority" style="width: 100%">
            <el-option :value="1" label="低（权重 0.8）" />
            <el-option :value="2" label="普通（权重 1.0）" />
            <el-option :value="3" label="高（权重 1.3）" />
          </el-select>
        </el-form-item>
        <el-form-item label="重复">
          <el-select v-model="taskForm.rrule" style="width: 100%">
            <el-option value="NONE" label="不重复" />
            <el-option value="DAILY" label="每天" />
            <el-option value="WEEKLY" label="每周" />
            <el-option value="MONTHLY" label="每月" />
          </el-select>
        </el-form-item>
        <el-form-item label="回应窗口">
          <el-input-number
            v-model="taskForm.responseWindowMinutes"
            :min="15"
            :max="720"
            :step="15"
          />
          <span class="form-tip">分钟（15 分钟 ~ 12 小时）</span>
        </el-form-item>
        <el-form-item label="允许备注">
          <el-switch v-model="taskForm.allowNote" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="taskForm.note"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
            placeholder="可选"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="taskSubmitting" @click="submitTask">
          {{ taskDialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="responseDialogVisible"
      :title="responseCorrecting ? '修正完成度' : '这次做得怎么样？'"
      width="520px"
      destroy-on-close
      @closed="closeResponse"
    >
      <div v-if="responseOccurrence" class="response-task">
        <div class="response-title">{{ responseOccurrence.title }}</div>
        <div class="response-meta">
          {{ responseOccurrence.occurAt }}
          <el-tag v-if="responseOccurrence.late" type="danger" size="small">补评，系数 0.3</el-tag>
        </div>
      </div>

      <div v-if="!responseResult">
        <div class="slider-head">
          <span class="slider-value">{{ responseForm.completionRatio }}%</span>
          <span class="slider-tip">
            {{ responseForm.touched ? '按真实感受拖动即可，0% 也合法' : '请先拖动滑块' }}
          </span>
        </div>
        <el-slider
          v-model="responseForm.completionRatio"
          :marks="sliderMarks"
          :step="1"
          :show-tooltip="true"
          @change="onSliderChange"
        />
        <el-input
          v-model="responseForm.note"
          type="textarea"
          :rows="3"
          maxlength="100"
          show-word-limit
          placeholder="想补一句话吗？可选"
          class="response-note"
        />
      </div>

      <div v-else class="result-panel">
        <el-icon class="result-icon"><Star /></el-icon>
        <div class="result-feedback">{{ responseResult.feedback }}</div>
        <div class="result-grid">
          <div>
            <div class="result-label">本次亲密度</div>
            <div class="result-value">{{ formatDelta(responseResult.intimacyDelta) }}</div>
          </div>
          <div>
            <div class="result-label">当前总分</div>
            <div class="result-value">{{ Number(responseResult.intimacyTotal || 0).toFixed(1) }}</div>
          </div>
          <div>
            <div class="result-label">关系阶段</div>
            <div class="result-value">{{ responseResult.stageName }}</div>
          </div>
        </div>
        <div class="result-sub">
          今日 {{ Number(responseResult.dailyGained || 0).toFixed(1) }} / {{ responseResult.dailyCap }}，
          已计分 {{ responseResult.taskCountToday }} / {{ responseResult.maxDailyTasks }} 个任务
        </div>
      </div>

      <template #footer>
        <template v-if="responseResult">
          <el-button type="primary" @click="closeResponse">关闭</el-button>
        </template>
        <template v-else>
          <el-button @click="closeResponse">取消</el-button>
          <el-button
            type="primary"
            :loading="responseSubmitting"
            :disabled="!responseForm.touched"
            @click="submitResponse"
          >
            {{ responseCorrecting ? '保存修正' : '提交回应' }}
          </el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.task-view {
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

.header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.relationship-card :deep(.el-card__body) {
  padding: 18px 20px;
}

.relationship-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.relationship-left {
  flex: 1;
  min-width: 0;
}

.relationship-stage {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-color-primary);
  font-weight: 600;
}

.relationship-total {
  margin: 8px 0;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.relationship-total .number {
  font-size: 30px;
  font-weight: 700;
}

.relationship-total .unit {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.relationship-sub {
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.relationship-sub .dot {
  margin: 0 6px;
}

.relationship-right {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-weight: 600;
}

.tab-buttons {
  display: flex;
  gap: 8px;
}

.empty-line {
  padding: 18px 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.occurrence-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.occurrence-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: #fff;
}

.occurrence-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-weight: 600;
}

.occurrence-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.occurrence-actions {
  flex-shrink: 0;
}

.task-title {
  font-weight: 500;
}

.task-note {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.muted {
  color: var(--el-text-color-placeholder);
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.response-task {
  margin-bottom: 16px;
}

.response-title {
  font-size: 16px;
  font-weight: 600;
}

.response-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.slider-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}

.slider-value {
  font-size: 30px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.slider-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.response-note {
  margin-top: 24px;
}

.result-panel {
  padding: 8px 4px 4px;
  text-align: center;
}

.result-icon {
  font-size: 34px;
  color: var(--el-color-warning);
}

.result-feedback {
  margin: 10px 0 18px;
  font-size: 16px;
  font-weight: 600;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  padding: 14px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

.result-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.result-value {
  margin-top: 4px;
  font-size: 20px;
  font-weight: 700;
}

.result-sub {
  margin-top: 14px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
  }

  .relationship-main {
    flex-direction: column;
    align-items: stretch;
  }

  .relationship-right {
    flex-direction: row;
    justify-content: space-between;
  }

  .occurrence-item {
    align-items: flex-start;
    flex-direction: column;
  }

  .result-grid {
    grid-template-columns: 1fr;
  }
}
</style>
