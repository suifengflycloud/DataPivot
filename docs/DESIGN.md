# DataPivot 设计契约（开发内部文档）

> 本文档是前后端唯一事实来源：接口契约、表结构、枚举、关键约定。前后端编码必须与本文档一致。

## 1. 全局约定

- 统一返回体：`{ code: 200|500, msg: string, data: T }`，HTTP 状态码恒为 200，业务失败 code=500。
- 分页返回体 `PageResult<T>`：`{ records: T[], total: number, current: number, size: number }`。
- 后端端口 **8081**（本机 8080 被占用）；前端 dev 端口 5173，Vite 代理 `/api → http://127.0.0.1:8081`。
- XXL-Job 调度中心默认地址 `http://127.0.0.1:8280/xxl-job-admin`（建议 docker `-p 8280:8080`），登录 admin/123456，执行器 AppName=`datapivot-executor`，accessToken=`default_token`，执行器端口 9999。
- 项目自身配置库 MySQL：`jdbc:mysql://127.0.0.1:3306/datapivot`，默认 root/root（环境变量 DP_DB_USER/DP_DB_PASSWORD/DP_DB_URL 覆盖）。
- AES 密钥环境变量 `DP_AES_KEY`（默认 `DataPivot@2026#Key`，任意长度，内部 SHA-256 派生 16 字节密钥）。
- 包名 `com.datapivot`；Java 17；Spring Boot 3.3.4。

## 2. 数据库枚举

`DbTypeEnum`: MYSQL(3306, com.mysql.cj.jdbc.Driver) / ORACLE(1521, oracle.jdbc.OracleDriver) / DM(达梦, 5236, dm.jdbc.driver.DmDriver) / KINGBASE(金仓, 54321, com.kingbase8.Driver) / POSTGRESQL(5432, org.postgresql.Driver)。

`ConflictStrategyEnum`: OVERWRITE(覆盖更新) / SKIP(跳过) / ERROR(报错中断)。
`TriggerTypeEnum`: MANUAL / SCHEDULED。`RecordStatusEnum`: RUNNING / SUCCESS / FAILED。
`DiffTypeEnum`: TABLE_ADD / TABLE_DELETE / TABLE_MODIFY / COLUMN_ADD / COLUMN_DELETE / COLUMN_TYPE / COLUMN_LENGTH / COLUMN_NULLABLE / COLUMN_PK / COLUMN_COMMENT。

标识符安全规则：所有进入 SQL 的表名/字段名必须匹配 `^[A-Za-z0-9_$#\u4e00-\u9fa5]+$` 且长度≤200，并在执行期与 JDBC 元数据核对存在性；拼接时一律按方言加引号（MySQL 反引号、其余双引号，内部引号 doubling 转义）；值一律 PreparedStatement 占位符传参。

## 3. 表结构（sql/datapivot_init.sql，库名 datapivot，utf8mb4）

- `dp_db_connection`：id, conn_name, db_type, host, port, db_name, username, password(AES密文), driver_class, jdbc_url, extra_params, remark, create_time, update_time, del_flag(逻辑删除)
- `dp_sync_task`：id, task_name, source_conn_id, target_conn_id, conflict_strategy, batch_size(默认1000), full_sync(1/0), remark, create_time, update_time, del_flag
- `dp_table_mapping`：id, task_id, source_catalog, source_schema, source_table, target_catalog, target_schema, target_table, create_time（物理删除）
- `dp_column_mapping`：id, mapping_id, source_column, target_column, create_time（物理删除）
- `dp_sync_record`：id, task_id, task_name, trigger_type, status, total_count, success_count, fail_count, start_time, end_time, error_log(MEDIUMTEXT), create_time
- `dp_schedule_config`：id, task_id, cron_expr, job_desc, owner, xxl_job_id, status(ENABLED/DISABLED), create_time, update_time, del_flag

## 4. 接口契约（前缀 /api）

### 连接管理 ConnectionController
- `GET /connection/page?current&size&keyword` → PageResult<DbConnectionVO>
- `GET /connection/list` → DbConnectionVO[]（下拉用：id, connName, dbType, host, port, dbName）
- `GET /connection/{id}` → DbConnectionVO（passwordMasked 脱敏）
- `POST /connection`、`PUT /connection/{id}` ← DbConnectionDTO；`DELETE /connection/{id}`（被任务引用则拒绝）
- `GET /connection/test/{id}` / `POST /connection/test` ← DbConnectionDTO → `{success, message, costMs, dbProduct}`

DbConnectionDTO：connName*、dbType*(枚举)、host*、port*(1-65535)、dbName*、username*、password(新建必填/编辑留空不改)、driverClass、jdbcUrl*、extraParams?、remark?

