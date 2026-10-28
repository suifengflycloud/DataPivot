// 与后端契约对齐的类型定义（详见 docs/DESIGN.md）

/** 数据库类型编码 */
export type DbTypeCode = 'MYSQL' | 'ORACLE' | 'DM' | 'KINGBASE' | 'POSTGRESQL'

/** 数据库类型下拉选项 */
export interface DbTypeOption {
  code: DbTypeCode
  label: string
  defaultPort: number
  defaultDriver: string
  urlTemplate: string
}

/** 统一返回体 */
export interface ApiBody<T> {
  code: number
  msg: string
  data: T
}

/** 分页返回体 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

// ==================== 连接管理 ====================

export interface ConnectionVO {
  id: number
  connName: string
  dbType: DbTypeCode
  dbTypeLabel: string
  host: string
  port: number
  dbName: string
  username: string
  passwordMasked: string
  driverClass: string
  jdbcUrl: string
  extraParams: string | null
  remark: string | null
  createTime: string
  updateTime: string
}

/** 连接表单（新增/编辑/测试） */
export interface ConnectionForm {
  id?: number
  connName: string
  dbType: DbTypeCode | ''
  host: string
  port: number | undefined
  dbName: string
  username: string
  password: string
  driverClass: string
  jdbcUrl: string
  extraParams: string
  remark: string
}

export interface TestResult {
  success: boolean
  message: string
  costMs: number
  dbProduct: string | null
}

// ==================== 元数据 ====================

export interface TableMeta {
  catalog: string | null
  schema: string | null
  tableName: string
  displayName: string
  remarks: string | null
}

export type TypeCategory = 'STRING' | 'NUMBER' | 'DATETIME' | 'BOOLEAN' | 'BINARY' | 'OTHER'

export interface ColumnMeta {
  columnName: string
  typeName: string
  dataType: number
  typeCategory: TypeCategory
  size: number
  digits: number
  nullable: boolean
  pk: boolean
  defaultValue: string | null
  remarks: string | null
}

// ==================== 同步任务 ====================

export type ConflictStrategy = 'OVERWRITE' | 'SKIP' | 'ERROR'

export interface SyncTaskVO {
  id: number
  taskName: string
  sourceConnId: number
  sourceConnName: string | null
  targetConnId: number
  targetConnName: string | null
  conflictStrategy: ConflictStrategy
  conflictStrategyLabel: string
  batchSize: number
  fullSync: boolean
  remark: string | null
  mappingCount: number
  createTime: string
  updateTime: string
}

export interface ColumnMappingItem {
  sourceColumn: string
  targetColumn: string
}

export interface TableMappingVO {
  id: number
  sourceCatalog: string | null
  sourceSchema: string | null
  sourceTable: string
  sourceDisplayName: string
  targetCatalog: string | null
  targetSchema: string | null
  targetTable: string
  targetDisplayName: string
  incColumn: string | null
  incLastValue: string | null
  columns: ColumnMappingItem[]
}

export interface SyncTaskDetail extends SyncTaskVO {
  mappings: TableMappingVO[]
}

/** 任务编辑器内的表映射行 */
export interface TableMappingRow {
  key: number
  sourceTable: TableMeta | null
  targetTable: TableMeta | null
  sourceColumns: ColumnMeta[]
  targetColumns: ColumnMeta[]
  columns: ColumnMappingRow[]
  /** 增量依据字段（增量同步模式使用，源表字段名） */
  incColumn: string | null
}

/** 任务编辑器内的字段映射行 */
export interface ColumnMappingRow {
  source: ColumnMeta
  selected: boolean
  target: string | null
}

/** 任务保存 DTO */
export interface SyncTaskDTO {
  taskName: string
  sourceConnId: number
  targetConnId: number
  conflictStrategy: ConflictStrategy
  batchSize: number
  fullSync: boolean
  remark: string
  mappings: {
    sourceCatalog: string | null
    sourceSchema: string | null
    sourceTable: string
    targetCatalog: string | null
    targetSchema: string | null
    targetTable: string
    /** 增量依据字段（全量同步传 null） */
    incColumn: string | null
    columns: ColumnMappingItem[]
  }[]
}

// ==================== 执行进度 / 记录 ====================

export type RunStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface SyncProgress {
  recordId: number
  taskId: number
  taskName: string
  status: RunStatus
  total: number
  processed: number
  successCount: number
  failCount: number
  percent: number
  currentTable: string
  logs: string[]
}

export interface RecordVO {
  id: number
  taskId: number
  taskName: string
  triggerType: 'MANUAL' | 'SCHEDULED'
  status: RunStatus
  totalCount: number
  successCount: number
  failCount: number
  startTime: string | null
  endTime: string | null
  durationMs: number | null
  errorLog: string | null
}

// ==================== 结构对比 ====================

export type TableDiffType = 'TABLE_ADD' | 'TABLE_DELETE' | 'TABLE_MODIFY'

export interface ColumnDiff {
  columnName: string
  diffType: string
  diffTypeLabel: string
  before: string | null
  after: string | null
  description: string
}

export interface TableDiff {
  tableName: string
  displayName: string
  diffType: TableDiffType
  diffTypeLabel: string
  columns: ColumnDiff[]
}

export interface CompareSummary {
  addedTables: number
  deletedTables: number
  changedTables: number
  totalDiffItems: number
}

export interface CompareResult {
  baseConnName: string
  targetConnName: string
  targetDbType: DbTypeCode
  typeMismatch: boolean
  summary: CompareSummary
  tableDiffs: TableDiff[]
  ddlScript: string
}

// ==================== 定时任务 ====================

export type ScheduleStatus = 'ENABLED' | 'DISABLED'

export interface ScheduleVO {
  id: number
  taskId: number
  taskName: string
  cronExpr: string
  jobDesc: string
  owner: string
  xxlJobId: number | null
  status: ScheduleStatus
  nextTriggerTime: string | null
  lastStatus: RunStatus | null
  lastEndTime: string | null
  createTime: string
  updateTime: string
}

export interface ScheduleForm {
  taskId: number | undefined
  cronExpr: string
  jobDesc: string
  owner: string
}

// ==================== 统计 ====================

export interface StatsOverview {
  connectionCount: number
  taskCount: number
  scheduleCount: number
  todayRecordCount: number
  todaySuccessCount: number
  todayFailCount: number
  successRate: number
}
