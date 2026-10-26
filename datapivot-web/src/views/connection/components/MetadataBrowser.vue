<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { listColumns, listTables } from '@/api/meta'
import DrawerResizeHandle from '@/components/DrawerResizeHandle.vue'
import { useDrawerResize } from '@/composables/useDrawerResize'
import type { ColumnMeta, ConnectionVO, TableMeta } from '@/types/model'

/**
 * 元数据浏览器：左侧表列表（可搜索/分页），点击行加载右侧字段详情
 */
const props = defineProps<{
  modelValue: boolean
  connection: ConnectionVO | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

// 抽屉左缘拖拽调宽：默认 72% 视口宽，最小 640px（双栏布局需要足够宽度）
const { drawerWidth, isDragging, onResizeStart } = useDrawerResize(
  Math.min(1600, Math.round(window.innerWidth * 0.72)),
  640,
)

const tablesLoading = ref(false)
const columnsLoading = ref(false)
const tables = ref<TableMeta[]>([])
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = 15
const selectedTable = ref<TableMeta | null>(null)
const columns = ref<ColumnMeta[]>([])

/** 关键字过滤后的表列表 */
const filteredTables = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return tables.value
  }
  return tables.value.filter(
    (table) =>
      table.tableName.toLowerCase().includes(keyword) ||
      (table.remarks ?? '').toLowerCase().includes(keyword),
  )
})

const pagedTables = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredTables.value.slice(start, start + pageSize)
})

/** 弹窗打开时拉取表列表 */
watch(
  () => props.modelValue,
  async (visible) => {
    if (visible && props.connection) {
      tablesLoading.value = true
      selectedTable.value = null
      columns.value = []
      searchKeyword.value = ''
      currentPage.value = 1
      try {
        tables.value = await listTables(props.connection.id)
      } finally {
        tablesLoading.value = false
      }
    }
  },
)

/** 点击表行 -> 拉取字段详情 */
async function selectTable(table: TableMeta): Promise<void> {
  if (!props.connection || selectedTable.value?.tableName === table.tableName) {
    return
  }
  selectedTable.value = table
  columnsLoading.value = true
  columns.value = []
  try {
    columns.value = await listColumns(props.connection.id, table)
  } finally {
    columnsLoading.value = false
  }
}

/** 类型 + 长度展示 */
function typeText(column: ColumnMeta): string {
  if (column.typeCategory === 'NUMBER' && column.size > 0) {
    return `${column.typeName}(${column.size},${column.digits})`
  }
  if (column.size > 0 && ['STRING', 'BINARY'].includes(column.typeCategory)) {
    return `${column.typeName}(${column.size})`
  }
  return column.typeName
}
</script>

<template>
  <!-- 拖拽期间加类禁用抽屉面板宽度过渡，保证跟手 -->
  <div :class="{ 'dp-drawer-resizing': isDragging }">
    <el-drawer
      :model-value="modelValue"
      :title="`元数据浏览 - ${connection?.connName ?? ''}`"
      :size="drawerWidth + 'px'"
      destroy-on-close
      @close="emit('update:modelValue', false)"
    >
      <DrawerResizeHandle @start="onResizeStart" />

      <div class="browser">
        <!-- 左：表列表 -->
        <div class="browser-left">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索表名/表注释"
            clearable
            :prefix-icon="'Search'"
            style="margin-bottom: 10px"
            @input="currentPage = 1"
          />
          <el-table
            v-loading="tablesLoading"
            :data="pagedTables"
            height="560"
            highlight-current-row
            size="small"
            @row-click="(row: TableMeta) => selectTable(row)"
          >
            <el-table-column prop="tableName" label="表名" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="table-name">{{ row.tableName }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="remarks" label="表注释" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.remarks || '—' }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无表" :image-size="60" />
            </template>
          </el-table>
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="filteredTables.length"
            layout="total, prev, pager, next"
            small
            style="margin-top: 10px; justify-content: flex-end"
          />
        </div>

        <!-- 右：字段详情 -->
        <div class="browser-right">
          <div v-if="!selectedTable" class="empty-tip">
            <el-empty description="点击左侧表名查看字段详情" :image-size="100" />
          </div>
          <template v-else>
            <div class="table-header">
              <span class="table-header-name">{{ selectedTable.displayName }}</span>
              <el-tag v-if="selectedTable.remarks" type="info" effect="plain">
                {{ selectedTable.remarks }}
              </el-tag>
            </div>
            <el-table v-loading="columnsLoading" :data="columns" height="588" size="small" border>
              <el-table-column prop="columnName" label="字段名" min-width="130" show-overflow-tooltip />
              <el-table-column label="数据类型" min-width="130">
                <template #default="{ row }">{{ typeText(row as ColumnMeta) }}</template>
              </el-table-column>
              <el-table-column label="主键" width="64" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.pk" type="warning" size="small">主键</el-tag>
                  <span v-else>—</span>
                </template>
              </el-table-column>
              <el-table-column label="允许为空" width="84" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.nullable ? 'info' : 'danger'" size="small" effect="plain">
                    {{ row.nullable ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="defaultValue" label="默认值" min-width="90" show-overflow-tooltip>
                <template #default="{ row }">{{ row.defaultValue ?? '—' }}</template>
              </el-table-column>
              <el-table-column prop="remarks" label="字段注释" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">{{ row.remarks || '—' }}</template>
              </el-table-column>
            </el-table>
          </template>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.browser {
  display: flex;
  gap: 16px;
  height: 100%;
}

/* 左侧表列表固定比例，拖宽抽屉时右侧字段详情区获得更多空间 */
.browser-left {
  width: 42%;
  flex-shrink: 0;
}

.browser-right {
  flex: 1;
  min-width: 0;
}

.table-name {
  font-weight: 500;
}

.table-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.table-header-name {
  font-size: 15px;
  font-weight: 600;
}

.empty-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
</style>
