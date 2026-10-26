package com.datapivot.datasource;

import com.datapivot.common.BusinessException;
import com.datapivot.enums.DbTypeEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 标识符安全工具：所有会进入 SQL 语句的表名/字段名必须先通过本工具校验与转义。
 * <p>安全策略：
 * <ol>
 *   <li>白名单字符校验（字母/数字/下划线/$/#/中文），从源头阻断 SQL 注入；</li>
 *   <li>按方言统一加引号（MySQL 反引号、其余双引号），并对标识符内部的引号做 doubling 转义；</li>
 *   <li>拼接的值一律走 PreparedStatement 占位符，本工具绝不处理"值"。</li>
 * </ol>
 * </p>
 */
public final class IdentifierUtils {

    /** 合法标识符白名单：字母、数字、下划线、$、#、中文，长度 1-200 */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9_$#\\u4e00-\\u9fa5]{1,200}$");

    private IdentifierUtils() {
    }

    /** 校验标识符合法性 */
    public static boolean isValidIdentifier(String name) {
        return name != null && SAFE_IDENTIFIER.matcher(name).matches();
    }

    /** 校验标识符，非法直接抛业务异常 */
    public static void checkIdentifier(String name, String what) {
        if (!isValidIdentifier(name)) {
            throw new BusinessException(what + "名称不合法: [" + name + "]，仅允许字母/数字/下划线/$/#/中文");
        }
    }

    /**
     * 按方言为标识符加引号（统一加引号，避免大小写折叠与关键字冲突）
     */
    public static String quote(String identifier, DbTypeEnum dbType) {
        if (identifier == null || identifier.isEmpty()) {
            throw new BusinessException("标识符不能为空");
        }
        if (dbType == DbTypeEnum.MYSQL) {
            // 反引号内部转义：` -> ``
            return "`" + identifier.replace("`", "``") + "`";
        }
        // 其余数据库使用双引号，内部转义：\" -> \"\"
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /**
     * 构造带限定前缀的表全名：
     * MySQL 取 catalog（库）.表；Oracle/DM/PG/金仓 取 schema.表；均为空则仅表名。
     */
    public static String qualifiedName(DbTypeEnum dbType, String catalog, String schema, String table) {
        checkIdentifier(table, "表");
        List<String> parts = new ArrayList<>();
        if (dbType == DbTypeEnum.MYSQL) {
            if (catalog != null && !catalog.isBlank()) {
                checkIdentifier(catalog, "catalog");
                parts.add(quote(catalog, dbType));
            }
        } else if (schema != null && !schema.isBlank()) {
            checkIdentifier(schema, "schema");
            parts.add(quote(schema, dbType));
        }
        parts.add(quote(table, dbType));
        return String.join(".", parts);
    }
}
