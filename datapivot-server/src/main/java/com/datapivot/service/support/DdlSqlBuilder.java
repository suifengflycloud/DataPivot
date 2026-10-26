package com.datapivot.service.support;

import com.datapivot.datasource.IdentifierUtils;
import com.datapivot.enums.DbTypeEnum;
import com.datapivot.vo.ColumnMetaVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DDL 语句构建器：按目标库方言生成 CREATE TABLE / ALTER TABLE 等结构升级语句。
 * <p>用于结构对比模块，输出"使对比库向基准库对齐"的 DDL 脚本。</p>
 * <p>方言要点：</p>
 * <ul>
 *   <li>MySQL：注释内联在列定义上，MODIFY COLUMN 需带完整列定义</li>
 *   <li>Oracle / 达梦：注释用 COMMENT ON，修改列用 MODIFY (列定义)</li>
 *   <li>PostgreSQL / 金仓：注释用 COMMENT ON，改类型用 ALTER COLUMN TYPE，可空性单独 SET/DROP NOT NULL</li>
 * </ul>
 */
public final class DdlSqlBuilder {

    private DdlSqlBuilder() {
    }

    /**
     * 生成 CREATE TABLE（含主键约束；Oracle/DM/PG/金仓的注释单独生成）
     *
     * @param tableQuoted 已加引号的表名
     * @param columns     基准库字段列表
     * @param tableRemark 表注释（可空）
     */
    public static String buildCreateTable(DbTypeEnum dbType, String tableQuoted, List<ColumnMetaVO> columns,
                                          String tableRemark) {
        List<String> pkColumns = columns.stream()
                .filter(c -> Boolean.TRUE.equals(c.getPk()))
                .map(ColumnMetaVO::getColumnName)
                .toList();
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(tableQuoted).append(" (\n");
        String body = columns.stream()
                .map(col -> "  " + columnDefinition(dbType, col, true))
                .collect(Collectors.joining(",\n"));
        sb.append(body);
        if (!pkColumns.isEmpty()) {
            String pk = pkColumns.stream().map(c -> IdentifierUtils.quote(c, dbType))
                    .collect(Collectors.joining(", "));
            sb.append(",\n  PRIMARY KEY (").append(pk).append(")");
        }
        sb.append("\n)");
        if (dbType == DbTypeEnum.MYSQL && tableRemark != null && !tableRemark.isBlank()) {
            sb.append(" COMMENT='").append(tableRemark.replace("'", "''")).append("'");
        }
        sb.append(";");
        return sb.toString();
    }

    /** 生成表注释语句（MySQL 已内联在 CREATE TABLE，返回 null） */
    public static String buildTableComment(DbTypeEnum dbType, String tableQuoted, String remark) {
        if (dbType == DbTypeEnum.MYSQL || remark == null || remark.isBlank()) {
            return null;
        }
        return "COMMENT ON TABLE " + tableQuoted + " IS '" + remark.replace("'", "''") + "';";
    }

    /** 生成 ALTER TABLE ADD COLUMN */
    public static String buildAddColumn(DbTypeEnum dbType, String tableQuoted, ColumnMetaVO col) {
        String definition = columnDefinition(dbType, col, true);
        return switch (dbType) {
            case MYSQL -> "ALTER TABLE " + tableQuoted + " ADD COLUMN " + definition + ";";
            case ORACLE, DM -> "ALTER TABLE " + tableQuoted + " ADD (" + definition + ");";
            case POSTGRESQL, KINGBASE -> "ALTER TABLE " + tableQuoted + " ADD COLUMN " + definition + ";";
        };
    }

    /**
     * 生成"完整列定义"式修改语句（类型/长度/可空性变更）。
     * PG 系使用 ALTER COLUMN TYPE，其余使用 MODIFY。
     */
    public static List<String> buildModifyColumn(DbTypeEnum dbType, String tableQuoted, ColumnMetaVO col) {
        String definition = columnDefinition(dbType, col, true);
        String quoted = IdentifierUtils.quote(col.getColumnName(), dbType);
        return switch (dbType) {
            case MYSQL -> List.of("ALTER TABLE " + tableQuoted + " MODIFY COLUMN " + definition + ";");
            case ORACLE, DM -> List.of("ALTER TABLE " + tableQuoted + " MODIFY (" + definition + ");");
            case POSTGRESQL, KINGBASE -> List.of("ALTER TABLE " + tableQuoted + " ALTER COLUMN " + quoted
                    + " TYPE " + renderType(dbType, col) + ";");
        };
    }

