<script setup lang="ts">
withDefaults(defineProps<{ title?: string }>(), { title: '按住拖拽调整宽度' })

const emit = defineEmits<{
  (e: 'start', event: MouseEvent): void
}>()
</script>

<template>
  <!-- 抽屉左缘拖拽手柄：绝对定位于抽屉整条左边缘，父容器需为 el-drawer 的插槽内容 -->
  <div class="drawer-resize-handle" :title="title" @mousedown="emit('start', $event)"></div>
</template>

<style scoped>
.drawer-resize-handle {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 5px;
  cursor: col-resize;
  z-index: 10;
  background: transparent;
  transition: background-color 0.2s;
}

.drawer-resize-handle:hover {
  background-color: var(--el-color-primary-light-7, #c6e2ff);
}
</style>
