import { onBeforeUnmount, ref } from 'vue'

/**
 * 抽屉左缘拖拽调宽 composable
 *
 * <p>配合 {@link ./components/DrawerResizeHandle.vue} 使用：抽屉右锚定，向左拖变宽、向右拖收窄。
 * 返回的 isDragging 用于给抽屉外层容器加 .dp-drawer-resizing 类（全局样式中禁用面板过渡，保证拖拽跟手）。</p>
 *
 * @param defaultWidth 初始宽度（px）
 * @param minWidth     最小宽度（px）
 */
export function useDrawerResize(defaultWidth: number, minWidth = 360) {
  const drawerWidth = ref(defaultWidth)
  const isDragging = ref(false)
  let dragStartX = 0
  let dragStartWidth = 0

  /** 按下左缘手柄开始拖拽 */
  function onResizeStart(event: MouseEvent): void {
    isDragging.value = true
    dragStartX = event.clientX
    dragStartWidth = drawerWidth.value
    document.addEventListener('mousemove', onResizeMove)
    document.addEventListener('mouseup', onResizeEnd)
    // 拖拽期间禁止选中文本，避免拖动时选中页面内容
    document.body.style.userSelect = 'none'
  }

  function onResizeMove(event: MouseEvent): void {
    if (!isDragging.value) {
      return
    }
    const delta = dragStartX - event.clientX
    const maxWidth = Math.min(window.innerWidth * 0.92, 1600)
    drawerWidth.value = Math.min(maxWidth, Math.max(minWidth, dragStartWidth + delta))
  }

  function onResizeEnd(): void {
    if (!isDragging.value) {
      return
    }
    isDragging.value = false
    document.body.style.userSelect = ''
    document.removeEventListener('mousemove', onResizeMove)
    document.removeEventListener('mouseup', onResizeEnd)
  }

  // 组件卸载兜底清理，防止监听器泄漏
  onBeforeUnmount(() => {
    onResizeEnd()
  })

  return { drawerWidth, isDragging, onResizeStart }
}
