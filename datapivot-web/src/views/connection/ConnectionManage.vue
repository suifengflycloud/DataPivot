<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ConnectionVO } from '@/types/model'
import {
  deleteConnection,
  pageConnections,
  testConnectionById,
} from '@/api/connection'
import { useConnectionStore } from '@/stores/connection'
import ConnectionFormDialog from './components/ConnectionFormDialog.vue'
import ConnectionDetailDrawer from './components/ConnectionDetailDrawer.vue'
import MetadataBrowser from './components/MetadataBrowser.vue'
import { dbTypeLabel } from '@/constants'
import { formatDateTime } from '@/utils/format'

const connectionStore = useConnectionStore()

const loading = ref(false)
const records = ref<ConnectionVO[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '' })

const formVisible = ref(false)
const editTarget = ref<ConnectionVO | null>(null)
const detailVisible = ref(false)
const detailTarget = ref<ConnectionVO | null>(null)
const browserVisible = ref(false)
const browserTarget = ref<ConnectionVO | null>(null)
const testingId = ref<number | null>(null)

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const page = await pageConnections(query.current, query.size, query.keyword)
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

function openCreate(): void {
  editTarget.value = null
  formVisible.value = true
}

function openEdit(row: ConnectionVO): void {
  editTarget.value = row
  formVisible.value = true
}

function openDetail(row: ConnectionVO): void {
  detailTarget.value = row
  detailVisible.value = true
}

/** 「更多」下拉菜单命令分发 */
function onMoreCommand(command: string, row: ConnectionVO): void {
  if (command === 'detail') {
    openDetail(row)
  } else if (command === 'delete') {
    handleDelete(row)
  }
}

function openBrowser(row: ConnectionVO): void {
  browserTarget.value = row
  browserVisible.value = true
}

/** 列表行内测试连接 */
async function handleTest(row: ConnectionVO): Promise<void> {
  testingId.value = row.id
  try {
    const result = await testConnectionById(row.id)
    if (result.success) {
      ElMessage.success(`连接成功（${result.costMs}ms）：${result.dbProduct ?? ''}`)
    } else {
      ElMessage.error(`连接失败：${result.message}`)
    }
  } finally {
    testingId.value = null
  }
}

/** 删除连接（二次确认） */
async function handleDelete(row: ConnectionVO): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除连接「${row.connName}」吗？该操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await deleteConnection(row.id)
  ElMessage.success('删除成功')
  // 若当前页删空则回退一页
  if (records.value.length === 1 && query.current > 1) {
    query.current -= 1
  }
  loadPage()
  connectionStore.fetchAll(true)
}

function onSaved(): void {
  loadPage()
  connectionStore.fetchAll(true)
}

onMounted(() => {
  loadPage()
  connectionStore.fetchAll(true)
})
</script>

<template>
  <div class="page-card">
    <!-- 工具栏 -->
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索连接名称 / IP / 数据库名"
        clearable
        style="width: 280px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <div class="spacer" />
      <el-button type="primary" :icon="'Plus'" @click="openCreate">新增连接</el-button>
    </div>

    <!-- 连接列表 -->
    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="connName" label="连接名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="类型" width="150">
        <template #default="{ row }">
          <el-tag effect="plain">{{ dbTypeLabel(row.dbType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="主机:端口" min-width="150">
        <template #default="{ row }">{{ row.host }}:{{ row.port }}</template>
      </el-table-column>
      <el-table-column prop="dbName" label="数据库" min-width="110" show-overflow-tooltip />
      <el-table-column prop="username" label="用户名" min-width="100" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="165" sortable prop="updateTime">
        <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button
            size="small"
            type="success"
            plain
            :loading="testingId === row.id"
            @click="handleTest(row as ConnectionVO)"
          >
            测试连接
          </el-button>
          <el-button size="small" type="primary" plain @click="openBrowser(row as ConnectionVO)">
            浏览
          </el-button>
          <el-button size="small" type="primary" plain @click="openEdit(row as ConnectionVO)">
            编辑
          </el-button>
          <el-dropdown
            class="more-dropdown"
            trigger="click"
            @command="(command: string) => onMoreCommand(command, row as ConnectionVO)"
          >
            <el-button size="small">
              更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="detail">
                  <el-icon><View /></el-icon>详情
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
        <el-empty description="暂无连接，点击右上角新增" />
      </template>
    </el-table>

    <!-- 分页 -->
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

    <!-- 新增/编辑 -->
    <ConnectionFormDialog v-model="formVisible" :connection="editTarget" @saved="onSaved" />
    <!-- 详情 -->
    <ConnectionDetailDrawer v-model="detailVisible" :connection="detailTarget" />
    <!-- 元数据浏览 -->
    <MetadataBrowser v-model="browserVisible" :connection="browserTarget" />
  </div>
</template>

<style scoped>
/* 下拉触发按钮与相邻按钮保持一致间距（el-dropdown 包裹层不参与按钮自动间距） */
.more-dropdown {
  margin-left: 12px;
}
</style>