    /** 生成可空性变更语句（仅可空性变化时使用；MySQL 需完整列定义） */
    public static List<String> buildNullableChange(DbTypeEnum dbType, String tableQuoted, ColumnMetaVO col) {
        String quoted = IdentifierUtils.quote(col.getColumnName(), dbType);
        boolean nullable = Boolean.TRUE.equals(col.getNullable());
        return switch (dbType) {
            case MYSQL -> List.of("ALTER TABLE " + tableQuoted + " MODIFY COLUMN "
                    + columnDefinition(dbType, col, false) + ";");
            case ORACLE, DM -> List.of("ALTER TABLE " + tableQuoted + " MODIFY (" + quoted
                    + (nullable ? " NULL" : " NOT NULL") + ");");
            case POSTGRESQL, KINGBASE -> List.of("ALTER TABLE " + tableQuoted + " ALTER COLUMN " + quoted
                    + (nullable ? " DROP NOT NULL;" : " SET NOT NULL;"));
        };
    }

    /** 生成 ALTER TABLE DROP COLUMN */
    public static String buildDropColumn(DbTypeEnum dbType, String tableQuoted, String columnName) {
        return "ALTER TABLE " + tableQuoted + " DROP COLUMN "
                + IdentifierUtils.quote(columnName, dbType) + ";";
    }

    /** 生成字段注释语句（MySQL 无独立注释语法，返回 null） */
    public static String buildColumnComment(DbTypeEnum dbType, String tableQuoted, ColumnMetaVO col) {
        if (dbType == DbTypeEnum.MYSQL || col.getRemarks() == null || col.getRemarks().isBlank()) {
            return null;
        }
        return "COMMENT ON COLUMN " + tableQuoted + "."
                + IdentifierUtils.quote(col.getColumnName(), dbType)
                + " IS '" + col.getRemarks().replace("'", "''") + "';";
    }

    /**
     * 组装列定义：`名` 类型 [NOT NULL] [COMMENT 'xx'（仅 MySQL）]
     */
    private static String columnDefinition(DbTypeEnum dbType, ColumnMetaVO col, boolean withComment) {
        String quoted = IdentifierUtils.quote(col.getColumnName(), dbType);
        StringBuilder sb = new StringBuilder(quoted);
        sb.append(" ").append(renderType(dbType, col));
        // 主键字段与 NOT NULL 字段均输出 NOT NULL，其余输出 NULL（显式语义更直观）
        boolean notNull = Boolean.FALSE.equals(col.getNullable()) || Boolean.TRUE.equals(col.getPk());
        sb.append(notNull ? " NOT NULL" : " NULL");
        if (withComment && dbType == DbTypeEnum.MYSQL && col.getRemarks() != null && !col.getRemarks().isBlank()) {
            sb.append(" COMMENT '").append(col.getRemarks().replace("'", "''")).append("'");
        }
        return sb.toString();
    }

