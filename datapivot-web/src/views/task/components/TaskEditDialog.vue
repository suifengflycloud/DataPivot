<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { createTask, updateTask } from '@/api/task'
import { listColumns, listTables } from '@/api/meta'
import { CONFLICT_STRATEGY_OPTIONS } from '@/constants'
import { useConnectionStore } from '@/stores/connection'
import type {
  ColumnMappingItem,
  ColumnMappingRow,
  ColumnMeta,
  ConflictStrategy,
  SyncTaskDTO,
  SyncTaskDetail,
  TableMappingRow,
  TableMeta,
} from '@/types/model'
import ColumnMappingPanel from './ColumnMappingPanel.vue'

/**
 * 同步任务编辑弹窗：基本信息 + 表映射 + 字段映射 + 高级配置
 */
const props = defineProps<{
  modelValue: boolean
  /** 传入则为编辑模式（父组件已加载详情） */
  task: SyncTaskDetail | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'saved'): void
}>()

const connectionStore = useConnectionStore()
const isEdit = computed(() => props.task !== null)

const formRef = ref<FormInstance>()
const saving = ref(false)
const sourceTables = ref<TableMeta[]>([])
const targetTables = ref<TableMeta[]>([])
const sourceTablesLoading = ref(false)
const targetTablesLoading = ref(false)
const columnsLoading = ref(false)
const mappings = ref<TableMappingRow[]>([])
const activeMappingKey = ref<number | null>(null)
let mappingKeySeed = 0

const form = reactive({
  taskName: '',
  sourceConnId: undefined as number | undefined,
  targetConnId: undefined as number | undefined,
  conflictStrategy: 'OVERWRITE' as ConflictStrategy,
  batchSize: 1000,
  fullSync: true,
  remark: '',
})

const rules: FormRules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  sourceConnId: [{ required: true, message: '请选择源数据库连接', trigger: 'change' }],
  targetConnId: [{ required: true, message: '请选择目标数据库连接', trigger: 'change' }],
}

const activeMapping = computed(
  () => mappings.value.find((mapping) => mapping.key === activeMappingKey.value) ?? null,
)

/** 是否增量同步模式 */
const incremental = computed(() => !form.fullSync)

/** 增量字段候选：仅日期时间/数值类型（水位比较要求单调可比较） */
function incColumnOptions(mapping: TableMappingRow): ColumnMeta[] {
  return mapping.sourceColumns.filter(
    (column) => column.typeCategory === 'DATETIME' || column.typeCategory === 'NUMBER',
  )
}

/** 已勾选且目标字段完整的映射数量校验结果 */
function mappingErrorText(): string | null {
  if (mappings.value.length === 0) {
    return '请至少添加一组表映射'
  }
  for (const mapping of mappings.value) {
    const label = `${mapping.sourceTable?.tableName ?? '?'} → ${mapping.targetTable?.tableName ?? '?'}`
    if (!mapping.sourceTable || !mapping.targetTable) {
      return `表映射 [${label}] 未选择完整，请同时选择源表与目标表`
    }
    if (incremental.value && !mapping.incColumn) {
      return `表映射 [${label}] 未选择增量依据字段（如 update_time 或自增 id）`
    }
    const selected = mapping.columns.filter((row) => row.selected)
    if (selected.length === 0) {
      return `表映射 [${label}] 至少勾选一个参与同步的字段`
    }
    if (selected.some((row) => !row.target)) {
      return `表映射 [${label}] 存在已勾选但未选择目标字段的映射`
    }
  }
  return null
}

