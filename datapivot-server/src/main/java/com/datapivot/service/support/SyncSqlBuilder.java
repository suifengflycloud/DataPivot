package com.datapivot.service.support;

import com.datapivot.common.BusinessException;
import com.datapivot.datasource.IdentifierUtils;
import com.datapivot.enums.ConflictStrategyEnum;
import com.datapivot.enums.DbTypeEnum;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据同步 DML 构建器：生成源表读取 SQL 与目标表写入 SQL。
 * <p>安全约定：表名/字段名均已经过白名单校验并按方言加引号；数据值一律使用 ? 占位符。</p>
 * <p>各库主键冲突(upsert)方言：</p>
 * <ul>
 *   <li>MySQL：INSERT ... ON DUPLICATE KEY UPDATE / INSERT IGNORE</li>
 *   <li>Oracle / 达梦：MERGE INTO</li>
 *   <li>PostgreSQL / 金仓：INSERT ... ON CONFLICT (pk) DO UPDATE / DO NOTHING</li>
 * </ul>
 */
public final class SyncSqlBuilder {

    private SyncSqlBuilder() {
    }

    /** 源表读取 SQL：SELECT 只查参与同步的字段 */
    public static String buildSelectSql(DbTypeEnum dbType, String qualifiedTable, List<String> columns) {
        String columnList = columns.stream()
                .map(c -> IdentifierUtils.quote(c, dbType))
                .collect(Collectors.joining(", "));
        return "SELECT " + columnList + " FROM " + qualifiedTable;
    }

    /** 源表读取 SQL（增量模式：whereSql 为完整的 "WHERE ..." 条件或 null） */
    public static String buildSelectSql(DbTypeEnum dbType, String qualifiedTable, List<String> columns, String whereSql) {
        return whereSql == null
                ? buildSelectSql(dbType, qualifiedTable, columns)
                : buildSelectSql(dbType, qualifiedTable, columns) + " " + whereSql;
    }

    /** 行数统计 SQL */
    public static String buildCountSql(String qualifiedTable) {
        return "SELECT COUNT(*) FROM " + qualifiedTable;
    }

    /** 行数统计 SQL（增量模式：whereSql 为完整的 "WHERE ..." 条件或 null） */
    public static String buildCountSql(String qualifiedTable, String whereSql) {
        return whereSql == null ? buildCountSql(qualifiedTable) : buildCountSql(qualifiedTable) + " " + whereSql;
    }

    /**
     * 增量拉取条件：水位比较符按冲突策略选择。
     * <p>覆盖更新/跳过策略写入幂等，用 &gt;= 重放边界记录防止同值新写入漏采；
     * 报错中断策略不容忍重复投递，用 &gt; 严格递增。</p>
     */
    public static String buildIncWhere(DbTypeEnum dbType, String incColumn, boolean inclusive) {
        return "WHERE " + IdentifierUtils.quote(incColumn, dbType) + (inclusive ? " >= ?" : " > ?");
    }

    /**
     * 目标表写入 SQL（按冲突策略选择 INSERT / UPSERT 语法）
     *
     * @param dbType      目标库类型
     * @param table       目标表全限定名（已加引号）
     * @param columns     目标字段列表（与占位符顺序一致）
     * @param pkColumns   目标表主键字段列表（策略非 ERROR 时必须非空）
     * @param strategy    主键冲突策略
     */
    public static String buildWriteSql(DbTypeEnum dbType, String table, List<String> columns,
                                       List<String> pkColumns, ConflictStrategyEnum strategy) {
        String columnList = columns.stream()
                .map(c -> IdentifierUtils.quote(c, dbType))
                .collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        String insertHead = "INSERT INTO " + table + " (" + columnList + ") VALUES (" + placeholders + ")";

        return switch (dbType) {
            case MYSQL -> buildMySqlWrite(insertHead, columns, pkColumns, strategy, dbType);
            case ORACLE, DM -> buildMergeWrite(dbType, table, columns, pkColumns, strategy);
            case POSTGRESQL, KINGBASE -> buildPgWrite(insertHead, columns, pkColumns, strategy, dbType);
        };
    }

    /** MySQL 方言：ON DUPLICATE KEY UPDATE / INSERT IGNORE */
    private static String buildMySqlWrite(String insertHead, List<String> columns, List<String> pkColumns,
                                          ConflictStrategyEnum strategy, DbTypeEnum dbType) {
        if (strategy == ConflictStrategyEnum.ERROR) {
            return insertHead;
        }
        if (strategy == ConflictStrategyEnum.SKIP) {
            return insertHead.replaceFirst("INSERT INTO", "INSERT IGNORE INTO");
        }
        // 覆盖更新：主键冲突时更新全部非主键字段；若映射字段全部是主键则退化为跳过
        List<String> nonPk = columns.stream().filter(c -> !pkColumns.contains(c)).toList();
        if (nonPk.isEmpty()) {
            return insertHead.replaceFirst("INSERT INTO", "INSERT IGNORE INTO");
        }
        String updateClause = nonPk.stream()
                .map(c -> IdentifierUtils.quote(c, dbType) + "=VALUES(" + IdentifierUtils.quote(c, dbType) + ")")
                .collect(Collectors.joining(", "));
        return insertHead + " ON DUPLICATE KEY UPDATE " + updateClause;
    }

