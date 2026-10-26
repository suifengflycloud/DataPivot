<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createSchedule,
  deleteSchedule,
  disableSchedule,
  enableSchedule,
  pageSchedules,
  triggerSchedule,
  updateSchedule,
} from '@/api/schedule'
import { listTasks } from '@/api/task'
import type { ScheduleForm, ScheduleVO, SyncTaskVO } from '@/types/model'
import { formatDateTime } from '@/utils/format'

/** Cron 快捷选项（XXL-Job 6 位格式：秒 分 时 日 月 周） */
const CRON_PRESETS = [
  { label: '每分钟', value: '0 * * * * ?' },
  { label: '每小时', value: '0 0 * * * ?' },
  { label: '每天 0 点', value: '0 0 0 * * ?' },
  { label: '每周一 0 点', value: '0 0 0 ? * MON' },
  { label: '每月 1 号 0 点', value: '0 0 0 1 * ?' },
] as const

const CUSTOM = '__CUSTOM__'

const loading = ref(false)
const records = ref<ScheduleVO[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10 })
const tasks = ref<SyncTaskVO[]>([])

const editVisible = ref(false)
const editId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<ScheduleForm>({
  taskId: undefined,
  cronExpr: '0 * * * * ?',
  jobDesc: '',
  owner: '',
})

/** 执行周期单选框当前选中项，CUSTOM 表示自定义；独立于 cronExpr 记录，否则选自定义时会被反推回预设项 */
const cronMode = ref<string>(CRON_PRESETS[0].value)

function onPresetChange(value: string | number | boolean | undefined): void {
  if (typeof value === 'string') {
    cronMode.value = value
    if (value !== CUSTOM) {
      form.cronExpr = value
    }
  }
}

const rules: FormRules = {
  taskId: [{ required: true, message: '请选择同步任务', trigger: 'change' }],
  cronExpr: [{ required: true, message: '请输入 Cron 表达式', trigger: 'blur' }],
  jobDesc: [{ required: true, message: '请输入任务描述', trigger: 'blur' }],
  owner: [{ required: true, message: '请输入负责人', trigger: 'blur' }],
}

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const page = await pageSchedules(query.current, query.size)
    records.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editId.value = null
  Object.assign(form, { taskId: undefined, cronExpr: '0 * * * * ?', jobDesc: '', owner: '' })
  cronMode.value = CRON_PRESETS[0].value
  editVisible.value = true
}

function openEdit(row: ScheduleVO): void {
  editId.value = row.id
  Object.assign(form, {
    taskId: row.taskId,
    cronExpr: row.cronExpr,
    jobDesc: row.jobDesc,
    owner: row.owner,
  })
  cronMode.value = CRON_PRESETS.find((preset) => preset.value === row.cronExpr)?.value ?? CUSTOM
  editVisible.value = true
}

async function handleSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    if (editId.value !== null) {
      await updateSchedule(editId.value, { ...form })
      ElMessage.success('定时任务更新成功')
    } else {
      await createSchedule({ ...form })
      ElMessage.success('定时任务创建成功，已在调度中心注册并启动')
    }
    editVisible.value = false
    loadPage()
  } finally {
    saving.value = false
  }
}

async function handleToggle(row: ScheduleVO): Promise<void> {
  if (row.status === 'ENABLED') {
    await disableSchedule(row.id)
    ElMessage.success('已暂停调度')
  } else {
    await enableSchedule(row.id)
    ElMessage.success('已恢复调度')
  }
  loadPage()
}

async function handleTrigger(row: ScheduleVO): Promise<void> {
  await triggerSchedule(row.id)
  ElMessage.success('已触发执行，稍后可在「任务执行记录」查看结果')
}

async function handleDelete(row: ScheduleVO): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除定时任务「${row.jobDesc}」吗？调度中心的对应任务也将一并移除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await deleteSchedule(row.id)
  ElMessage.success('定时任务删除成功')
  if (records.value.length === 1 && query.current > 1) {
    query.current -= 1
  }
  loadPage()
}

/** 「更多」下拉菜单命令分发 */
function onMoreCommand(command: string, row: ScheduleVO): void {
  if (command === 'edit') {
    openEdit(row)
  } else if (command === 'delete') {
    handleDelete(row)
  }
}

