<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteRecord, getRecord, pageRecords } from '@/api/record'
import { listTasks } from '@/api/task'
import type { RecordVO, SyncTaskVO } from '@/types/model'
import { formatDateTime, formatDuration, formatNumber } from '@/utils/format'

const loading = ref(false)
const records = ref<RecordVO[]>([])
const total = ref(0)
const tasks = ref<SyncTaskVO[]>([])
const query = reactive({
  current: 1,
  size: 10,
  taskId: null as number | null,
  status: '',
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<RecordVO | null>(null)

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const page = await pageRecords(query.current, query.size, query.taskId, query.status)
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

async function openDetail(row: RecordVO): Promise<void> {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getRecord(row.id)
  } finally {
    detailLoading.value = false
  }
}

async function handleDelete(row: RecordVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除执行记录 #${row.id} 吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await deleteRecord(row.id)
  ElMessage.success('记录删除成功')
  if (records.value.length === 1 && query.current > 1) {
    query.current -= 1
  }
  loadPage()
}

function statusTagType(status: string): 'success' | 'danger' | 'primary' {
  if (status === 'SUCCESS') {
    return 'success'
  }
  return status === 'FAILED' ? 'danger' : 'primary'
}

function statusText(status: string): string {
  if (status === 'SUCCESS') {
    return '成功'
  }
  return status === 'FAILED' ? '失败' : '执行中'
}

onMounted(async () => {
  loadPage()
  tasks.value = await listTasks()
})
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-select
        v-model="query.taskId"
        placeholder="按任务筛选（全部）"
        clearable
        filterable
        style="width: 240px"
      >
        <el-option
          v-for="task in tasks"
          :key="task.id"
          :value="task.id"
          :label="task.taskName"
        />
      </el-select>
      <el-select v-model="query.status" placeholder="按状态筛选（全部）" clearable style="width: 160px">
        <el-option value="RUNNING" label="执行中" />
        <el-option value="SUCCESS" label="成功" />
        <el-option value="FAILED" label="失败" />
      </el-select>
      <el-button type="primary" @click="onSearch">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="id" label="记录ID" width="80" sortable />
      <el-table-column prop="taskName" label="任务名称" min-width="150" show-overflow-tooltip />
      <el-table-column label="触发方式" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.triggerType === 'MANUAL' ? 'info' : 'warning'" effect="plain">
            {{ row.triggerType === 'MANUAL' ? '手动执行' : '定时调度' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.status)" effect="dark">
            {{ statusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="已处理/成功/失败" width="150" align="center">
        <template #default="{ row }">
          <span class="count-success">{{ formatNumber(row.successCount) }}</span>
          <span class="count-sep"> / </span>
          <span class="count-fail">{{ formatNumber(row.failCount) }}</span>
          <span class="count-sep"> / </span>
          <span>{{ formatNumber(row.totalCount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" width="165" sortable prop="startTime">
        <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="耗时" width="90" align="center" sortable prop="durationMs">
        <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" plain @click="openDetail(row as RecordVO)">
            日志详情
          </el-button>
          <el-button size="small" type="danger" plain @click="handleDelete(row as RecordVO)">
            删除
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无执行记录" />
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

    <!-- 日志详情 -->
    <el-dialog v-model="detailVisible" title="执行日志详情" width="760px" top="6vh">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="3" border size="small" style="margin-bottom: 12px">
            <el-descriptions-item label="任务名称">{{ detail.taskName }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="statusTagType(detail.status)">
                {{ statusText(detail.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="触发方式">
              {{ detail.triggerType === 'MANUAL' ? '手动执行' : '定时调度' }}
            </el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ formatDateTime(detail.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ formatDateTime(detail.endTime) }}</el-descriptions-item>
            <el-descriptions-item label="耗时">{{ formatDuration(detail.durationMs) }}</el-descriptions-item>
            <el-descriptions-item label="成功条数">{{ formatNumber(detail.successCount) }}</el-descriptions-item>
            <el-descriptions-item label="失败条数">{{ formatNumber(detail.failCount) }}</el-descriptions-item>
            <el-descriptions-item label="已处理">{{ formatNumber(detail.totalCount) }}</el-descriptions-item>
          </el-descriptions>
          <div class="log-title">执行日志</div>
          <div class="log-panel detail-log">{{ detail.errorLog || '（无日志）' }}</div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.count-success {
  color: #059669;
  font-weight: 600;
}

.count-fail {
  color: #dc2626;
  font-weight: 600;
}

.count-sep {
  color: #cbd5e1;
}

.log-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.detail-log {
  height: 380px;
}
</style>
