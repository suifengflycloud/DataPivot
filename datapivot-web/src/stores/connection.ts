import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listConnections } from '@/api/connection'
import type { ConnectionVO } from '@/types/model'

/**
 * 数据库连接全局状态：任务编辑、结构对比、定时任务等页面共享连接下拉数据
 */
export const useConnectionStore = defineStore('connection', () => {
  const connections = ref<ConnectionVO[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  /** 拉取连接列表（默认缓存，force 强刷） */
  async function fetchAll(force = false): Promise<void> {
    if (loaded.value && !force) {
      return
    }
    if (loading.value) {
      return
    }
    loading.value = true
    try {
      connections.value = await listConnections()
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  return { connections, loaded, loading, fetchAll }
})
