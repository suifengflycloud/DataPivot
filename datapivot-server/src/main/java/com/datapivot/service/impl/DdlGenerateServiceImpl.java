package com.datapivot.service.impl;

import com.datapivot.datasource.IdentifierUtils;
import com.datapivot.enums.DbTypeEnum;
import com.datapivot.enums.DiffTypeEnum;
import com.datapivot.service.DdlGenerateService;
import com.datapivot.service.support.DdlSqlBuilder;
import com.datapivot.vo.ColumnDiffVO;
import com.datapivot.vo.ColumnMetaVO;
import com.datapivot.vo.TableDiffVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DDL 升级脚本生成服务实现
 * <p>根据结构差异生成"使对比库向基准库对齐"的 DDL：
 * 新增表 -> CREATE TABLE；字段新增/修改/删除 -> ALTER TABLE ...；
 * 删除表仅生成注释建议（避免误删）；主键变更生成人工处理提示（各库改主键语法差异大且风险高）。</p>
 */
@Service
public class DdlGenerateServiceImpl implements DdlGenerateService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String generate(DbTypeEnum targetDbType, List<TableDiffVO> tableDiffs, String baseConnName,
                           String targetConnName) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- ============================================================\n");
        sb.append("-- DataPivot 结构对比 DDL 升级脚本\n");
        sb.append("-- 基准库: ").append(baseConnName).append("\n");
        sb.append("-- 对比库(执行目标): ").append(targetConnName)
                .append(" (").append(targetDbType.getLabel()).append(")\n");
        sb.append("-- 生成时间: ").append(LocalDateTime.now().format(TIME_FORMAT)).append("\n");
        sb.append("-- 注意: 1)请在对比库执行前做好备份; 2)默认在对比库当前 schema 下执行;\n");
        sb.append("--       3)少量特殊类型(枚举/空间类型等)请人工核对; 4)未包含默认值迁移\n");
        sb.append("-- ============================================================\n\n");

        if (tableDiffs.isEmpty()) {
            sb.append("-- 两个库的表结构完全一致，无需生成升级脚本。\n");
            return sb.toString();
        }

        int index = 0;
        for (TableDiffVO tableDiff : tableDiffs) {
            sb.append("-- ========== ").append(++index).append(". 表 [").append(tableDiff.getDisplayName())
                    .append("] (").append(tableDiff.getDiffTypeLabel()).append(") ==========\n");
            String tableQuoted = IdentifierUtils.quote(tableDiff.getTableName(), targetDbType);
            switch (DiffTypeEnum.valueOf(tableDiff.getDiffType())) {
                case TABLE_ADD -> appendCreateTable(sb, targetDbType, tableQuoted, tableDiff);
                case TABLE_DELETE -> sb.append("-- 对比库多余的表，确认无用后可执行删除:\n")
                        .append("-- DROP TABLE ").append(tableQuoted).append(";\n\n");
                case TABLE_MODIFY -> appendAlterTable(sb, targetDbType, tableQuoted, tableDiff);
                default -> {
                    // 不会出现
                }
            }
        }
        return sb.toString();
    }

    /** 新增表：CREATE TABLE + 注释语句 */
    private void appendCreateTable(StringBuilder sb, DbTypeEnum dbType, String tableQuoted, TableDiffVO diff) {
        sb.append(DdlSqlBuilder.buildCreateTable(dbType, tableQuoted, diff.getSourceColumns(),
                diff.getTableRemark())).append("\n");
        // 表注释（MySQL 已内联）
        String tableComment = DdlSqlBuilder.buildTableComment(dbType, tableQuoted, diff.getTableRemark());
        if (tableComment != null) {
            sb.append(tableComment).append("\n");
        }
        // 字段注释（非 MySQL 方言）
        if (dbType != DbTypeEnum.MYSQL) {
            for (ColumnMetaVO col : diff.getSourceColumns()) {
                String comment = DdlSqlBuilder.buildColumnComment(dbType, tableQuoted, col);
                if (comment != null) {
                    sb.append(comment).append("\n");
                }
            }
        }
        sb.append("\n");
    }

    /** 表级修改：按字段归组生成 ALTER 语句 */
    private void appendAlterTable(StringBuilder sb, DbTypeEnum dbType, String tableQuoted, TableDiffVO diff) {
        // 同一字段的多维差异合并处理：结构变化一条 MODIFY，注释单独 COMMENT ON
        Map<String, List<ColumnDiffVO>> byColumn = new LinkedHashMap<>();
        List<ColumnDiffVO> addDiffs = new ArrayList<>();
        List<ColumnDiffVO> dropDiffs = new ArrayList<>();
        for (ColumnDiffVO columnDiff : diff.getColumns()) {
            switch (DiffTypeEnum.valueOf(columnDiff.getDiffType())) {
                case COLUMN_ADD -> addDiffs.add(columnDiff);
                case COLUMN_DELETE -> dropDiffs.add(columnDiff);
                default -> byColumn.computeIfAbsent(columnDiff.getColumnName().toLowerCase(), k -> new ArrayList<>())
                        .add(columnDiff);
            }
        }
        // 1) 新增字段
        for (ColumnDiffVO addDiff : addDiffs) {
            sb.append(DdlSqlBuilder.buildAddColumn(dbType, tableQuoted, addDiff.getBaseColumn())).append("\n");
            if (dbType != DbTypeEnum.MYSQL) {
                String comment = DdlSqlBuilder.buildColumnComment(dbType, tableQuoted, addDiff.getBaseColumn());
                if (comment != null) {
                    sb.append(comment).append("\n");
                }
            }
        }
        // 2) 修改字段（类型/长度/可空性/注释，按字段合并）
        for (List<ColumnDiffVO> columnDiffs : byColumn.values()) {
            appendColumnModify(sb, dbType, tableQuoted, columnDiffs);
        }
        // 3) 删除字段
        for (ColumnDiffVO dropDiff : dropDiffs) {
            sb.append(DdlSqlBuilder.buildDropColumn(dbType, tableQuoted, dropDiff.getColumnName())).append("\n");
        }
        sb.append("\n");
    }

    /** 单字段的全部差异 -> DDL（结构变更一条语句覆盖；注释与主键提示单独输出） */
    private void appendColumnModify(StringBuilder sb, DbTypeEnum dbType, String tableQuoted,
                                    List<ColumnDiffVO> columnDiffs) {
        ColumnMetaVO baseColumn = columnDiffs.get(0).getBaseColumn();
        boolean structuralChange = columnDiffs.stream().anyMatch(d ->
                d.getDiffType().equals(DiffTypeEnum.COLUMN_TYPE.getCode())
                        || d.getDiffType().equals(DiffTypeEnum.COLUMN_LENGTH.getCode())
                        || d.getDiffType().equals(DiffTypeEnum.COLUMN_NULLABLE.getCode()));
        boolean commentChange = columnDiffs.stream()
                .anyMatch(d -> d.getDiffType().equals(DiffTypeEnum.COLUMN_COMMENT.getCode()));
        boolean pkChange = columnDiffs.stream()
                .anyMatch(d -> d.getDiffType().equals(DiffTypeEnum.COLUMN_PK.getCode()));

        if (structuralChange) {
            // PG 系：类型与可空性分语句；MySQL/Oracle/DM：完整列定义一次覆盖（MySQL 注释同时生效）
            DdlSqlBuilder.buildModifyColumn(dbType, tableQuoted, baseColumn)
                    .forEach(sql -> sb.append(sql).append("\n"));
            if ((dbType == DbTypeEnum.POSTGRESQL || dbType == DbTypeEnum.KINGBASE)
                    && columnDiffs.stream().anyMatch(d -> d.getDiffType()
                            .equals(DiffTypeEnum.COLUMN_NULLABLE.getCode()))) {
                DdlSqlBuilder.buildNullableChange(dbType, tableQuoted, baseColumn)
                        .forEach(sql -> sb.append(sql).append("\n"));
            }
        } else if (commentChange && dbType == DbTypeEnum.MYSQL) {
            // MySQL 注释变更必须通过完整列定义 MODIFY
            DdlSqlBuilder.buildModifyColumn(dbType, tableQuoted, baseColumn)
                    .forEach(sql -> sb.append(sql).append("\n"));
        }
        if (commentChange && dbType != DbTypeEnum.MYSQL) {
            String comment = DdlSqlBuilder.buildColumnComment(dbType, tableQuoted, baseColumn);
            if (comment != null) {
                sb.append(comment).append("\n");
            } else {
                // 基准库注释为空：生成清空注释语句
                sb.append("COMMENT ON COLUMN ").append(tableQuoted).append(".")
                        .append(IdentifierUtils.quote(baseColumn.getColumnName(), dbType))
                        .append(" IS NULL;\n");
            }
        }
        if (pkChange) {
            sb.append("-- 主键变更（字段 ").append(columnDiffs.get(0).getColumnName())
                    .append("）：各库改主键语法差异较大且涉及数据约束，请人工评估后处理\n");
        }
    }
}
