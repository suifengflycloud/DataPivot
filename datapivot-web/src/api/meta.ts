import { api } from './request'
import type { ColumnMeta, TableMeta } from '@/types/model'

/** 拉取连接下全部表 */
export function listTables(connectionId: number) {
  return api<TableMeta[]>({ url: '/meta/tables', method: 'get', params: { connectionId } })
}

/** 拉取指定表字段详情 */
export function listColumns(connectionId: number, table: TableMeta) {
  return api<ColumnMeta[]>({
    url: '/meta/columns',
    method: 'get',
    params: {
      connectionId,
      tableName: table.tableName,
      catalog: table.catalog ?? undefined,
      schema: table.schema ?? undefined,
    },
  })
}
