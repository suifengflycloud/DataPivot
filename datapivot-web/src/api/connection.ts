import { api } from './request'
import type { ConnectionForm, ConnectionVO, PageResult, TestResult } from '@/types/model'

/** 连接分页 */
export function pageConnections(current: number, size: number, keyword: string) {
  return api<PageResult<ConnectionVO>>({
    url: '/connection/page',
    method: 'get',
    params: { current, size, keyword: keyword || undefined },
  })
}

/** 连接全量列表（下拉） */
export function listConnections() {
  return api<ConnectionVO[]>({ url: '/connection/list', method: 'get' })
}

/** 连接详情 */
export function getConnection(id: number) {
  return api<ConnectionVO>({ url: `/connection/${id}`, method: 'get' })
}

/** 新增连接 */
export function createConnection(form: ConnectionForm) {
  return api<void>({ url: '/connection', method: 'post', data: form })
}

/** 编辑连接 */
export function updateConnection(id: number, form: ConnectionForm) {
  return api<void>({ url: `/connection/${id}`, method: 'put', data: form })
}

/** 删除连接 */
export function deleteConnection(id: number) {
  return api<void>({ url: `/connection/${id}`, method: 'delete' })
}

/** 测试已保存连接 */
export function testConnectionById(id: number) {
  return api<TestResult>({ url: `/connection/test/${id}`, method: 'get' })
}

/** 测试表单连接（未保存） */
export function testConnectionByForm(form: ConnectionForm) {
  return api<TestResult>({ url: '/connection/test', method: 'post', data: form })
}
