<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadDdlScript, executeCompare } from '@/api/compare'
import { dbTypeLabel, diffTagType } from '@/constants'
import { useConnectionStore } from '@/stores/connection'
import { formatNumber } from '@/utils/format'
import type { CompareResult, TableDiff, TableDiffType } from '@/types/model'
import SqlCode from '@/components/SqlCode.vue'

const connectionStore = useConnectionStore()
const baseConnId = ref<number | undefined>(undefined)
const targetConnId = ref<number | undefined>(undefined)
const comparing = ref(false)
const downloading = ref(false)
const result = ref<CompareResult | null>(null)
/** 差异过滤：全部 / 新增 / 删除 / 修改 */
const filterType = ref<'ALL' | 'ADD' | 'DELETE' | 'MODIFY'>('ALL')

const baseConn = computed(() =>
  connectionStore.connections.find((conn) => conn.id === baseConnId.value) ?? null,
)
const targetConn = computed(() =>
  connectionStore.connections.find((conn) => conn.id === targetConnId.value) ?? null,
)

/** 两库类型不一致提示 */
const typeMismatch = computed(
  () => baseConn.value !== null && targetConn.value !== null && baseConn.value.dbType !== targetConn.value.dbType,
)

/** 过滤后的表差异 */
const filteredDiffs = computed<TableDiff[]>(() => {
  if (!result.value) {
    return []
  }
  if (filterType.value === 'ALL') {
    return result.value.tableDiffs
  }
  return result.value.tableDiffs.filter((diff) => diff.diffType === `TABLE_${filterType.value}`)
})

/** 差异行数统计（当前过滤） */
const filteredItemCount = computed(() =>
  filteredDiffs.value.reduce(
    (count, diff) =>
      count + (diff.diffType === 'TABLE_MODIFY' ? diff.columns.length : 1),
    0,
  ),
)

async function handleCompare(): Promise<void> {
  if (!baseConnId.value || !targetConnId.value) {
    ElMessage.warning('请选择基准库与对比库连接')
    return
  }
  if (baseConnId.value === targetConnId.value) {
    ElMessage.warning('基准库与对比库不能为同一连接')
    return
  }
  comparing.value = true
  result.value = null
  try {
    result.value = await executeCompare(baseConnId.value, targetConnId.value)
    if (result.value.tableDiffs.length === 0) {
      ElMessage.success('两个库的表结构完全一致')
    }
  } finally {
    comparing.value = false
  }
}

