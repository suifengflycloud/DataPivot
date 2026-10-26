package com.datapivot.service.impl;

import com.datapivot.common.BusinessException;
import com.datapivot.datasource.DynamicDataSourceManager;
import com.datapivot.entity.DbConnection;
import com.datapivot.enums.DbTypeEnum;
import com.datapivot.service.MetadataService;
import com.datapivot.service.support.ValueConverter;
import com.datapivot.vo.ColumnMetaVO;
import com.datapivot.vo.TableMetaVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 元数据读取服务实现
 * <p>严格使用 JDBC DatabaseMetaData 标准 API（getTables / getColumns / getPrimaryKeys），
 * 不查询任何数据库系统表/视图，天然规避各方言差异。</p>
 * <p>各库 catalog/schema 定位规则：</p>
 * <ul>
 *   <li>MySQL：catalog = 当前库，schema = null</li>
 *   <li>Oracle / 达梦：schema = 当前会话 schema（取不到时回退为用户名大写）</li>
 *   <li>PostgreSQL / 金仓：schema = 当前会话 schema（通常为 public）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataServiceImpl implements MetadataService {

    private final DynamicDataSourceManager dataSourceManager;

    @Override
    public List<TableMetaVO> listTables(DbConnection conn) {
        DbTypeEnum dbType = DbTypeEnum.fromCode(conn.getDbType());
        try (Connection connection = dataSourceManager.getConnection(conn)) {
            DatabaseMetaData metaData = connection.getMetaData();
            String[] location = resolveLocation(connection, conn, dbType);
            List<TableMetaVO> tables = new ArrayList<>();
            // 只拉取 TABLE 类型（排除视图/系统表）
            try (ResultSet rs = metaData.getTables(location[0], location[1], "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    // Oracle 回收站对象（BIN$xxx）过滤
                    if (dbType == DbTypeEnum.ORACLE && tableName != null && tableName.startsWith("BIN$")) {
                        continue;
                    }
                    // getTables 的 catalog/schema 参数在部分驱动中按"模式"匹配，需精确过滤防误匹配
                    if (!exactMatch(rs.getString("TABLE_CAT"), location[0])
                            || !exactMatch(rs.getString("TABLE_SCHEM"), location[1])) {
                        continue;
                    }
                    TableMetaVO vo = new TableMetaVO();
                    vo.setCatalog(rs.getString("TABLE_CAT"));
                    vo.setSchema(rs.getString("TABLE_SCHEM"));
                    vo.setTableName(tableName);
                    vo.setDisplayName(buildDisplayName(vo));
                    vo.setRemarks(rs.getString("REMARKS"));
                    tables.add(vo);
                }
            }
            tables.sort(Comparator.comparing(TableMetaVO::getTableName, String.CASE_INSENSITIVE_ORDER));
            return tables;
        } catch (SQLException e) {
            log.info("读取表列表失败: {}", e.getMessage());
            throw new BusinessException("读取表列表失败: " + e.getMessage());
        }
    }

    @Override
    public List<ColumnMetaVO> listColumns(DbConnection conn, String catalog, String schema, String tableName) {
        DbTypeEnum dbType = DbTypeEnum.fromCode(conn.getDbType());
        // 未显式传定位信息时按连接默认规则解析
        try (Connection connection = dataSourceManager.getConnection(conn)) {
            if (!StringUtils.hasText(catalog) && !StringUtils.hasText(schema)) {
                String[] location = resolveLocation(connection, conn, dbType);
                catalog = location[0];
                schema = location[1];
            }
            DatabaseMetaData metaData = connection.getMetaData();

            // 1. 主键字段集合
            Set<String> pkColumns = new HashSet<>();
            try (ResultSet pk = metaData.getPrimaryKeys(catalog, schema, tableName)) {
                while (pk.next()) {
                    pkColumns.add(pk.getString("COLUMN_NAME"));
                }
            }

            // 2. 字段明细
            List<ColumnMetaVO> columns = new ArrayList<>();
            try (ResultSet rs = metaData.getColumns(catalog, schema, tableName, "%")) {
                while (rs.next()) {
                    // 防止表名/库名中的下划线被当作模式通配符而误匹配其他表
                    if (!tableName.equals(rs.getString("TABLE_NAME"))
                            || !exactMatch(rs.getString("TABLE_CAT"), catalog)
                            || !exactMatch(rs.getString("TABLE_SCHEM"), schema)) {
                        continue;
                    }
                    ColumnMetaVO vo = new ColumnMetaVO();
                    vo.setColumnName(rs.getString("COLUMN_NAME"));
                    vo.setTypeName(rs.getString("TYPE_NAME"));
                    int dataType = rs.getInt("DATA_TYPE");
                    vo.setDataType(dataType);
                    vo.setTypeCategory(ValueConverter.categoryOf(dataType).name());
                    vo.setSize(rs.getInt("COLUMN_SIZE"));
                    vo.setDigits(rs.getInt("DECIMAL_DIGITS"));
                    vo.setNullable(!"NO".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                    vo.setPk(pkColumns.contains(vo.getColumnName()));
                    vo.setDefaultValue(rs.getString("COLUMN_DEF"));
                    vo.setRemarks(rs.getString("REMARKS"));
                    columns.add(vo);
                }
            }
            return columns;
        } catch (SQLException e) {
            log.info("读取字段信息失败: {}", e.getMessage());
            throw new BusinessException("读取表 [" + tableName + "] 字段信息失败: " + e.getMessage());
        }
    }

    /**
     * 解析当前库的 catalog/schema 定位（数组：[catalog, schema]，不适用者为 null）
     */
    private String[] resolveLocation(Connection connection, DbConnection conn, DbTypeEnum dbType)
            throws SQLException {
        return switch (dbType) {
            case MYSQL -> new String[]{connection.getCatalog(), null};
            case ORACLE -> new String[]{null, firstNonBlank(safeGetSchema(connection),
                    conn.getUsername().toUpperCase())};
            case DM -> new String[]{null, firstNonBlank(safeGetSchema(connection),
                    StringUtils.hasText(conn.getDbName()) ? conn.getDbName() : conn.getUsername())};
            case POSTGRESQL, KINGBASE -> new String[]{null, firstNonBlank(safeGetSchema(connection), "public")};
        };
    }

    /**
     * 读取会话 schema：个别老驱动未实现 getSchema（抛 AbstractMethodError），
     * 此处兜底返回 null 交由调用方回退。
     */
    private String safeGetSchema(Connection connection) {
        try {
            return connection.getSchema();
        } catch (Throwable e) {
            return null;
        }
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** 模式参数精确匹配校验（期望值为 null 时视为通过） */
    private boolean exactMatch(String actual, String expected) {
        if (expected == null) {
            return true;
        }
        return expected.equals(actual);
    }

    private String buildDisplayName(TableMetaVO vo) {
        return StringUtils.hasText(vo.getSchema())
                ? vo.getSchema() + "." + vo.getTableName() : vo.getTableName();
    }
}
