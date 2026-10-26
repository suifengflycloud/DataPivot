import { api } from './request'
import type { PageResult, SyncProgress, SyncTaskDTO, SyncTaskDetail, SyncTaskVO } from '@/types/model'

/** 任务分页 */
export function pageTasks(current: number, size: number, keyword: string) {
  return api<PageResult<SyncTaskVO>>({
    url: '/task/page',
    method: 'get',
    params: { current, size, keyword: keyword || undefined },
  })
}

/** 任务简要列表（下拉） */
export function listTasks() {
  return api<SyncTaskVO[]>({ url: '/task/list', method: 'get' })
}

/** 任务详情 */
export function getTask(id: number) {
  return api<SyncTaskDetail>({ url: `/task/${id}`, method: 'get' })
}

/** 新建任务 */
export function createTask(dto: SyncTaskDTO) {
  return api<void>({ url: '/task', method: 'post', data: dto })
}

/** 编辑任务 */
export function updateTask(id: number, dto: SyncTaskDTO) {
  return api<void>({ url: `/task/${id}`, method: 'put', data: dto })
}

/** 删除任务 */
export function deleteTask(id: number) {
  return api<void>({ url: `/task/${id}`, method: 'delete' })
}

/** 立即执行，返回执行记录 id */
export function executeTask(id: number) {
  return api<number>({ url: `/task/${id}/execute`, method: 'post' })
}

/** 轮询执行进度 */
export function getProgress(recordId: number) {
  return api<SyncProgress>({ url: `/task/progress/${recordId}`, method: 'get' })
}