function lastResultTag(status: string | null): string {
  if (status === 'SUCCESS') {
    return '成功'
  }
  if (status === 'FAILED') {
    return '失败'
  }
  return '—'
}

function lastResultTagType(status: string | null): 'success' | 'danger' | 'info' {
  if (status === 'SUCCESS') {
    return 'success'
  }
  return status === 'FAILED' ? 'danger' : 'info'
}

onMounted(async () => {
  loadPage()
  tasks.value = await listTasks()
})
</script>

<template>
  <div class="page-card">
    <el-alert
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 14px"
      title="定时任务由 XXL-Job 调度中心统一调度：可在调度中心控制台查看任务状态、执行日志，也可手动触发/暂停。"
    />

    <div class="toolbar">
      <div class="spacer" />
      <el-button type="primary" :icon="'Plus'" @click="openCreate">新建定时任务</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="jobDesc" label="任务描述" min-width="160" show-overflow-tooltip />
      <el-table-column prop="taskName" label="同步任务" min-width="130" show-overflow-tooltip />
      <el-table-column label="Cron 表达式" width="130">
        <template #default="{ row }">
          <code class="cron-text">{{ row.cronExpr }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="owner" label="负责人" width="90" align="center" />
      <el-table-column label="下次执行时间" width="165">
        <template #default="{ row }">{{ formatDateTime(row.nextTriggerTime) }}</template>
      </el-table-column>
      <el-table-column label="最近执行结果" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="lastResultTagType(row.lastStatus)" effect="plain">
            {{ lastResultTag(row.lastStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'" effect="dark">
            {{ row.status === 'ENABLED' ? '运行中' : '已暂停' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="success" plain @click="handleTrigger(row as ScheduleVO)">
            立即触发
          </el-button>
          <el-button
            size="small"
            :type="row.status === 'ENABLED' ? 'warning' : 'success'"
            plain
            @click="handleToggle(row as ScheduleVO)"
          >
            {{ row.status === 'ENABLED' ? '暂停' : '启动' }}
          </el-button>
          <el-dropdown
            class="more-dropdown"
            trigger="click"
            @command="(command: string) => onMoreCommand(command, row as ScheduleVO)"
          >
            <el-button size="small">
              更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">
                  <el-icon><EditPen /></el-icon>编辑
                </el-dropdown-item>
                <el-dropdown-item command="delete" divided class="dp-danger-item">
                  <el-icon><Delete /></el-icon>删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无定时任务，点击右上角新建" />
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
      @size-change="loadPage"
    />

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="editVisible"
      :title="editId === null ? '新建定时任务' : '编辑定时任务'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="同步任务" prop="taskId">
          <el-select v-model="form.taskId" placeholder="选择要定时执行的同步任务" filterable style="width: 100%">
            <el-option v-for="task in tasks" :key="task.id" :value="task.id" :label="task.taskName" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行周期" prop="cronExpr">
          <el-radio-group :model-value="cronMode" @change="onPresetChange">
            <el-radio-button v-for="preset in CRON_PRESETS" :key="preset.value" :value="preset.value">
              {{ preset.label }}
            </el-radio-button>
            <el-radio-button :value="CUSTOM">自定义</el-radio-button>
          </el-radio-group>
          <el-input
            v-if="cronMode === CUSTOM"
            v-model="form.cronExpr"
            placeholder="6 位 Cron：秒 分 时 日 月 周，如 0 0/30 * * * ?"
            style="margin-top: 8px"
          />
          <div v-else class="cron-current">当前表达式：<code>{{ form.cronExpr }}</code></div>
        </el-form-item>
        <el-form-item label="任务描述" prop="jobDesc">
          <el-input v-model="form.jobDesc" maxlength="200" placeholder="定时任务用途说明" />
        </el-form-item>
        <el-form-item label="负责人" prop="owner">
          <el-input v-model="form.owner" maxlength="50" placeholder="负责人姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 下拉触发按钮与相邻按钮保持一致间距（el-dropdown 包裹层不参与按钮自动间距） */
.more-dropdown {
  margin-left: 12px;
}

.cron-text {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.cron-current {
  margin-top: 6px;
  font-size: 13px;
  color: #64748b;
}
</style>
