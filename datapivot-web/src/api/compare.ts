import { api, apiDownload } from './request'
import type { CompareResult } from '@/types/model'

/** 执行结构对比 */
export function executeCompare(baseConnId: number, targetConnId: number) {
  return api<CompareResult>({
    url: '/compare/execute',
    method: 'post',
    data: { baseConnId, targetConnId },
  })
}

/** 下载 DDL 脚本（文件流，不走 JSON 解包） */
export function downloadDdlScript(baseConnId: number, targetConnId: number): Promise<Blob> {
  return apiDownload({
    url: '/compare/download',
    method: 'post',
    data: { baseConnId, targetConnId },
  })
}