/** 弹窗打开：初始化（编辑模式回填并拉取两端元数据） */
async function onOpen(): Promise<void> {
  formRef.value?.clearValidate()
  mappings.value = []
  activeMappingKey.value = null
  connectionStore.fetchAll()
  if (props.task) {
    Object.assign(form, {
      taskName: props.task.taskName,
      sourceConnId: props.task.sourceConnId,
      targetConnId: props.task.targetConnId,
      conflictStrategy: props.task.conflictStrategy,
      batchSize: props.task.batchSize,
      fullSync: props.task.fullSync,
      remark: props.task.remark ?? '',
    })
    await Promise.all([loadSourceTables(), loadTargetTables()])
    // 回填表映射并按已保存的字段映射恢复勾选
    for (const mappingVO of props.task.mappings) {
      const row: TableMappingRow = {
        key: ++mappingKeySeed,
        sourceTable:
          sourceTables.value.find((table) => table.tableName === mappingVO.sourceTable) ?? {
            catalog: mappingVO.sourceCatalog,
            schema: mappingVO.sourceSchema,
            tableName: mappingVO.sourceTable,
            displayName: mappingVO.sourceDisplayName,
            remarks: null,
          },
        targetTable:
          targetTables.value.find((table) => table.tableName === mappingVO.targetTable) ?? {
            catalog: mappingVO.targetCatalog,
            schema: mappingVO.targetSchema,
            tableName: mappingVO.targetTable,
            displayName: mappingVO.targetDisplayName,
            remarks: null,
          },
        sourceColumns: [],
        targetColumns: [],
        columns: [],
        incColumn: mappingVO.incColumn ?? null,
      }
      mappings.value.push(row)
      activeMappingKey.value = row.key
      // 回填模式：以已保存的字段映射为准
      loadColumnsForMapping(row, mappingVO.columns)
    }
    if (mappings.value.length > 0) {
      activeMappingKey.value = mappings.value[0]?.key ?? null
    }
  } else {
    Object.assign(form, {
      taskName: '',
      sourceConnId: undefined,
      targetConnId: undefined,
      conflictStrategy: 'OVERWRITE',
      batchSize: 1000,
      fullSync: true,
      remark: '',
    })
    sourceTables.value = []
    targetTables.value = []
  }
}

async function loadSourceTables(): Promise<void> {
  if (!form.sourceConnId) {
    return
  }
  sourceTablesLoading.value = true
  try {
    sourceTables.value = await listTables(form.sourceConnId)
  } finally {
    sourceTablesLoading.value = false
  }
}

async function loadTargetTables(): Promise<void> {
  if (!form.targetConnId) {
    return
  }
  targetTablesLoading.value = true
  try {
    targetTables.value = await listTables(form.targetConnId)
  } finally {
    targetTablesLoading.value = false
  }
}

/** 切换源连接：重新拉表并清空已有表映射（原映射已失效） */
async function onSourceConnChange(): Promise<void> {
  mappings.value = []
  activeMappingKey.value = null
  await loadSourceTables()
}

/** 切换目标连接 */
async function onTargetConnChange(): Promise<void> {
  mappings.value = []
  activeMappingKey.value = null
  await loadTargetTables()
}

/** 新增一组表映射 */
function addMapping(): void {
  if (!form.sourceConnId || !form.targetConnId) {
    ElMessage.warning('请先选择源数据库连接与目标数据库连接')
    return
  }
  const row: TableMappingRow = {
    key: ++mappingKeySeed,
    sourceTable: null,
    targetTable: null,
    sourceColumns: [],
    targetColumns: [],
    columns: [],
    incColumn: null,
  }
  mappings.value.push(row)
  activeMappingKey.value = row.key
}

function removeMapping(key: number): void {
  const index = mappings.value.findIndex((mapping) => mapping.key === key)
  if (index >= 0) {
    mappings.value.splice(index, 1)
    if (activeMappingKey.value === key) {
      activeMappingKey.value = mappings.value[0]?.key ?? null
    }
  }
}

/** 源表/目标表选择变化：拉取两端字段并初始化映射行（换表后原增量字段失效） */
function onMappingTableChange(mapping: TableMappingRow): void {
  mapping.incColumn = null
  if (mapping.sourceTable && mapping.targetTable) {
    loadColumnsForMapping(mapping)
  } else {
    mapping.columns = []
  }
}

/**
 * 拉取某组映射的两端字段列表并初始化勾选
 * @param savedColumns 编辑模式传入已保存的字段映射，优先按其恢复
 */
async function loadColumnsForMapping(mapping: TableMappingRow, savedColumns?: ColumnMappingItem[]) {
  if (!form.sourceConnId || !form.targetConnId || !mapping.sourceTable || !mapping.targetTable) {
    return
  }
  columnsLoading.value = true
  try {
    const [sourceColumns, targetColumns] = await Promise.all([
      listColumns(form.sourceConnId, mapping.sourceTable),
      listColumns(form.targetConnId, mapping.targetTable),
    ])
    mapping.sourceColumns = sourceColumns
    mapping.targetColumns = targetColumns
    // 初始化映射行：默认同名自动匹配
    mapping.columns = sourceColumns.map<ColumnMappingRow>((column) => {
      const matched = targetColumns.find(
        (target) => target.columnName.toLowerCase() === column.columnName.toLowerCase(),
      )
      return { source: column, selected: matched !== undefined, target: matched?.columnName ?? null }
    })
    // 编辑模式：以保存的映射为准恢复勾选
    if (savedColumns && savedColumns.length > 0) {
      mapping.columns.forEach((row) => {
        const saved = savedColumns.find(
          (item) => item.sourceColumn.toLowerCase() === row.source.columnName.toLowerCase(),
        )
        if (saved) {
          row.selected = true
          row.target = saved.targetColumn
        } else {
          row.selected = false
          row.target = null
        }
      })
    }
  } finally {
    columnsLoading.value = false
  }
}