/** 复制 DDL 脚本 */
async function handleCopy(): Promise<void> {
  if (!result.value) {
    return
  }
  try {
    await navigator.clipboard.writeText(result.value.ddlScript)
    ElMessage.success('DDL 脚本已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

/** 下载 .sql 文件（文件名由后端生成并校验） */
async function handleDownload(): Promise<void> {
  if (!baseConnId.value || !targetConnId.value) {
    return
  }
  downloading.value = true
  try {
    const blob = await downloadDdlScript(baseConnId.value, targetConnId.value)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `DataPivot升级脚本_${Date.now()}.sql`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('DDL 脚本下载成功')
  } finally {
    downloading.value = false
  }
}

function tableTagType(diffType: TableDiffType): 'success' | 'danger' | 'warning' {
  return diffTagType(diffType)
}

onMounted(() => {
  connectionStore.fetchAll()
})
</script>

<template>
  <div>
    <!-- 选择连接 -->
    <div class="page-card">
      <div class="toolbar">
        <div class="selector">
          <span class="selector-label">基准库 A</span>
          <el-select
            v-model="baseConnId"
            placeholder="选择基准库（结构以它为准）"
            filterable
            style="width: 300px"
          >
            <el-option
              v-for="conn in connectionStore.connections"
              :key="conn.id"
              :value="conn.id"
              :label="`${conn.connName}（${conn.dbTypeLabel}）`"
            />
          </el-select>
        </div>
        <el-icon class="compare-icon"><Right /></el-icon>
        <div class="selector">
          <span class="selector-label">对比库 B</span>
          <el-select
            v-model="targetConnId"
            placeholder="选择对比库（差异向它对齐）"
            filterable
            style="width: 300px"
          >
            <el-option
              v-for="conn in connectionStore.connections"
              :key="conn.id"
              :value="conn.id"
              :label="`${conn.connName}（${conn.dbTypeLabel}）`"
            />
          </el-select>
        </div>
        <el-button
          type="primary"
          :icon="'Search'"
          :loading="comparing"
          style="margin-left: auto"
          @click="handleCompare"
        >
          开始对比
        </el-button>
      </div>
      <el-alert
        v-if="typeMismatch"
        title="两个连接的数据库类型不一致，DDL 脚本将按对比库（B）的方言生成"
        type="warning"
        show-icon
        :closable="false"
      />
    </div>

    <template v-if="result">
      <!-- 汇总卡片 -->
      <el-row :gutter="16" class="summary-row">
        <el-col :span="6">
          <div class="summary-card add">
            <div class="summary-num">{{ formatNumber(result.summary.addedTables) }}</div>
            <div class="summary-label">新增表（A有B无）</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="summary-card del">
            <div class="summary-num">{{ formatNumber(result.summary.deletedTables) }}</div>
            <div class="summary-label">删除表（B有A无）</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="summary-card modify">
            <div class="summary-num">{{ formatNumber(result.summary.changedTables) }}</div>
            <div class="summary-label">有变更的表</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="summary-card total">
            <div class="summary-num">{{ formatNumber(result.summary.totalDiffItems) }}</div>
            <div class="summary-label">差异明细总数</div>
          </div>
        </el-col>
      </el-row>

      <!-- 差异明细 -->
      <div class="page-card">
        <div class="toolbar">
          <span class="diff-title">差异明细</span>
          <el-radio-group v-model="filterType" size="small">
            <el-radio-button value="ALL">全部</el-radio-button>
            <el-radio-button value="ADD">新增</el-radio-button>
            <el-radio-button value="DELETE">删除</el-radio-button>
            <el-radio-button value="MODIFY">修改</el-radio-button>
          </el-radio-group>
          <span class="diff-count">共 {{ filteredItemCount }} 条差异</span>
        </div>

        <el-empty
          v-if="result.tableDiffs.length === 0"
          description="两个库的表结构完全一致，无差异"
        />
        <template v-else>
          <el-empty v-if="filteredDiffs.length === 0" description="当前过滤条件下无差异" />
          <div v-else class="diff-list">
            <div v-for="diff in filteredDiffs" :key="diff.tableName" class="diff-group">
              <div class="diff-group-header">
                <span class="diff-table-name">{{ diff.displayName }}</span>
                <el-tag :type="tableTagType(diff.diffType)" size="small" effect="dark">
                  {{ diff.diffTypeLabel }}
                </el-tag>
                <span v-if="diff.diffType === 'TABLE_MODIFY'" class="diff-column-count">
                  {{ diff.columns.length }} 项字段变更
                </span>
                <span v-else-if="diff.diffType === 'TABLE_ADD'" class="diff-column-count">
                  {{ diff.columns.length }} 个字段（整表新建）
                </span>
              </div>
              <div v-if="diff.diffType === 'TABLE_MODIFY'" class="diff-body">
                <div v-for="column in diff.columns" :key="column.columnName + column.diffType" class="diff-item">
                  <el-tag :type="diffTagType(column.diffType)" size="small" effect="plain">
                    {{ column.diffTypeLabel }}
                  </el-tag>
                  <span class="diff-desc">{{ column.description }}</span>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- DDL 脚本 -->
      <div class="page-card">
        <div class="toolbar">
          <span class="diff-title">
            DDL 升级脚本（{{ dbTypeLabel(result.targetDbType) }} 方言 · 在对比库 B 执行）
          </span>
          <div class="spacer" />
          <el-button :icon="'CopyDocument'" @click="handleCopy">复制</el-button>
          <el-button type="primary" :icon="'Download'" :loading="downloading" @click="handleDownload">
            下载 .sql
          </el-button>
        </div>
        <div class="ddl-code">
          <SqlCode :code="result.ddlScript" />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.selector {
  display: flex;
  align-items: center;
  gap: 8px;
}

.selector-label {
  font-size: 13px;
  color: #64748b;
  white-space: nowrap;
}

.compare-icon {
  color: #94a3b8;
  font-size: 18px;
}

.summary-row {
  margin-bottom: 16px;
}

.summary-card {
  border-radius: 8px;
  padding: 16px;
  color: #ffffff;
}

.summary-card.add {
  background: linear-gradient(135deg, #10b981, #059669);
}

.summary-card.del {
  background: linear-gradient(135deg, #f87171, #dc2626);
}

.summary-card.modify {
  background: linear-gradient(135deg, #fbbf24, #d97706);
}

.summary-card.total {
  background: linear-gradient(135deg, #60a5fa, #2563eb);
}

.summary-num {
  font-size: 26px;
  font-weight: 700;
}

.summary-label {
  font-size: 13px;
  opacity: 0.9;
  margin-top: 2px;
}

.diff-title {
  font-weight: 600;
}

.diff-count {
  color: #94a3b8;
  font-size: 13px;
  margin-left: auto;
}

.diff-list {
  max-height: 420px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 4px;
}

.diff-group {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}

.diff-group-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  border-radius: 6px 6px 0 0;
}

.diff-table-name {
  font-weight: 600;
}

.diff-column-count {
  color: #94a3b8;
  font-size: 12px;
}

.diff-body {
  padding: 6px 12px;
}

.diff-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 0;
  border-bottom: 1px dashed #f1f5f9;
}

.diff-item:last-child {
  border-bottom: none;
}

.diff-desc {
  font-size: 13px;
  color: #334155;
}

.ddl-code {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 12px;
  background: #f8fafc;
}

.ddl-code :deep(.sql-code) {
  max-height: 480px;
}
</style>
