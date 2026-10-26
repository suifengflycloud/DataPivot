<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getProgress } from '@/api/task'
import { formatNumber } from '@/utils/format'
import type { SyncProgress } from '@/types/model'

/**
 * 同步执行进度弹窗：每秒轮询进度接口，实时展示进度条、计数与滚动日志。
 * 关闭弹窗不影响后台执行，任务结果可在「任务执行记录」查看。
 */
const props = defineProps<{
  modelValue: boolean
  recordId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const progress = ref<SyncProgress | null>(null)
const pollingErrorCount = ref(0)
let timer: ReturnType<typeof setInterval> | null = null
const logPanelRef = ref<HTMLElement | null>(null)

const isRunning = computed(() => progress.value?.status === 'RUNNING')
const statusTagType = computed(() => {
  if (!progress.value) {
    return 'info'
  }
  if (progress.value.status === 'SUCCESS') {
    return 'success'
  }
  return progress.value.status === 'FAILED' ? 'danger' : 'primary'
})

async function poll(): Promise<void> {
  if (!props.recordId) {
    return
  }
  try {
    progress.value = await getProgress(props.recordId)
    pollingErrorCount.value = 0
    // 日志面板自动滚动到底部
    requestAnimationFrame(() => {
      const panel = logPanelRef.value
      if (panel) {
        panel.scrollTop = panel.scrollHeight
      }
    })
    if (progress.value.status !== 'RUNNING') {
      stopPolling()
      if (progress.value.status === 'SUCCESS') {
        ElMessage.success(`任务执行成功，共写入 ${formatNumber(progress.value.successCount)} 条`)
      } else {
        ElMessage.error('任务执行失败，请查看执行日志')
      }
    }
  } catch {
    // 进度接口异常（如记录刚被清理）：连续失败 5 次停止轮询
    pollingErrorCount.value += 1
    if (pollingErrorCount.value >= 5) {
      stopPolling()
    }
  }
}

function startPolling(): void {
  stopPolling()
  pollingErrorCount.value = 0
  poll()
  timer = setInterval(poll, 1000)
}

function stopPolling(): void {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
}

watch(
  () => [props.modelValue, props.recordId] as const,
  ([visible, recordId]) => {
    if (visible && recordId) {
      progress.value = null
      startPolling()
    } else {
      stopPolling()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  stopPolling()
})

function handleClose(): void {
  stopPolling()
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="同步执行进度"
    width="760px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <template v-if="progress">
      <!-- 状态与当前表 -->
      <div class="progress-header">
        <el-tag :type="statusTagType" size="large" effect="dark">
          {{ progress.status === 'RUNNING' ? '执行中' : progress.status === 'SUCCESS' ? '成功' : '失败' }}
        </el-tag>
        <span class="task-name">{{ progress.taskName }}</span>
        <span class="current-table">当前：{{ progress.currentTable || '—' }}</span>
      </div>

      <!-- 进度条 -->
      <el-progress
        :percentage="progress.percent"
        :status="progress.status === 'FAILED' ? 'exception' : progress.status === 'SUCCESS' ? 'success' : undefined"
        :stroke-width="14"
        :format="(p: number) => `${p}%`"
        style="margin: 14px 0"
      />

      <!-- 计数 -->
      <el-row :gutter="12" class="stat-row">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-num">{{ formatNumber(progress.total) }}</div>
            <div class="stat-label">总条数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-num">{{ formatNumber(progress.processed) }}</div>
            <div class="stat-label">已处理</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-num success">{{ formatNumber(progress.successCount) }}</div>
            <div class="stat-label">成功</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-num danger">{{ formatNumber(progress.failCount) }}</div>
            <div class="stat-label">失败</div>
          </div>
        </el-col>
      </el-row>

      <!-- 执行日志 -->
      <div class="log-title">执行日志</div>
      <div ref="logPanelRef" class="log-panel log-height">
        <div v-for="(log, index) in progress.logs" :key="index">{{ log }}</div>
      </div>
    </template>

    <div v-else class="loading-block">
      <el-skeleton :rows="6" animated />
    </div>

    <template #footer>
      <el-button v-if="isRunning" @click="handleClose">后台运行</el-button>
      <el-button type="primary" @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.progress-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.task-name {
  font-weight: 600;
}

.current-table {
  color: #64748b;
  font-size: 13px;
  margin-left: auto;
}

.stat-row {
  margin-bottom: 14px;
}

.stat-item {
  background: #f8fafc;
  border-radius: 6px;
  padding: 10px;
  text-align: center;
}

.stat-num {
  font-size: 20px;
  font-weight: 700;
}

.stat-num.success {
  color: #059669;
}

.stat-num.danger {
  color: #dc2626;
}

.stat-label {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}

.log-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.log-height {
  height: 240px;
}

.loading-block {
  padding: 8px 0;
}
</style>
