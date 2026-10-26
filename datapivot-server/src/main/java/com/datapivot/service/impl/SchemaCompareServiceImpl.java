package com.datapivot.service.impl;

import com.datapivot.entity.DbConnection;
import com.datapivot.enums.DbTypeEnum;
import com.datapivot.enums.DiffTypeEnum;
import com.datapivot.service.DbConnectionService;
import com.datapivot.service.DdlGenerateService;
import com.datapivot.service.MetadataService;
import com.datapivot.service.SchemaCompareService;
import com.datapivot.service.support.DdlSqlBuilder;
import com.datapivot.vo.ColumnDiffVO;
import com.datapivot.vo.ColumnMetaVO;
import com.datapivot.vo.CompareResultVO;
import com.datapivot.vo.CompareSummaryVO;
import com.datapivot.vo.TableDiffVO;
import com.datapivot.vo.TableMetaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据库结构对比服务实现
 * <p>只对比结构元信息（表/字段），不触碰业务数据。对比方向：使"对比库"向"基准库"对齐。</p>
 */
@Service
@RequiredArgsConstructor
public class SchemaCompareServiceImpl implements SchemaCompareService {

    private final DbConnectionService connectionService;
    private final MetadataService metadataService;
    private final DdlGenerateService ddlGenerateService;

    @Override
    public CompareResultVO compare(Long baseConnId, Long targetConnId) {
        DbConnection baseConn = connectionService.getByIdNotNull(baseConnId);
        DbConnection targetConn = connectionService.getByIdNotNull(targetConnId);
        DbTypeEnum baseType = DbTypeEnum.fromCode(baseConn.getDbType());
        DbTypeEnum targetType = DbTypeEnum.fromCode(targetConn.getDbType());

        // 1. 拉取两端表清单（表名按小写归一匹配，兼容 MySQL->Oracle 等大小写折叠差异）
        List<TableMetaVO> baseTables = metadataService.listTables(baseConn);
        List<TableMetaVO> targetTables = metadataService.listTables(targetConn);
        Map<String, TableMetaVO> baseMap = baseTables.stream()
                .collect(Collectors.toMap(t -> t.getTableName().toLowerCase(), Function.identity(), (a, b) -> a));
        Map<String, TableMetaVO> targetMap = targetTables.stream()
                .collect(Collectors.toMap(t -> t.getTableName().toLowerCase(), Function.identity(), (a, b) -> a));

        // 2. 求并集并逐表对比
        TreeSet<String> allTableNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        allTableNames.addAll(baseMap.keySet());
        allTableNames.addAll(targetMap.keySet());

        CompareSummaryVO summary = new CompareSummaryVO();
        List<TableDiffVO> tableDiffs = new ArrayList<>();
        for (String nameKey : allTableNames) {
            TableMetaVO baseTable = baseMap.get(nameKey);
            TableMetaVO targetTable = targetMap.get(nameKey);
            if (baseTable != null && targetTable == null) {
                // 基准库有、对比库无：新增表（需在对比库建表）
                tableDiffs.add(buildAddTableDiff(baseTable, baseConn));
                summary.setAddedTables(summary.getAddedTables() + 1);
                summary.setTotalDiffItems(summary.getTotalDiffItems() + 1);
            } else if (baseTable == null) {
                // 对比库有、基准库无：删除表
                tableDiffs.add(buildDeleteTableDiff(targetTable));
                summary.setDeletedTables(summary.getDeletedTables() + 1);
                summary.setTotalDiffItems(summary.getTotalDiffItems() + 1);
            } else {
                // 两端都有：字段级对比
                List<ColumnDiffVO> columnDiffs = compareColumns(baseTable, targetTable, baseConn, targetConn);
                if (!columnDiffs.isEmpty()) {
                    TableDiffVO diff = new TableDiffVO();
                    diff.setTableName(targetTable.getTableName());
                    diff.setDisplayName(targetTable.getDisplayName());
                    diff.setDiffType(DiffTypeEnum.TABLE_MODIFY.getCode());
                    diff.setDiffTypeLabel(DiffTypeEnum.TABLE_MODIFY.getLabel());
                    diff.setColumns(columnDiffs);
                    tableDiffs.add(diff);
                    summary.setChangedTables(summary.getChangedTables() + 1);
                    summary.setTotalDiffItems(summary.getTotalDiffItems() + columnDiffs.size());
                }
            }
        }

        // 3. 组装结果并生成 DDL 脚本
        CompareResultVO result = new CompareResultVO();
        result.setBaseConnName(baseConn.getConnName());
        result.setTargetConnName(targetConn.getConnName());
        result.setTargetDbType(targetType.getCode());
        result.setTypeMismatch(baseType != targetType);
        result.setSummary(summary);
        result.setTableDiffs(tableDiffs);
        result.setDdlScript(ddlGenerateService.generate(targetType, tableDiffs,
                baseConn.getConnName(), targetConn.getConnName()));
        return result;
    }

