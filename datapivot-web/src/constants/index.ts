import type { ConflictStrategy, DbTypeCode, DbTypeOption } from '@/types/model'

/** 支持的数据库类型（与后端 DbTypeEnum 保持一致） */
export const DB_TYPE_OPTIONS: DbTypeOption[] = [
  {
    code: 'MYSQL',
    label: 'MySQL',
    defaultPort: 3306,
    defaultDriver: 'com.mysql.cj.jdbc.Driver',
    urlTemplate:
      'jdbc:mysql://{host}:{port}/{db}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&useCursorFetch=true&useInformationSchema=true',
  },
  {
    code: 'ORACLE',
    label: 'Oracle',
    defaultPort: 1521,
    defaultDriver: 'oracle.jdbc.OracleDriver',
    urlTemplate: 'jdbc:oracle:thin:@//{host}:{port}/{db}',
  },
  {
    code: 'DM',
    label: '达梦DM',
    defaultPort: 5236,
    defaultDriver: 'dm.jdbc.driver.DmDriver',
    urlTemplate: 'jdbc:dm://{host}:{port}/{db}',
  },
  {
    code: 'KINGBASE',
    label: '人大金仓KingbaseES',
    defaultPort: 54321,
    defaultDriver: 'com.kingbase8.Driver',
    urlTemplate: 'jdbc:kingbase8://{host}:{port}/{db}',
  },
  {
    code: 'POSTGRESQL',
    label: 'PostgreSQL',
    defaultPort: 5432,
    defaultDriver: 'org.postgresql.Driver',
    urlTemplate: 'jdbc:postgresql://{host}:{port}/{db}?currentSchema=public',
  },
]

export function dbTypeOption(code: DbTypeCode): DbTypeOption {
  const found = DB_TYPE_OPTIONS.find((option) => option.code === code)
  if (!found) {
    throw new Error(`未知数据库类型: ${code}`)
  }
  return found
}

export function dbTypeLabel(code: string): string {
  return DB_TYPE_OPTIONS.find((option) => option.code === code)?.label ?? code
}

/** 主键冲突策略选项 */
export const CONFLICT_STRATEGY_OPTIONS: {
  code: ConflictStrategy
  label: string
  description: string
}[] = [
  { code: 'OVERWRITE', label: '覆盖更新', description: '目标表存在相同主键时，更新其余字段' },
  { code: 'SKIP', label: '跳过', description: '目标表存在相同主键时，忽略该行' },
  { code: 'ERROR', label: '报错中断', description: '主键冲突直接报错，任务失败结束' },
]

/** 类型大类显示名（用于字段映射转换提示） */
export const CATEGORY_LABELS: Record<string, string> = {
  STRING: '字符串',
  NUMBER: '数字',
  DATETIME: '日期',
  BOOLEAN: '布尔',
  BINARY: '二进制',
  OTHER: '其他',
}

/** 差异类型 -> 标签颜色（新增绿 / 删除红 / 修改黄） */
export function diffTagType(diffType: string): 'success' | 'danger' | 'warning' {
  if (diffType.endsWith('_ADD')) {
    return 'success'
  }
  if (diffType.endsWith('_DELETE')) {
    return 'danger'
  }
  return 'warning'
}
