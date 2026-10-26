import { api } from './request'
import type { PageResult, RecordVO } from '@/types/model'

/** 执行记录分页 */
export function pageRecords(current: number, size: number, taskId: number | null, status: string) {
  return api<PageResult<RecordVO>>({
    url: '/record/page',
    method: 'get',
    params: { current, size, taskId: taskId ?? undefined, status: status || undefined },
  })
}

/** 记录详情（含全量日志） */
export function getRecord(id: number) {
  return api<RecordVO>({ url: `/record/${id}`, method: 'get' })
}

/** 删除记录 */
export function deleteRecord(id: number) {
  return api<void>({ url: `/record/${id}`, method: 'delete' })
}

/** 某任务最近一次执行记录 */
export function latestRecord(taskId: number) {
  return api<RecordVO | null>({ url: '/record/latest', method: 'get', params: { taskId } })
}
