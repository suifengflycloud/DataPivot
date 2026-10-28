package com.datapivot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datapivot.common.BusinessException;
import com.datapivot.datasource.DynamicDataSourceManager;
import com.datapivot.datasource.IdentifierUtils;
import com.datapivot.entity.ColumnMapping;
import com.datapivot.entity.DbConnection;
import com.datapivot.entity.SyncRecord;
import com.datapivot.entity.SyncTask;
import com.datapivot.entity.TableMapping;
import com.datapivot.enums.ConflictStrategyEnum;
import com.datapivot.enums.DbTypeEnum;
import com.datapivot.enums.RecordStatusEnum;
import com.datapivot.enums.TriggerTypeEnum;
import com.datapivot.mapper.ColumnMappingMapper;
import com.datapivot.mapper.SyncRecordMapper;
import com.datapivot.mapper.SyncTaskMapper;
import com.datapivot.mapper.TableMappingMapper;
import com.datapivot.service.DbConnectionService;
import com.datapivot.service.MetadataService;
import com.datapivot.service.SyncExecuteService;
import com.datapivot.service.support.IncrementalWatermark;
import com.datapivot.service.support.SyncProgress;
import com.datapivot.service.support.SyncSqlBuilder;
import com.datapivot.service.support.ValueConverter;
import com.datapivot.vo.ColumnMetaVO;
import com.datapivot.vo.SyncProgressVO;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 同步执行服务实现
 * <p>执行流程：
 * 1. 校验任务配置并创建 RUNNING 状态执行记录；
 * 2. 提交到独立线程池异步执行（避免长时间占用请求线程）；
 * 3. 每组表映射：JDBC 元数据核对字段 -> COUNT 统计总量 -> 流式分批读取源表 ->
 *    按目标字段类型转换值 -> 目标表批量写入（每批一提交）；
 * 4. 进度实时写入内存进度对象，前端轮询展示；每批同步回写执行记录表。</p>
 */
@Slf4j
@Service
public class SyncExecuteServiceImpl implements SyncExecuteService {

    private final SyncTaskMapper taskMapper;
    private final TableMappingMapper tableMappingMapper;
    private final ColumnMappingMapper columnMappingMapper;
    private final SyncRecordMapper recordMapper;
    private final DbConnectionService connectionService;
    private final MetadataService metadataService;
    private final DynamicDataSourceManager dataSourceManager;

    private final int maxLogLines;
    private final int maxErrorLogChars;

