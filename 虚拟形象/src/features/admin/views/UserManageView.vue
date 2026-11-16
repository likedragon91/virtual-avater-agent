<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getUserList, updateUserStatus } from '@/shared/api/user'
import { useAuthStore } from '@/shared/stores/auth'

const authStore = useAuthStore()

// 用户列表数据
const tableData = ref([])
const loading = ref(false)
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const statusUpdatingId = ref(null)

const isAdmin = computed(() => authStore.isAdmin)

const emptyText = computed(() =>
  isAdmin.value ? '暂无用户数据' : '仅管理员可查看用户列表'
)

const genderText = (row) => {
  if (row.genderDisplayName) return row.genderDisplayName
  return { 0: '未知', 1: '男', 2: '女' }[row.gender] || '未知'
}

const statusText = (row) =>
  row.statusDisplayName || ({ 0: '禁用', 1: '正常', 2: '锁定' }[Number(row.status)] || '正常')

const statusTagType = (status) => {
  switch (Number(status)) {
    case 1:
      return 'success'
    case 2:
      return 'warning'
    default:
      return 'danger'
  }
}

// 获取用户列表（仅管理员）
async function loadList() {
  if (!isAdmin.value) {
    tableData.value = []
    total.value = 0
    return
  }

  loading.value = true
  try {
    const res = await getUserList({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value.trim()
    })

    if (res && res.code === '200') {
      const data = res.data || {}
      tableData.value = data.records || []
      total.value = Number(data.total) || 0
    } else if (res && res.code === '403') {
      tableData.value = []
      total.value = 0
      ElMessage.error(res.msg || '没有访问权限，仅管理员可查看用户列表')
    } else {
      tableData.value = []
      total.value = 0
      ElMessage.error((res && res.msg) || '获取用户列表失败')
    }
  } catch (e) {
    tableData.value = []
    total.value = 0
    if (e && e.isAuthError) return
    ElMessage.error('获取用户列表失败，请检查网络或接口地址')
  } finally {
    loading.value = false
  }
}

async function toggleStatus(row) {
  const nextStatus = Number(row.status) === 1 ? 0 : 1
  const action = nextStatus === 0 ? '禁用' : '启用'

  if (nextStatus === 0 && Number(row.id) === Number(authStore.userInfo?.id)) {
    ElMessage.warning('不能禁用当前登录账号')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要${action}用户“${row.username}”吗？`,
      '修改用户状态',
      {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }

  statusUpdatingId.value = row.id
  try {
    const res = await updateUserStatus(row.id, nextStatus)
    if (res && res.code === '200') {
      ElMessage.success(`已${action}用户“${row.username}”`)
      await loadList()
    } else {
      ElMessage.error((res && res.msg) || `${action}失败`)
    }
  } catch (e) {
    if (e && e.isAuthError) return
    ElMessage.error(`${action}失败，请稍后重试`)
  } finally {
    statusUpdatingId.value = null
  }
}

function handleSearch() {
  currentPage.value = 1
  loadList()
}

function handleSizeChange() {
  currentPage.value = 1
  loadList()
}

function resetSearch() {
  keyword.value = ''
  currentPage.value = 1
  loadList()
}

onMounted(loadList)
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span class="card-title">用户信息</span>
        <div class="card-actions">
          <el-input
            v-model="keyword"
            class="search-input"
            placeholder="请输入账号或昵称查询"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
            @clear="resetSearch"
          />
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </div>
      </div>
    </template>

    <div class="table-scroll">
      <el-table
        v-loading="loading"
        :data="tableData"
        :empty-text="emptyText"
        style="width: 100%"
        height="400"
      >
      <el-table-column prop="username" label="账号" min-width="150" />
      <el-table-column label="昵称" min-width="150">
        <template #default="{ row }">
          {{ row.nickname || row.displayName || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="性别" width="100">
        <template #default="{ row }">
          {{ genderText(row) }}
        </template>
      </el-table-column>
      <el-table-column label="用户类型" width="120">
        <template #default="{ row }">
          {{ row.userTypeDisplayName || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">
            {{ statusText(row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="email" label="邮箱" min-width="200" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            :type="Number(row.status) === 1 ? 'danger' : 'success'"
            :loading="statusUpdatingId === row.id"
            @click="toggleStatus(row)"
          >
            {{ Number(row.status) === 1 ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
      </el-table>
    </div>

    <div class="table-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="loadList"
      />
    </div>
  </el-card>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-weight: 600;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.table-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 900px) {
  .card-header {
    flex-wrap: wrap;
    gap: 8px;
  }

  .card-actions {
    flex-wrap: wrap;
    width: 100%;
  }

  .search-input {
    width: 100% !important;
  }

  .table-pagination {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