/** 保存任务 */
async function handleSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  const errorText = mappingErrorText()
  if (errorText) {
    ElMessage.warning(errorText)
    return
  }
  const dto: SyncTaskDTO = {
    taskName: form.taskName.trim(),
    sourceConnId: form.sourceConnId as number,
    targetConnId: form.targetConnId as number,
    conflictStrategy: form.conflictStrategy,
    batchSize: form.batchSize,
    fullSync: form.fullSync,
    remark: form.remark,
    mappings: mappings.value.map((mapping) => ({
      sourceCatalog: mapping.sourceTable?.catalog ?? null,
      sourceSchema: mapping.sourceTable?.schema ?? null,
      sourceTable: mapping.sourceTable?.tableName ?? '',
      targetCatalog: mapping.targetTable?.catalog ?? null,
      targetSchema: mapping.targetTable?.schema ?? null,
      targetTable: mapping.targetTable?.tableName ?? '',
      incColumn: incremental.value ? mapping.incColumn : null,
      columns: mapping.columns
        .filter((row) => row.selected && row.target)
        .map((row) => ({
          sourceColumn: row.source.columnName,
          targetColumn: row.target as string,
        })),
    })),
  }
  saving.value = true
  try {
    if (isEdit.value && props.task) {
      await updateTask(props.task.id, dto)
      ElMessage.success('任务更新成功')
    } else {
      await createTask(dto)
      ElMessage.success('任务创建成功')
    }
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}