    /** 同步工作线程池（小核心数 + 有界队列，避免大并发打爆源/目标库） */
    private final ThreadPoolExecutor workerPool = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new AtomicIntegerThreadFactory(),
            new ThreadPoolExecutor.CallerRunsPolicy());

    /** 执行进度缓存：recordId -> 进度对象 */
    private final Map<Long, SyncProgress> progressMap = new ConcurrentHashMap<>();

    public SyncExecuteServiceImpl(SyncTaskMapper taskMapper,
                                  TableMappingMapper tableMappingMapper,
                                  ColumnMappingMapper columnMappingMapper,
                                  SyncRecordMapper recordMapper,
                                  DbConnectionService connectionService,
                                  MetadataService metadataService,
                                  DynamicDataSourceManager dataSourceManager,
                                  @Value("${datapivot.sync.max-log-lines:300}") int maxLogLines,
                                  @Value("${datapivot.sync.max-error-log-chars:60000}") int maxErrorLogChars) {
        this.taskMapper = taskMapper;
        this.tableMappingMapper = tableMappingMapper;
        this.columnMappingMapper = columnMappingMapper;
        this.recordMapper = recordMapper;
        this.connectionService = connectionService;
        this.metadataService = metadataService;
        this.dataSourceManager = dataSourceManager;
        this.maxLogLines = maxLogLines;
        this.maxErrorLogChars = maxErrorLogChars;
    }

    @Override
    public Long executeTask(Long taskId, TriggerTypeEnum triggerType) {
        SyncTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("同步任务不存在或已删除");
        }
        List<TableMapping> mappings = tableMappingMapper.selectList(
                new LambdaQueryWrapper<TableMapping>().eq(TableMapping::getTaskId, taskId));
        if (mappings.isEmpty()) {
            throw new BusinessException("任务 [" + task.getTaskName() + "] 未配置表映射，请先编辑任务");
        }
        // 校验源/目标连接可用（密码保持密文，由动态数据源管理器统一解密）
        DbConnection sourceConn = connectionService.getByIdNotNull(task.getSourceConnId());
        DbConnection targetConn = connectionService.getByIdNotNull(task.getTargetConnId());

        // 创建执行记录（RUNNING）
        SyncRecord record = new SyncRecord();
        record.setTaskId(task.getId());
        record.setTaskName(task.getTaskName());
        record.setTriggerType(triggerType.getCode());
        record.setStatus(RecordStatusEnum.RUNNING.getCode());
        record.setTotalCount(0L);
        record.setSuccessCount(0L);
        record.setFailCount(0L);
        record.setStartTime(LocalDateTime.now());
        recordMapper.insert(record);

        SyncProgress progress = new SyncProgress(record.getId(), task.getId(), task.getTaskName(), maxLogLines);
        progressMap.put(record.getId(), progress);
        progress.appendLog("任务开始执行，触发方式: " + triggerType.getLabel()
                + "，共 " + mappings.size() + " 组表映射");

        workerPool.submit(() -> runSafely(task, sourceConn, targetConn, mappings, progress));
        return record.getId();
    }

    @Override
    public SyncProgressVO getProgress(Long recordId) {
        SyncProgress progress = progressMap.get(recordId);
        return progress == null ? null : progress.toVO();
    }

    @Override
    public String awaitCompletion(Long recordId, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            SyncProgress progress = progressMap.get(recordId);
            if (progress == null) {
                // 进度对象可能已被清理，回查记录表兜底
                SyncRecord record = recordMapper.selectById(recordId);
                return record == null ? null : record.getStatus();
            }
            if (!progress.isRunning()) {
                return progress.getStatus();
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /** 异步任务总入口：兜住一切异常，保证记录一定落终态 */
    private void runSafely(SyncTask task, DbConnection sourceConn, DbConnection targetConn,
                           List<TableMapping> mappings, SyncProgress progress) {
        try {
            runTask(task, sourceConn, targetConn, mappings, progress);
        } catch (Exception e) {
            log.error("同步任务执行异常, taskId={}", task.getId(), e);
            progress.appendLog("[异常] " + rootMessage(e));
            progress.markFailed(rootMessage(e));
        } finally {
            finishRecord(progress);
        }
    }

    /** 逐表执行同步；任一表失败即按策略终止整个任务 */
    private void runTask(SyncTask task, DbConnection sourceConn, DbConnection targetConn,
                         List<TableMapping> mappings, SyncProgress progress) {
        for (TableMapping mapping : mappings) {
            // 上一张表已失败则不再继续
            if (!progress.isRunning()) {
                break;
            }
            String sourceDisplay = display(mapping.getSourceSchema(), mapping.getSourceTable());
            String targetDisplay = display(mapping.getTargetSchema(), mapping.getTargetTable());
            progress.setCurrentTable(sourceDisplay + " → " + targetDisplay);
            try (Connection sourceConnection = dataSourceManager.getConnection(sourceConn);
                 Connection targetConnection = dataSourceManager.getConnection(targetConn)) {
                syncOneTable(task, mapping, sourceConn, targetConn, sourceConnection, targetConnection, progress);
            } catch (Exception e) {
                progress.appendLog("[失败] 表 " + sourceDisplay + " 同步失败: " + rootMessage(e));
                progress.markFailed("表 " + sourceDisplay + " 同步失败: " + rootMessage(e));
            }
        }
        if (progress.isRunning()) {
            progress.appendLog("任务执行完成");
            progress.finishSuccess();
        }
    }

    /**
     * 单表同步：字段核对 -> 计数 -> 流式读取 -> 批量写入
     */
    private void syncOneTable(SyncTask task, TableMapping mapping, DbConnection sourceConn, DbConnection targetConn,
                              Connection sourceConnection, Connection targetConnection,
                              SyncProgress progress) throws SQLException {
        DbTypeEnum sourceType = DbTypeEnum.fromCode(sourceConn.getDbType());
        DbTypeEnum targetType = DbTypeEnum.fromCode(targetConn.getDbType());
        String sourceDisplay = display(mapping.getSourceSchema(), mapping.getSourceTable());
        String targetDisplay = display(mapping.getTargetSchema(), mapping.getTargetTable());

        // 1. 读取两端字段元数据（JDBC DatabaseMetaData）
        List<ColumnMetaVO> sourceColumns = metadataService.listColumns(sourceConn,
                mapping.getSourceCatalog(), mapping.getSourceSchema(), mapping.getSourceTable());
        List<ColumnMetaVO> targetColumns = metadataService.listColumns(targetConn,
                mapping.getTargetCatalog(), mapping.getTargetSchema(), mapping.getTargetTable());
        if (sourceColumns.isEmpty()) {
            throw new BusinessException("源表 " + sourceDisplay + " 不存在或没有字段");
        }
        if (targetColumns.isEmpty()) {
            throw new BusinessException("目标表 " + targetDisplay + " 不存在或没有字段");
        }
        Map<String, ColumnMetaVO> sourceMap = sourceColumns.stream()
                .collect(Collectors.toMap(c -> c.getColumnName().toLowerCase(), Function.identity(),
                        (a, b) -> a));
        Map<String, ColumnMetaVO> targetMap = targetColumns.stream()
                .collect(Collectors.toMap(c -> c.getColumnName().toLowerCase(), Function.identity(),
                        (a, b) -> a));

        // 2. 核对字段映射：映射字段必须存在于真实元数据（配置漂移/非法字段直接拦截）
        List<ColumnMapping> columnMappings = columnMappingMapper.selectList(
                new LambdaQueryWrapper<ColumnMapping>().eq(ColumnMapping::getMappingId, mapping.getId()));
        List<ColumnMetaVO> selectedSource = new ArrayList<>();
        List<ColumnMetaVO> selectedTarget = new ArrayList<>();
        for (ColumnMapping columnMapping : columnMappings) {
            ColumnMetaVO sourceColumn = sourceMap.get(columnMapping.getSourceColumn().toLowerCase());
            if (sourceColumn == null) {
                throw new BusinessException("源表 " + sourceDisplay + " 中不存在字段 ["
                        + columnMapping.getSourceColumn() + "]，请重新编辑字段映射");
            }
            ColumnMetaVO targetColumn = targetMap.get(columnMapping.getTargetColumn().toLowerCase());
            if (targetColumn == null) {
                throw new BusinessException("目标表 " + targetDisplay + " 中不存在字段 ["
                        + columnMapping.getTargetColumn() + "]，请重新编辑字段映射");
            }
            selectedSource.add(sourceColumn);
            selectedTarget.add(targetColumn);
        }
        if (selectedSource.isEmpty()) {
            progress.appendLog("表 " + sourceDisplay + " 未配置字段映射，跳过");
            return;
        }

        // 3. 目标表主键与冲突策略校验
        List<String> targetPkColumns = targetColumns.stream()
                .filter(c -> Boolean.TRUE.equals(c.getPk()))
                .map(ColumnMetaVO::getColumnName)
                .collect(Collectors.toList());
        ConflictStrategyEnum strategy = ConflictStrategyEnum.fromCode(task.getConflictStrategy());
        SyncSqlBuilder.checkStrategySupport(!targetPkColumns.isEmpty(), strategy, targetDisplay);

        // 3.5 增量同步配置：校验增量字段存在且类型受支持，并解析上次同步水位
        boolean incremental = task.getFullSync() != null && task.getFullSync() == 0;
        ColumnMetaVO incMeta = null;
        Object watermarkParam = null;
        boolean incInclusive = false;
        if (incremental) {
            if (mapping.getIncColumn() == null || mapping.getIncColumn().isBlank()) {
                throw new BusinessException("表 " + sourceDisplay + " 未配置增量字段，增量同步必须为每组映射选择增量依据字段");
            }
            incMeta = sourceMap.get(mapping.getIncColumn().toLowerCase());
            if (incMeta == null) {
                throw new BusinessException("源表 " + sourceDisplay + " 中不存在增量字段 [" + mapping.getIncColumn()
                        + "]，请重新编辑任务选择增量字段");
            }
            IncrementalWatermark.checkTypeSupport(incMeta, sourceDisplay, mapping.getIncColumn());
            if (mapping.getIncLastValue() != null && !mapping.getIncLastValue().isBlank()) {
                Object watermark = IncrementalWatermark.parse(incMeta, mapping.getIncLastValue());
                watermarkParam = IncrementalWatermark.toBindParam(watermark);
                // 覆盖更新/跳过写入幂等，用 >= 重放边界记录防止漏采；报错中断不容忍重复投递，用 > 严格递增
                incInclusive = strategy != ConflictStrategyEnum.ERROR;
            }
        }
        String incWhere = watermarkParam == null
                ? null
                : SyncSqlBuilder.buildIncWhere(sourceType, incMeta.getColumnName(), incInclusive);

        // 4. 源表总量统计（用于进度百分比；增量模式统计水位之后的记录数）
        String sourceQualified = IdentifierUtils.qualifiedName(sourceType,
                mapping.getSourceCatalog(), mapping.getSourceSchema(), mapping.getSourceTable());
        long total = countRows(sourceConnection, sourceQualified, incWhere, watermarkParam);
        progress.increaseTotal(total);
        progress.appendLog("开始同步 " + sourceDisplay + " → " + targetDisplay
                + "，共 " + total + " 条，冲突策略: " + strategy.getLabel()
                + (incremental
                        ? (watermarkParam == null
                                ? "（增量模式：首次执行全量拉取）"
                                : "（增量模式：拉取水位 " + mapping.getIncLastValue()
                                + (incInclusive ? " 及之后" : " 之后") + "的记录）")
                        : ""));
        if (total == 0) {
            progress.appendLog("表 " + sourceDisplay + (incremental ? " 无新增数据，跳过" : " 无数据，跳过"));
            return;
        }

        // 5. 构建 SQL（表名/字段名已校验并加引号；值全部占位符）
        //    增量模式若增量字段不在映射字段中，作为附加列拼在 SELECT 末尾，用于计算新水位
        List<String> sourceColumnNames = selectedSource.stream()
                .map(ColumnMetaVO::getColumnName).collect(Collectors.toCollection(ArrayList::new));
        int incSelectIndex = -1;
        if (incMeta != null) {
            incSelectIndex = sourceColumnNames.indexOf(incMeta.getColumnName());
            if (incSelectIndex < 0) {
                sourceColumnNames.add(incMeta.getColumnName());
                incSelectIndex = sourceColumnNames.size() - 1;
            }
        }
        List<String> targetColumnNames = selectedTarget.stream()
                .map(ColumnMetaVO::getColumnName).toList();
        String targetQualified = IdentifierUtils.qualifiedName(targetType,
                mapping.getTargetCatalog(), mapping.getTargetSchema(), mapping.getTargetTable());
        String selectSql = SyncSqlBuilder.buildSelectSql(sourceType, sourceQualified, sourceColumnNames, incWhere);
        String writeSql = SyncSqlBuilder.buildWriteSql(targetType, targetQualified, targetColumnNames,
                targetPkColumns, strategy);
        int batchSize = Math.max(1, task.getBatchSize() == null ? 1000 : task.getBatchSize());

        // 6. 流式读取 + 分批写入
        // 源连接关闭自动提交：PostgreSQL/金仓 驱动仅在事务内生效 fetchSize 流式游标
        sourceConnection.setAutoCommit(false);
        targetConnection.setAutoCommit(false);
        Object maxWatermark = null;
        try (PreparedStatement selectPs = sourceConnection.prepareStatement(selectSql,
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             PreparedStatement writePs = targetConnection.prepareStatement(writeSql)) {
            selectPs.setFetchSize(batchSize);
            if (watermarkParam != null) {
                selectPs.setObject(1, watermarkParam);
            }
            int inBatch = 0;
            long batchIndex = 0;
            try (ResultSet rs = selectPs.executeQuery()) {
                while (rs.next()) {
                    // 逐字段取值并按目标类型转换（null 值走 setNull）
                    for (int i = 0; i < selectedSource.size(); i++) {
                        Object raw = rs.getObject(i + 1);
                        ColumnMetaVO targetMeta = selectedTarget.get(i);
                        if (raw == null) {
                            writePs.setNull(i + 1, targetMeta.getDataType());
                        } else {
                            Object converted = ValueConverter.convert(raw,
                                    selectedSource.get(i).getDataType(), targetMeta.getDataType());
                            if (converted instanceof byte[] bytes) {
                                writePs.setBytes(i + 1, bytes);
                            } else {
                                writePs.setObject(i + 1, converted, targetMeta.getDataType());
                            }
                        }
                    }
                    // 跟踪本批增量字段最大值，作为本次同步的新水位
                    if (incSelectIndex >= 0) {
                        Object incValue = IncrementalWatermark.normalize(incMeta, rs.getObject(incSelectIndex + 1));
                        if (incValue != null
                                && (maxWatermark == null || IncrementalWatermark.compare(incValue, maxWatermark) > 0)) {
                            maxWatermark = incValue;
                        }
                    }
                    writePs.addBatch();
                    inBatch++;
                    if (inBatch >= batchSize) {
                        commitBatch(writePs, targetConnection, inBatch, ++batchIndex, progress);
                        inBatch = 0;
                    }
                }
            }
            // 提交最后一批
            if (inBatch > 0) {
                commitBatch(writePs, targetConnection, inBatch, ++batchIndex, progress);
            }
            sourceConnection.commit();
        } catch (SQLException e) {
            rollbackQuietly(sourceConnection);
            rollbackQuietly(targetConnection);
            throw e;
        } finally {
            restoreAutoCommit(sourceConnection);
            restoreAutoCommit(targetConnection);
        }

        // 7. 全部落库成功后推进增量水位（失败抛异常由执行记录如实标记，下次重跑按旧水位幂等重放）
        if (maxWatermark != null) {
            String newWatermark = IncrementalWatermark.toStored(maxWatermark);
            TableMapping watermarkUpdate = new TableMapping();
            watermarkUpdate.setId(mapping.getId());
            watermarkUpdate.setIncLastValue(newWatermark);
            tableMappingMapper.updateById(watermarkUpdate);
            progress.appendLog("增量同步完成，水位更新为 " + newWatermark);
        }
    }

    /** 提交一批写入并刷新进度 */
    private void commitBatch(PreparedStatement writePs, Connection targetConnection, int count,
                             long batchIndex, SyncProgress progress) throws SQLException {
        writePs.executeBatch();
        writePs.clearBatch();
        targetConnection.commit();
        progress.addSuccess(count);
        progress.appendLog("第 " + batchIndex + " 批写入完成，本批 " + count
                + " 条，累计成功 " + progress.getSuccessCount() + " 条");
        // 每批回写执行记录（轻量字段更新，保证中途异常也能保留进度）
        updateRecordCounts(progress);
    }

    /** 总行数统计（whereSql 为 null 时全表统计；有条件时绑定单个水位参数） */
    private long countRows(Connection connection, String qualifiedTable, String whereSql, Object whereParam)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                SyncSqlBuilder.buildCountSql(qualifiedTable, whereSql))) {
            if (whereSql != null) {
                ps.setObject(1, whereParam);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /** 任务终态落库 */
    private void finishRecord(SyncProgress progress) {
        SyncRecord record = new SyncRecord();
        record.setId(progress.getRecordId());
        record.setStatus(progress.getStatus());
        record.setSuccessCount(progress.getSuccessCount());
        record.setFailCount(progress.getFailCount());
        record.setTotalCount(progress.getSuccessCount() + progress.getFailCount());
        record.setEndTime(LocalDateTime.now());
        String logs = progress.dumpLogs();
        if (logs.length() > maxErrorLogChars) {
            logs = logs.substring(logs.length() - maxErrorLogChars);
        }
        record.setErrorLog(logs);
        recordMapper.updateById(record);
    }

    /** 每批回写计数（不影响主流程） */
    private void updateRecordCounts(SyncProgress progress) {
        try {
            SyncRecord record = new SyncRecord();
            record.setId(progress.getRecordId());
            record.setSuccessCount(progress.getSuccessCount());
            record.setFailCount(progress.getFailCount());
            record.setTotalCount(progress.getSuccessCount() + progress.getFailCount());
            recordMapper.updateById(record);
        } catch (Exception e) {
            log.warn("回写执行进度失败(不影响同步): {}", e.getMessage());
        }
    }

    /** 定时清理已结束超过 30 分钟的进度对象（防止内存缓慢膨胀） */
    @Scheduled(fixedDelay = 600000)
    public void cleanFinishedProgress() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        progressMap.entrySet().removeIf(entry -> {
            SyncProgress progress = entry.getValue();
            return !progress.isRunning()
                    && progress.getFinishTime() != null
                    && progress.getFinishTime().isBefore(threshold);
        });
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // 回滚失败仅忽略，由连接池关闭兜底
        }
    }

    private void restoreAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // 连接即将归还池中，恢复失败由池销毁兜底
        }
    }

    private String display(String schema, String table) {
        return schema == null || schema.isBlank() ? table : schema + "." + table;
    }

    /** 提取异常根因消息（避免把驱动堆栈直接抛给用户） */
    private String rootMessage(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        }
        if (current instanceof SQLRecoverableException) {
            message = "数据库连接异常: " + message;
        }
        return message.length() > 500 ? message.substring(0, 500) + "..." : message;
    }

    @PreDestroy
    public void shutdown() {
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** 同步工作线程命名工厂 */
    private static class AtomicIntegerThreadFactory implements java.util.concurrent.ThreadFactory {

        private final AtomicInteger index = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "sync-worker-" + index.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}
