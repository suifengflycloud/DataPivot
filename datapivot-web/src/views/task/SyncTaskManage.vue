<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteTask, executeTask, getTask, pageTasks } from '@/api/task'
import type { SyncTaskDetail, SyncTaskVO } from '@/types/model'
import { formatDateTime } from '@/utils/format'
import TaskEditDialog from './components/TaskEditDialog.vue'
import ProgressDialog from './components/ProgressDialog.vue'

const loading = ref(false)
const records = ref<SyncTaskVO[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '' })

const editVisible = ref(false)
const editTask = ref<SyncTaskDetail | null>(null)
const editLoading = ref(false)
const progressVisible = ref(false)
const progressRecordId = ref<number | null>(null)

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const page = await pageTasks(query.current, query.size, query.keyword)
    records.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  query.current = 1
  loadPage()
}

async function openCreate(): Promise<void> {
  editTask.value = null
  editVisible.value = true
}

async function openEdit(row: SyncTaskVO): Promise<void> {
  editLoading.value = true
  try {
    editTask.value = await getTask(row.id)
    editVisible.value = true
  } finally {
    editLoading.value = false
  }
}

/** 手动立即执行 */
async function handleExecute(row: SyncTaskVO): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定立即执行同步任务「${row.taskName}」吗？数据将按映射关系从源库写入目标库。`,
      '执行确认',
      { type: 'warning', confirmButtonText: '立即执行', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  const recordId = await executeTask(row.id)
  progressRecordId.value = recordId
  progressVisible.value = true
}

async function handleDelete(row: SyncTaskVO): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除任务「${row.taskName}」吗？其表映射、字段映射与定时配置将一并删除，执行记录保留。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await deleteTask(row.id)
  ElMessage.success('任务删除成功')
  if (records.value.length === 1 && query.current > 1) {
    query.current -= 1
  }
  loadPage()
}

function onSaved(): void {
  loadPage()
}

onMounted(() => {
  loadPage()
})
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索任务名称"
        clearable
        style="width: 260px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <div class="spacer" />
      <el-button type="primary" :icon="'Plus'" @click="openCreate">新建任务</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="taskName" label="任务名称" min-width="150" show-overflow-tooltip />
      <el-table-column label="源库 → 目标库" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tag type="info" effect="plain" size="small">{{ row.sourceConnName ?? '—' }}</el-tag>
          <span class="arrow"> → </span>
          <el-tag type="primary" effect="plain" size="small">{{ row.targetConnName ?? '—' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="mappingCount" label="表映射数" width="90" align="center" sortable />
      <el-table-column label="冲突策略" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.conflictStrategy === 'ERROR' ? 'danger' : 'primary'" effect="plain">
            {{ row.conflictStrategyLabel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="batchSize" label="批次大小" width="90" align="center" sortable />
      <el-table-column label="同步方式" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.fullSync ? 'success' : 'info'">
            {{ row.fullSync ? '全量' : '增量(预留)' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="165" sortable prop="updateTime">
        <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="success" @click="handleExecute(row as SyncTaskVO)">
            执行
          </el-button>
          <el-button
            size="small"
            type="primary"
            plain
            :loading="editLoading"
            @click="openEdit(row as SyncTaskVO)"
          >
            编辑
          </el-button>
          <el-button size="small" type="danger" plain @click="handleDelete(row as SyncTaskVO)">
            删除
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无同步任务，点击右上角新建" />
      </template>
    </el-table>

    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next, jumper"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="loadPage"
      @size-change="onSearch"
    />

    <TaskEditDialog v-model="editVisible" :task="editTask" @saved="onSaved" />
    <ProgressDialog v-model="progressVisible" :record-id="progressRecordId" />
  </div>
</template>

<style scoped>
.arrow {
  color: #94a3b8;
  font-weight: 600;
}
</style>