    /** Oracle / 达梦方言：MERGE INTO */
    private static String buildMergeWrite(DbTypeEnum dbType, String table, List<String> columns,
                                          List<String> pkColumns, ConflictStrategyEnum strategy) {
        if (strategy == ConflictStrategyEnum.ERROR) {
            String columnList = columns.stream().map(c -> IdentifierUtils.quote(c, dbType))
                    .collect(Collectors.joining(", "));
            String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
            return "INSERT INTO " + table + " (" + columnList + ") VALUES (" + placeholders + ")";
        }
        // USING 子查询中的占位符与字段一一对应
        String selectList = columns.stream()
                .map(c -> "? AS " + IdentifierUtils.quote(c, dbType))
                .collect(Collectors.joining(", "));
        // ON 条件：主键相等（占位符在 USING 中已提供，ON 引用别名即可）
        String onClause = pkColumns.stream()
                .map(pk -> "t." + IdentifierUtils.quote(pk, dbType) + " = s." + IdentifierUtils.quote(pk, dbType))
                .collect(Collectors.joining(" AND "));
        StringBuilder sql = new StringBuilder();
        sql.append("MERGE INTO ").append(table).append(" t ")
                .append("USING (SELECT ").append(selectList).append(" FROM dual) s ")
                .append("ON (").append(onClause).append(")");
        // 覆盖更新且存在非主键映射字段时才需要 UPDATE 分支（主键本身不可更新）
        List<String> nonPk = columns.stream().filter(c -> !pkColumns.contains(c)).toList();
        if (strategy == ConflictStrategyEnum.OVERWRITE && !nonPk.isEmpty()) {
            String updateList = nonPk.stream()
                    .map(c -> "t." + IdentifierUtils.quote(c, dbType) + " = s." + IdentifierUtils.quote(c, dbType))
                    .collect(Collectors.joining(", "));
            sql.append(" WHEN MATCHED THEN UPDATE SET ").append(updateList);
        }
        String insertColumns = columns.stream().map(c -> IdentifierUtils.quote(c, dbType))
                .collect(Collectors.joining(", "));
        String insertValues = columns.stream().map(c -> "s." + IdentifierUtils.quote(c, dbType))
                .collect(Collectors.joining(", "));
        sql.append(" WHEN NOT MATCHED THEN INSERT (").append(insertColumns)
                .append(") VALUES (").append(insertValues).append(")");
        return sql.toString();
    }

    /** PostgreSQL / 金仓方言：ON CONFLICT */
    private static String buildPgWrite(String insertHead, List<String> columns, List<String> pkColumns,
                                       ConflictStrategyEnum strategy, DbTypeEnum dbType) {
        if (strategy == ConflictStrategyEnum.ERROR) {
            return insertHead;
        }
        if (strategy == ConflictStrategyEnum.SKIP) {
            // 不指定冲突目标：任意唯一约束冲突均跳过
            return insertHead + " ON CONFLICT DO NOTHING";
        }
        List<String> nonPk = columns.stream().filter(c -> !pkColumns.contains(c)).toList();
        if (nonPk.isEmpty()) {
            return insertHead + " ON CONFLICT DO NOTHING";
        }
        String conflictTarget = pkColumns.stream().map(pk -> IdentifierUtils.quote(pk, dbType))
                .collect(Collectors.joining(", "));
        String updateList = nonPk.stream()
                .map(c -> IdentifierUtils.quote(c, dbType) + " = EXCLUDED." + IdentifierUtils.quote(c, dbType))
                .collect(Collectors.joining(", "));
        return insertHead + " ON CONFLICT (" + conflictTarget + ") DO UPDATE SET " + updateList;
    }

    /** 校验策略与主键的组合是否可执行（在构建 SQL 前调用，给出友好提示） */
    public static void checkStrategySupport(boolean targetHasPk, ConflictStrategyEnum strategy, String targetDisplay) {
        if (!targetHasPk && strategy != ConflictStrategyEnum.ERROR) {
            throw new BusinessException("目标表 [" + targetDisplay + "] 没有主键，无法执行【"
                    + strategy.getLabel() + "】策略，请为目标表设置主键，或将冲突策略改为【报错中断】");
        }
    }
}