### 元数据 MetadataController
- `GET /meta/tables?connectionId=` → TableMetaVO[] `{catalog, schema, tableName, displayName, remarks}`
- `GET /meta/columns?connectionId=&catalog=&schema=&tableName=` → ColumnMetaVO[] `{columnName, typeName, dataType, typeCategory(STRING|NUMBER|DATETIME|BOOLEAN|BINARY|OTHER), size, digits, nullable, pk, defaultValue, remarks}`

### 同步任务 SyncTaskController
- `GET /task/page?current&size&keyword` → PageResult<SyncTaskVO>（含 sourceConnName/targetConnName/mappingCount）
- `GET /task/list` → `{id, taskName}[]`
- `GET /task/{id}` → SyncTaskDetailVO `{...任务字段, mappings: TableMappingVO[]}`
- `POST /task`、`PUT /task/{id}` ← SyncTaskDTO；`DELETE /task/{id}`
- `POST /task/{id}/execute` → recordId(number)
- `GET /task/progress/{recordId}` → `{recordId, taskId, taskName, status, total, processed, successCount, failCount, percent, currentTable, logs: string[]}`（前端1s轮询）

SyncTaskDTO：taskName*、sourceConnId*、targetConnId*、conflictStrategy*(OVERWRITE|SKIP|ERROR)、batchSize*(1-100000)、fullSync*、remark?、mappings*[ {sourceCatalog?, sourceSchema?, sourceTable*, targetCatalog?, targetSchema?, targetTable*, columns*[ {sourceColumn*, targetColumn*} ]} ]

### 结构对比 SchemaCompareController
- `POST /compare/execute` ← `{baseConnId*, targetConnId*}` → CompareResultVO
- `POST /compare/download` ← `{baseConnId*, targetConnId*, fileName?}` → .sql 文件流（服务端校验文件名，仅允许 [A-Za-z0-9_\-.]）

CompareResultVO：`{baseConnName, targetConnName, targetDbType, typeMismatch, summary{addedTables, deletedTables, changedTables, totalDiffItems}, tableDiffs: [{tableName, displayName, diffType, columns: [{columnName, diffType, before, after, description, baseColumn?}], sourceColumns?}], ddlScript}`

### 执行记录 SyncRecordController
- `GET /record/page?current&size&taskId&status&keyword` → PageResult<RecordVO>（含 durationMs）
- `GET /record/{id}`、`DELETE /record/{id}`、`GET /record/latest?taskId=` → RecordVO|null

### 定时任务 ScheduleController
- `GET /schedule/page` → PageResult<ScheduleVO> `{id, taskId, taskName, cronExpr, jobDesc, owner, xxlJobId, status, nextTriggerTime, lastStatus, lastEndTime, createTime, updateTime}`
- `POST /schedule`、`PUT /schedule/{id}` ← ScheduleDTO `{taskId*, cronExpr*(6位cron), jobDesc*, owner*}`
- `DELETE /schedule/{id}`、`POST /schedule/{id}/enable`、`POST /schedule/{id}/disable`、`POST /schedule/{id}/trigger`

### 统计 StatsController
- `GET /stats/overview` → `{connectionCount, taskCount, scheduleCount, todayRecordCount, todaySuccessCount, todayFailCount, successRate}`

## 5. 关键机制

- 动态数据源：HikariCP 按 connectionId 缓存，配置签名(url|user)变化自动重建；测试连接用 DriverManager 直连(8s超时)不入池。
- 元数据只走 `DatabaseMetaData`（getTables/getColumns/getPrimaryKeys），结果按 catalog/schema/表名精确过滤（防 pattern 通配符误匹配）；Oracle 过滤 BIN$ 回收站。
- 同步执行：`SELECT 源列 FROM 源表` 流式读取(fetchSize=batchSize) → 值按目标 JDBC 类型类别转换(ValueConverter) → 目标 upsert 批写(executeBatch+commit/批)；进度存内存 Map + 每批回写 dp_sync_record；异常即失败终态。
- 目标写入 SQL 方言：MySQL `ON DUPLICATE KEY UPDATE`/`INSERT IGNORE`；Oracle/DM `MERGE INTO`；PG/金仓 `ON CONFLICT (pk) DO UPDATE/NOTHING`；无主键时 OVERWRITE/SKIP 策略直接报错提示。
- XXL-Job：单一 handler `syncTaskExecuteJob`，param=taskId，执行完 XxlJobHelper 回传成功/失败；调度配置保存时通过 Admin REST（login→cookie→jobinfo/add|update|remove|start|stop|trigger，jobgroup/pageList 按 appname 查组id）自动注册任务。
- 调度中心不可达时：执行器以空 adminAddresses 启动（不注册，无报错刷屏），仅打一条明确 WARN 提示；xxl.job.enabled=false 可完全关闭。

## 6. 前端路由

`/home 首页(Odometer)` `/connection 连接管理(Connection)` `/task 同步任务(Switch)` `/compare 结构对比(DocumentCopy)` `/record 任务执行记录(Tickets)` `/schedule 定时任务(Timer)`；布局：深色侧边栏(#0f172a) + 浅色内容区。
