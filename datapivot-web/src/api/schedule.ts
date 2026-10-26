import { api } from './request'
import type { PageResult, ScheduleForm, ScheduleVO } from '@/types/model'

/** 定时配置分页 */
export function pageSchedules(current: number, size: number) {
  return api<PageResult<ScheduleVO>>({
    url: '/schedule/page',
    method: 'get',
    params: { current, size },
  })
}

/** 新建定时配置 */
export function createSchedule(form: ScheduleForm) {
  return api<void>({ url: '/schedule', method: 'post', data: form })
}

/** 修改定时配置 */
export function updateSchedule(id: number, form: ScheduleForm) {
  return api<void>({ url: `/schedule/${id}`, method: 'put', data: form })
}

/** 删除定时配置 */
export function deleteSchedule(id: number) {
  return api<void>({ url: `/schedule/${id}`, method: 'delete' })
}

/** 启用 */
export function enableSchedule(id: number) {
  return api<void>({ url: `/schedule/${id}/enable`, method: 'post' })
}

/** 暂停 */
export function disableSchedule(id: number) {
  return api<void>({ url: `/schedule/${id}/disable`, method: 'post' })
}

/** 立即触发一次 */
export function triggerSchedule(id: number) {
  return api<void>({ url: `/schedule/${id}/trigger`, method: 'post' })
}
