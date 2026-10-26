import { api } from './request'
import type { CompareResult } from '@/types/model'

/** 执行结构对比 */
export function executeCompare(baseConnId: number, targetConnId: number) {
  return api<CompareResult>({
    url: '/compare/execute',
    method: 'post',
    data: { baseConnId, targetConnId },
  })
}

/** 下载 DDL 脚本（Blob） */
export async function downloadDdlScript(baseConnId: number, targetConnId: number): Promise<Blob> {
  const response = await api<Blob>({
    url: '/compare/download',
    method: 'post',
    data: { baseConnId, targetConnId },
    responseType: 'blob',
  })
  return response
}