    /** 新增表差异（携带建表所需全部字段元数据） */
    private TableDiffVO buildAddTableDiff(TableMetaVO baseTable, DbConnection baseConn) {
        TableDiffVO diff = new TableDiffVO();
        diff.setTableName(baseTable.getTableName());
        diff.setDisplayName(baseTable.getDisplayName());
        diff.setDiffType(DiffTypeEnum.TABLE_ADD.getCode());
        diff.setDiffTypeLabel(DiffTypeEnum.TABLE_ADD.getLabel());
        diff.setSourceColumns(metadataService.listColumns(baseConn,
                baseTable.getCatalog(), baseTable.getSchema(), baseTable.getTableName()));
        diff.setTableRemark(baseTable.getRemarks());
        return diff;
    }

    /** 删除表差异 */
    private TableDiffVO buildDeleteTableDiff(TableMetaVO targetTable) {
        TableDiffVO diff = new TableDiffVO();
        diff.setTableName(targetTable.getTableName());
        diff.setDisplayName(targetTable.getDisplayName());
        diff.setDiffType(DiffTypeEnum.TABLE_DELETE.getCode());
        diff.setDiffTypeLabel(DiffTypeEnum.TABLE_DELETE.getLabel());
        return diff;
    }

    /**
     * 字段级对比：新增/删除字段 + 类型/长度/可空/主键/注释变更
     */
    private List<ColumnDiffVO> compareColumns(TableMetaVO baseTable, TableMetaVO targetTable,
                                              DbConnection baseConn, DbConnection targetConn) {
        List<ColumnMetaVO> baseColumns = metadataService.listColumns(baseConn,
                baseTable.getCatalog(), baseTable.getSchema(), baseTable.getTableName());
        List<ColumnMetaVO> targetColumns = metadataService.listColumns(targetConn,
                targetTable.getCatalog(), targetTable.getSchema(), targetTable.getTableName());
        Map<String, ColumnMetaVO> baseMap = baseColumns.stream()
                .collect(Collectors.toMap(c -> c.getColumnName().toLowerCase(), Function.identity(), (a, b) -> a));
        Map<String, ColumnMetaVO> targetMap = targetColumns.stream()
                .collect(Collectors.toMap(c -> c.getColumnName().toLowerCase(), Function.identity(), (a, b) -> a));

        List<ColumnDiffVO> diffs = new ArrayList<>();
        TreeSet<String> allColumns = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        allColumns.addAll(baseMap.keySet());
        allColumns.addAll(targetMap.keySet());
        for (String columnKey : allColumns) {
            ColumnMetaVO baseCol = baseMap.get(columnKey);
            ColumnMetaVO targetCol = targetMap.get(columnKey);
            if (baseCol != null && targetCol == null) {
                diffs.add(buildColumnDiff(DiffTypeEnum.COLUMN_ADD, baseCol.getColumnName(),
                        typeLabel(baseCol), null, "新增字段 " + baseCol.getColumnName()
                                + " " + typeLabel(baseCol), baseCol));
            } else if (baseCol == null) {
                diffs.add(buildColumnDiff(DiffTypeEnum.COLUMN_DELETE, targetCol.getColumnName(),
                        typeLabel(targetCol), null, "删除字段 " + targetCol.getColumnName()
                                + " " + typeLabel(targetCol), null));
            } else {
                diffs.addAll(compareOneColumn(baseCol, targetCol));
            }
        }
        return diffs;
    }

