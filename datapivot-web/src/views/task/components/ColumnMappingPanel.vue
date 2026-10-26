<script setup lang="ts">
import { computed } from 'vue'
import type { ColumnMeta, ColumnMappingRow } from '@/types/model'
import { CATEGORY_LABELS } from '@/constants'

/**
 * 字段映射双栏面板：左列源字段（可勾选），右列目标字段（下拉选择）
 * 通过 v-model:rows 与父组件双向绑定。
 */
const props = defineProps<{
  sourceColumns: ColumnMeta[]
  targetColumns: ColumnMeta[]
  loading: boolean
}>()

const rows = defineModel<ColumnMappingRow[]>('rows', { required: true })

/** 目标字段下拉选项：名称 + 类型 */
const targetOptions = computed(() =>
  props.targetColumns.map((column) => ({
    value: column.columnName,
    label: `${column.columnName} (${column.typeName})`,
    column,
  })),
)

/** 勾选行数 */
const selectedCount = computed(() => rows.value.filter((row) => row.selected).length)

function typeText(column: ColumnMeta): string {
  if (column.typeCategory === 'NUMBER' && column.size > 0) {
    return `${column.typeName}(${column.size},${column.digits})`
  }
  if (column.size > 0 && ['STRING', 'BINARY'].includes(column.typeCategory)) {
    return `${column.typeName}(${column.size})`
  }
  return column.typeName
}

/** 类型大类是否需要自动转换 */
function categoryOf(column: ColumnMeta): string {
  return CATEGORY_LABELS[column.typeCategory] ?? column.typeCategory
}

function needConvert(row: ColumnMappingRow): boolean {
  if (!row.target) {
    return false
  }
  const target = props.targetColumns.find((column) => column.columnName === row.target)
  return target !== undefined && target.typeCategory !== row.source.typeCategory
}

function toggleAll(selected: boolean): void {
  rows.value.forEach((row) => {
    row.selected = selected
  })
}

/** 同名自动匹配（保留已勾选状态） */
function autoMatch(): void {
  const targetNames = new Set(props.targetColumns.map((column) => column.columnName.toLowerCase()))
  rows.value.forEach((row) => {
    const matched = props.targetColumns.find(
      (column) => column.columnName.toLowerCase() === row.source.columnName.toLowerCase(),
    )
    if (matched) {
      row.target = matched.columnName
      row.selected = true
    } else if (!targetNames.has(String(row.target ?? '').toLowerCase())) {
      row.target = row.target ?? null
    }
  })
}

/** 目标字段变更时自动勾选 */
function onTargetChange(row: ColumnMappingRow): void {
  if (row.target) {
    row.selected = true
  }
}
</script>

<template>
  <div v-loading="loading" class="mapping-panel">
    <div class="mapping-toolbar">
      <span class="mapping-count">已选 {{ selectedCount }} / {{ rows.length }} 个字段参与同步</span>
      <div class="spacer" />
      <el-button size="small" @click="toggleAll(true)">全选</el-button>
      <el-button size="small" @click="toggleAll(false)">取消全选</el-button>
      <el-button size="small" type="primary" plain @click="autoMatch">同名自动匹配</el-button>
    </div>

    <el-table :data="rows" size="small" border max-height="380">
      <el-table-column label="源字段（勾选参与同步）" min-width="200">
        <template #default="{ row }">
          <el-checkbox v-model="row.selected">
            <span class="column-name">{{ row.source.columnName }}</span>
            <el-tag size="small" type="info" effect="plain" class="type-tag">
              {{ typeText(row.source) }}
            </el-tag>
          </el-checkbox>
        </template>
      </el-table-column>
      <el-table-column label="源类型大类" width="100" align="center">
        <template #default="{ row }">{{ categoryOf(row.source) }}</template>
      </el-table-column>
      <el-table-column label="目标字段（下拉选择映射）" min-width="260">
        <template #default="{ row }">
          <el-select
            v-model="row.target"
            placeholder="选择目标字段"
            clearable
            filterable
            size="small"
            style="width: 100%"
            @change="onTargetChange(row as ColumnMappingRow)"
          >
            <el-option
              v-for="option in targetOptions"
              :key="option.value"
              :value="option.value"
              :label="option.label"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="转换提示" width="130" align="center">
        <template #default="{ row }">
          <el-tag v-if="needConvert(row as ColumnMappingRow)" type="warning" size="small" effect="plain">
            类型自动转换
          </el-tag>
          <el-tag v-else-if="row.target" type="success" size="small" effect="plain"> 类型一致 </el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="请先选择源表与目标表" :image-size="60" />
      </template>
    </el-table>
  </div>
</template>

<style scoped>
.mapping-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.mapping-count {
  color: #2563eb;
  font-weight: 600;
}

.spacer {
  flex: 1;
}

.column-name {
  font-weight: 500;
  margin-right: 6px;
}

.type-tag {
  margin-left: 2px;
}
</style>
