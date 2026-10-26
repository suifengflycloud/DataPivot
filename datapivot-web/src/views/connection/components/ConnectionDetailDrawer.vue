<script setup lang="ts">
import { dbTypeLabel } from '@/constants'
import { formatDateTime } from '@/utils/format'
import DrawerResizeHandle from '@/components/DrawerResizeHandle.vue'
import { useDrawerResize } from '@/composables/useDrawerResize'
import type { ConnectionVO } from '@/types/model'

defineProps<{
  modelValue: boolean
  connection: ConnectionVO | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

// 抽屉左缘拖拽调宽（默认 480px，最小 360px）
const { drawerWidth, isDragging, onResizeStart } = useDrawerResize(480, 360)

interface InfoRow {
  label: string
  value: string
}

function infoRows(connection: ConnectionVO | null): InfoRow[] {
  if (!connection) {
    return []
  }
  return [
    { label: '连接名称', value: connection.connName },
    { label: '数据库类型', value: `${dbTypeLabel(connection.dbType)}` },
    { label: '主机地址', value: connection.host },
    { label: '端口', value: String(connection.port) },
    { label: '数据库/SID', value: connection.dbName },
    { label: '用户名', value: connection.username },
    { label: '密码（脱敏）', value: connection.passwordMasked },
    { label: '驱动类名', value: connection.driverClass },
    { label: 'JDBC URL', value: connection.jdbcUrl },
    { label: '扩展参数', value: connection.extraParams || '—' },
    { label: '备注', value: connection.remark || '—' },
    { label: '创建时间', value: formatDateTime(connection.createTime) },
    { label: '更新时间', value: formatDateTime(connection.updateTime) },
  ]
}
</script>

<template>
  <!-- 拖拽期间加类禁用抽屉面板宽度过渡，保证跟手 -->
  <div :class="{ 'dp-drawer-resizing': isDragging }">
    <el-drawer
      :model-value="modelValue"
      title="连接详情"
      :size="drawerWidth + 'px'"
      @close="emit('update:modelValue', false)"
    >
      <DrawerResizeHandle @start="onResizeStart" />

      <el-descriptions v-if="connection" :column="1" border>
        <el-descriptions-item
          v-for="row in infoRows(connection)"
          :key="row.label"
          :label="row.label"
          label-class-name="detail-label"
        >
          <span class="detail-value">{{ row.value }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<style scoped>
/* 字段名列：加宽并禁止换行。
   注意：label 单元格由 el-descriptions 内部渲染、不携带父组件 scoped 属性，
   必须用 :deep() 穿透，否则宽度与 nowrap 均不生效（元素仍会换行） */
:deep(.detail-label) {
  width: 140px;
  white-space: nowrap;
}

/* 字段值正常折行展示（超长 URL 类内容换行便于通读） */
.detail-value {
  word-break: break-all;
}
</style>