    /**
     * 按目标方言渲染字段类型：先将各库原生类型名归一到"类型家族"，再按目标方言输出。
     * <p>元数据中的 COLUMN_SIZE 语义随类型而异（字符长度/精度/显示宽度），
     * 仅对字符与精确数值类型附加 (n) / (p,s) 参数。</p>
     */
    public static String renderType(DbTypeEnum dbType, ColumnMetaVO col) {
        String raw = col.getTypeName() == null ? "" : col.getTypeName().trim().toUpperCase();
        int size = col.getSize() == null ? 0 : col.getSize();
        int digits = col.getDigits() == null ? 0 : col.getDigits();
        String family = typeFamily(raw, size);

        switch (family) {
            case "VARCHAR":
                // 超长字符按大文本处理
                if (size > 4000 && (dbType == DbTypeEnum.ORACLE || dbType == DbTypeEnum.DM)) {
                    return "CLOB";
                }
                if (size > 65535 && dbType == DbTypeEnum.MYSQL) {
                    return "TEXT";
                }
                if (size > 10485760 && (dbType == DbTypeEnum.POSTGRESQL || dbType == DbTypeEnum.KINGBASE)) {
                    return "text";
                }
                int len = size > 0 ? size : 255;
                return (dbType == DbTypeEnum.ORACLE || dbType == DbTypeEnum.DM)
                        ? "VARCHAR2(" + len + ")" : "varchar(" + len + ")";
            case "CHAR":
                return (dbType == DbTypeEnum.ORACLE || dbType == DbTypeEnum.DM)
                        ? "CHAR(" + Math.max(size, 1) + ")" : "char(" + Math.max(size, 1) + ")";
            case "TEXT":
                return switch (dbType) {
                    case MYSQL -> "TEXT";
                    case ORACLE, DM -> "CLOB";
                    default -> "text";
                };
            case "DECIMAL":
                if (size <= 0) {
                    return (dbType == DbTypeEnum.ORACLE || dbType == DbTypeEnum.DM) ? "NUMBER" : "numeric";
                }
                if (dbType == DbTypeEnum.ORACLE || dbType == DbTypeEnum.DM) {
                    return "NUMBER(" + size + "," + digits + ")";
                }
                return "decimal(" + size + "," + digits + ")";
            case "INTEGER":
                return (dbType == DbTypeEnum.ORACLE || dbType == DbTypeEnum.DM) ? "NUMBER(10)" : "integer";
            case "BIGINT":
                return (dbType == DbTypeEnum.ORACLE || dbType == DbTypeEnum.DM) ? "NUMBER(19)" : "bigint";
            case "SMALLINT":
                return (dbType == DbTypeEnum.ORACLE || dbType == DbTypeEnum.DM) ? "NUMBER(5)" : "smallint";
            case "TINYINT":
                return (dbType == DbTypeEnum.ORACLE || dbType == DbTypeEnum.DM) ? "NUMBER(3)" : "smallint";
            case "FLOAT":
                return switch (dbType) {
                    case MYSQL -> "float";
                    case ORACLE, DM -> "BINARY_FLOAT";
                    default -> "real";
                };
            case "DOUBLE":
                return switch (dbType) {
                    case MYSQL -> "double";
                    case ORACLE, DM -> "BINARY_DOUBLE";
                    default -> "double precision";
                };
            case "DATE":
                return dbType == DbTypeEnum.MYSQL ? "date" : (dbType == DbTypeEnum.POSTGRESQL || dbType == DbTypeEnum.KINGBASE) ? "date" : "DATE";
            case "TIME":
                return (dbType == DbTypeEnum.ORACLE) ? "TIMESTAMP" : (dbType == DbTypeEnum.DM) ? "TIME" : "time";
            case "DATETIME":
                return switch (dbType) {
                    case MYSQL -> "datetime";
                    case ORACLE -> "DATE";
                    case DM -> "TIMESTAMP";
                    default -> "timestamp";
                };
            case "TIMESTAMP":
                // 仅当元数据精度在 1-9 之间时附加精度（MySQL 的 19/26 为显示宽度，不追加）
                return (size > 0 && size <= 9) ? "timestamp(" + size + ")" : "timestamp";
            case "BINARY":
                return switch (dbType) {
                    case MYSQL -> "blob";
                    case ORACLE, DM -> "BLOB";
                    default -> "bytea";
                };
            case "BOOLEAN":
                return switch (dbType) {
                    case MYSQL -> "tinyint(1)";
                    case ORACLE -> "NUMBER(1)";
                    case DM -> "BIT";
                    default -> "boolean";
                };
            default:
                // 未知/自定义类型原样输出（如 GIS、enum 等，需人工核对）
                return raw;
        }
    }

    /**
     * 原生类型名 -> 类型家族（忽略各库命名差异）
     */
    private static String typeFamily(String typeName, int size) {
        switch (typeName) {
            case "VARCHAR", "VARCHAR2", "NVARCHAR", "NVARCHAR2", "CHARACTER VARYING", "NCHAR VARYING":
                return "VARCHAR";
            case "CHAR", "NCHAR", "CHARACTER":
                return "CHAR";
            case "TEXT", "CLOB", "NCLOB", "LONGVARCHAR", "LONG", "TINYTEXT", "MEDIUMTEXT", "LONGTEXT":
                return "TEXT";
            case "NUMBER", "NUMERIC", "DECIMAL", "DEC":
                return "DECIMAL";
            case "INTEGER", "INT", "INT4", "SERIAL", "MEDIUMINT":
                return "INTEGER";
            case "BIGINT", "INT8", "BIGSERIAL":
                return "BIGINT";
            case "SMALLINT", "INT2", "SMALLSERIAL":
                return "SMALLINT";
            case "TINYINT", "INT1":
                return "TINYINT";
            case "FLOAT", "BINARY_FLOAT", "REAL", "FLOAT4":
                return "FLOAT";
            case "DOUBLE", "DOUBLE PRECISION", "BINARY_DOUBLE", "FLOAT8":
                return "DOUBLE";
            case "DATE":
                return "DATE";
            case "TIME", "TIME WITH TIME ZONE", "TIMETZ":
                return "TIME";
            case "DATETIME":
                return "DATETIME";
            case "TIMESTAMP", "TIMESTAMP WITH TIME ZONE", "TIMESTAMPTZ", "TIMESTAMP WITH LOCAL TIME ZONE":
                return "TIMESTAMP";
            case "BLOB", "BYTEA", "BINARY", "VARBINARY", "LONGVARBINARY", "RAW", "LONG RAW", "TINYBLOB",
                    "MEDIUMBLOB", "LONGBLOB", "IMAGE":
                return "BINARY";
            case "BOOLEAN", "BOOL":
                return "BOOLEAN";
            case "BIT":
                // BIT(1) 视为布尔，多 bit 视为二进制
                return size <= 1 ? "BOOLEAN" : "BINARY";
            default:
                return "OTHER";
        }
    }
}
