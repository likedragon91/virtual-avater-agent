<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getUserList } from '@/shared/api/user'
import { useAuthStore } from '@/shared/stores/auth'

const authStore = useAuthStore()

// 用户列表数据
const tableData = ref([])
const loading = ref(false)
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const isAdmin = computed(() => authStore.isAdmin)

const emptyText = computed(() =>
  isAdmin.value ? '暂无用户数据' : '仅管理员可查看用户列表'
)

const genderText = (row) => {
  if (row.genderDisplayName) return row.genderDisplayName
  return { 0: '未知', 1: '男', 2: '女' }[row.gender] || '未知'
}

const statusText = (row) => row.statusDisplayName || (row.status === 1 ? '正常' : '禁用')

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
    </el-table>

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
</style>