function handleClose(): void {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑同步任务' : '新建同步任务'"
    width="1100px"
    top="4vh"
    destroy-on-close
    @open="onOpen"
    @close="handleClose"
  >
    <!-- 一、基本信息 -->
    <div class="section-title">一、基本信息</div>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="任务名称" prop="taskName">
            <el-input v-model="form.taskName" placeholder="如：订单库到数仓同步" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="源数据库" prop="sourceConnId">
            <el-select
              v-model="form.sourceConnId"
              placeholder="选择源库连接 A"
              style="width: 100%"
              @change="onSourceConnChange"
            >
              <el-option
                v-for="conn in connectionStore.connections"
                :key="conn.id"
                :value="conn.id"
                :label="`${conn.connName}（${conn.dbTypeLabel}）`"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="目标数据库" prop="targetConnId">
            <el-select
              v-model="form.targetConnId"
              placeholder="选择目标库连接 B"
              style="width: 100%"
              @change="onTargetConnChange"
            >
              <el-option
                v-for="conn in connectionStore.connections"
                :key="conn.id"
                :value="conn.id"
                :label="`${conn.connName}（${conn.dbTypeLabel}）`"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 二、表映射 -->
    <div class="section-title">
      二、表映射
      <span class="section-tip">从 A 库选源表，绑定 B 库目标表，可配置多组（A1→B1、A2→B2...）</span>
    </div>
    <el-table :data="mappings" size="small" border>
      <el-table-column label="源表（A 库）" min-width="220">
        <template #default="{ row }">
          <el-select
            v-model="row.sourceTable"
            placeholder="选择源表"
            filterable
            size="small"
            style="width: 100%"
            value-key="tableName"
            :loading="sourceTablesLoading"
            @change="onMappingTableChange(row as TableMappingRow)"
          >
            <el-option
              v-for="table in sourceTables"
              :key="table.tableName"
              :value="table"
              :label="table.displayName"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column width="40" align="center">
        <template #default><span class="arrow">→</span></template>
      </el-table-column>
      <el-table-column label="目标表（B 库）" min-width="220">
        <template #default="{ row }">
          <el-select
            v-model="row.targetTable"
            placeholder="选择目标表"
            filterable
            size="small"
            style="width: 100%"
            value-key="tableName"
            :loading="targetTablesLoading"
            @change="onMappingTableChange(row as TableMappingRow)"
          >
            <el-option
              v-for="table in targetTables"
              :key="table.tableName"
              :value="table"
              :label="table.displayName"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="字段数" width="76" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.columns.length" size="small" type="info" effect="plain">
            {{ (row as TableMappingRow).columns.filter((column) => column.selected).length }}/{{
              row.columns.length
            }}
          </el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column min-width="150">
        <template #header>
          增量字段
          <span v-if="!incremental" class="inc-header-tip">（全量模式无需）</span>
        </template>
        <template #default="{ row }">
          <el-select
            v-if="incremental"
            v-model="(row as TableMappingRow).incColumn"
            placeholder="选择增量依据字段"
            filterable
            size="small"
            style="width: 100%"
          >
            <el-option
              v-for="column in incColumnOptions(row as TableMappingRow)"
              :key="column.columnName"
              :value="column.columnName"
              :label="`${column.columnName}（${column.typeName}）`"
            />
          </el-select>
          <span v-else class="inc-na">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" align="center">
        <template #default="{ row }">
          <el-button
            size="small"
            type="primary"
            plain
            @click="activeMappingKey = (row as TableMappingRow).key"
          >
            字段映射
          </el-button>
          <el-button size="small" type="danger" plain @click="removeMapping((row as TableMappingRow).key)">
            删除
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无表映射，点击下方按钮添加" :image-size="60" />
      </template>
    </el-table>
    <el-button
      type="primary"
      plain
      :icon="'Plus'"
      style="margin-top: 10px; width: 100%"
      @click="addMapping"
    >
      添加表映射
    </el-button>

    <!-- 三、字段映射 -->
    <div class="section-title">
      三、字段映射
      <span class="section-tip">
        {{
          activeMapping
            ? `${activeMapping.sourceTable?.displayName ?? ''} → ${activeMapping.targetTable?.displayName ?? ''}`
            : '点击表映射行的「字段映射」按钮配置'
        }}
      </span>
    </div>
    <ColumnMappingPanel
      v-if="activeMapping"
      v-model:rows="activeMapping.columns"
      :source-columns="activeMapping.sourceColumns"
      :target-columns="activeMapping.targetColumns"
      :loading="columnsLoading"
    />
    <el-empty v-else description="选择一组表映射后配置字段映射" :image-size="70" />

    <!-- 四、高级配置 -->
    <div class="section-title">四、同步高级配置</div>
    <el-form label-width="110px">
      <el-form-item label="主键冲突策略">
        <el-radio-group v-model="form.conflictStrategy">
          <el-radio-button
            v-for="option in CONFLICT_STRATEGY_OPTIONS"
            :key="option.code"
            :value="option.code"
          >
            {{ option.label }}
          </el-radio-button>
        </el-radio-group>
        <div class="strategy-desc">
          {{ CONFLICT_STRATEGY_OPTIONS.find((o) => o.code === form.conflictStrategy)?.description }}
        </div>
      </el-form-item>
      <el-form-item label="同步批次大小">
        <el-input-number v-model="form.batchSize" :min="1" :max="100000" :step="500" />
        <span class="inline-tip">条 / 批</span>
      </el-form-item>
      <el-form-item label="同步方式">
        <el-radio-group v-model="form.fullSync">
          <el-radio-button :value="true">全量同步</el-radio-button>
          <el-radio-button :value="false">增量同步</el-radio-button>
        </el-radio-group>
        <div class="strategy-desc">
          {{
            form.fullSync
              ? '每次执行清空式比对全表数据，按冲突策略写入目标表，适合首次迁移或定期对账。'
              : '按每组映射选择的增量字段（如 update_time / 自增 id）拉取「上次水位之后」的新增与变更记录写入目标表；首次执行无水位自动全量拉取，请确保增量字段单调递增且不为空。'
          }}
        </div>
      </el-form-item>
      <el-form-item label="备注">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="2"
          maxlength="300"
          show-word-limit
          style="max-width: 520px"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存任务</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.section-title {
  font-size: 14px;
  font-weight: 600;
  margin: 18px 0 10px;
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.section-title:first-child {
  margin-top: 0;
}

.section-tip {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 400;
}

.arrow {
  color: #94a3b8;
  font-weight: 600;
}

.strategy-desc {
  font-size: 12px;
  color: #94a3b8;
  /* el-form-item 内容区为 flex 布局，占满一行才能换行到按钮下方，避免与单选按钮挤在同一行 */
  width: 100%;
  margin-top: 6px;
  line-height: 1.5;
}

.inc-header-tip {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 400;
}

.inc-na {
  color: #cbd5e1;
}

.inline-tip {
  margin-left: 8px;
  color: #94a3b8;
  font-size: 13px;
}
</style>