    /** 单字段五维对比：类型 / 长度 / 可空 / 主键 / 注释 */
    private List<ColumnDiffVO> compareOneColumn(ColumnMetaVO baseCol, ColumnMetaVO targetCol) {
        List<ColumnDiffVO> diffs = new ArrayList<>();
        String columnName = targetCol.getColumnName();
        String baseType = typeLabel(baseCol);
        String targetType = typeLabel(targetCol);
        // 类型对比仅比较"类型家族"（剔除长度参数），长度差异单独维度呈现
        if (!stripParams(baseType).equals(stripParams(targetType))) {
            diffs.add(buildColumnDiff(DiffTypeEnum.COLUMN_TYPE, columnName, targetType, baseType,
                    "字段 " + columnName + " 类型变更: " + targetType + " → " + baseType, baseCol));
        } else if (!lengthLabel(baseCol).equals(lengthLabel(targetCol))) {
            diffs.add(buildColumnDiff(DiffTypeEnum.COLUMN_LENGTH, columnName,
                    lengthLabel(targetCol), lengthLabel(baseCol),
                    "字段 " + columnName + " 长度变更: " + lengthLabel(targetCol) + " → " + lengthLabel(baseCol),
                    baseCol));
        }
        // 可空性变更
        boolean baseNullable = !Boolean.FALSE.equals(baseCol.getNullable()) && !Boolean.TRUE.equals(baseCol.getPk());
        boolean targetNullable = !Boolean.FALSE.equals(targetCol.getNullable()) && !Boolean.TRUE.equals(targetCol.getPk());
        if (baseNullable != targetNullable) {
            diffs.add(buildColumnDiff(DiffTypeEnum.COLUMN_NULLABLE, columnName,
                    nullableLabel(targetCol), nullableLabel(baseCol),
                    "字段 " + columnName + " 可空性变更: " + nullableLabel(targetCol) + " → " + nullableLabel(baseCol),
                    baseCol));
        }
        // 主键变更
        boolean basePk = Boolean.TRUE.equals(baseCol.getPk());
        boolean targetPk = Boolean.TRUE.equals(targetCol.getPk());
        if (basePk != targetPk) {
            diffs.add(buildColumnDiff(DiffTypeEnum.COLUMN_PK, columnName,
                    pkLabel(targetCol), pkLabel(baseCol),
                    "字段 " + columnName + " 主键变更: " + pkLabel(targetCol) + " → " + pkLabel(baseCol), baseCol));
        }
        // 注释变更
        String baseRemark = StringUtils.hasText(baseCol.getRemarks()) ? baseCol.getRemarks().trim() : "";
        String targetRemark = StringUtils.hasText(targetCol.getRemarks()) ? targetCol.getRemarks().trim() : "";
        if (!baseRemark.equals(targetRemark)) {
            diffs.add(buildColumnDiff(DiffTypeEnum.COLUMN_COMMENT, columnName,
                    targetRemark, baseRemark,
                    "字段 " + columnName + " 注释变更: [" + targetRemark + "] → [" + baseRemark + "]", baseCol));
        }
        return diffs;
    }

    private ColumnDiffVO buildColumnDiff(DiffTypeEnum type, String columnName, String before, String after,
                                         String description, ColumnMetaVO baseColumn) {
        ColumnDiffVO vo = new ColumnDiffVO();
        vo.setColumnName(columnName);
        vo.setDiffType(type.getCode());
        vo.setDiffTypeLabel(type.getLabel());
        vo.setBefore(before);
        vo.setAfter(after);
        vo.setDescription(description);
        vo.setBaseColumn(baseColumn);
        return vo;
    }

    /** 类型名归一（大写 + 剔除长度参数），用于跨库类型家族差异判断 */
    private String stripParams(String renderedType) {
        return renderedType.replaceAll("\\(\\d+(,\\d+)?\\)", "").replace(" ", "").toUpperCase();
    }

    /** 类型 + 长度展示 */
    private String typeLabel(ColumnMetaVO col) {
        return DdlSqlBuilder.renderType(DbTypeEnum.POSTGRESQL, col);
    }

    /** 长度标签：精确数值型展示 (精度,小数位)，字符型展示长度，时间戳展示精度（1-9），其余无意义 */
    private String lengthLabel(ColumnMetaVO col) {
        String family = familyOf(col);
        int size = nz(col.getSize());
        switch (family) {
            case "DECIMAL":
                return "(" + size + "," + nz(col.getDigits()) + ")";
            case "VARCHAR":
            case "CHAR":
                return String.valueOf(size);
            case "TIMESTAMP":
                return size > 0 && size <= 9 ? String.valueOf(size) : "-";
            default:
                // 长度对该类类型无业务含义，统一视为一致
                return "-";
        }
    }

    private String familyOf(ColumnMetaVO col) {
        String rendered = typeLabel(col).toUpperCase();
        if (rendered.startsWith("VARCHAR")) {
            return "VARCHAR";
        }
        if (rendered.startsWith("CHAR")) {
            return "CHAR";
        }
        if (rendered.startsWith("DECIMAL") || rendered.startsWith("NUMERIC")) {
            return "DECIMAL";
        }
        if (rendered.startsWith("TIMESTAMP")) {
            return "TIMESTAMP";
        }
        return "OTHER";
    }

    private String nullableLabel(ColumnMetaVO col) {
        return Boolean.FALSE.equals(col.getNullable()) ? "NOT NULL" : "NULL";
    }

    private String pkLabel(ColumnMetaVO col) {
        return Boolean.TRUE.equals(col.getPk()) ? "主键" : "非主键";
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
