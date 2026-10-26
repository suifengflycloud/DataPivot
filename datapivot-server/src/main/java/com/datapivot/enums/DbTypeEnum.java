package com.datapivot.enums;

import com.datapivot.common.BusinessException;
import lombok.Getter;

import java.util.Arrays;

/**
 * 支持的数据库类型枚举
 * <p>集中维护各库默认端口、驱动类名与 JDBC URL 模板，前端按此规则自动拼接 URL。</p>
 */
@Getter
public enum DbTypeEnum {

    /** MySQL */
    MYSQL("MYSQL", "MySQL", 3306, "com.mysql.cj.jdbc.Driver",
            "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&useCursorFetch=true&useInformationSchema=true"),
    /** Oracle（thin 驱动，服务名方式） */
    ORACLE("ORACLE", "Oracle", 1521, "oracle.jdbc.OracleDriver",
            "jdbc:oracle:thin:@//%s:%d/%s"),
    /** 达梦 DM */
    DM("DM", "达梦DM", 5236, "dm.jdbc.driver.DmDriver",
            "jdbc:dm://%s:%d/%s"),
    /** 人大金仓 KingbaseES */
    KINGBASE("KINGBASE", "人大金仓KingbaseES", 54321, "com.kingbase8.Driver",
            "jdbc:kingbase8://%s:%d/%s"),
    /** PostgreSQL */
    POSTGRESQL("POSTGRESQL", "PostgreSQL", 5432, "org.postgresql.Driver",
            "jdbc:postgresql://%s:%d/%s?currentSchema=public");

    private final String code;
    private final String label;
    private final int defaultPort;
    private final String defaultDriver;
    private final String urlTemplate;

    DbTypeEnum(String code, String label, int defaultPort, String defaultDriver, String urlTemplate) {
        this.code = code;
        this.label = label;
        this.defaultPort = defaultPort;
        this.defaultDriver = defaultDriver;
        this.urlTemplate = urlTemplate;
    }

    /** 按编码解析，非法编码抛出友好业务异常 */
    public static DbTypeEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(t -> t.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException("不支持的数据库类型: " + code));
    }

    /** 按编码解析，允许为空（返回 null） */
    public static DbTypeEnum fromCodeOrNull(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(t -> t.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }

    /** 按主机/端口/库名生成默认 JDBC URL */
    public String buildUrl(String host, int port, String dbName) {
        return String.format(urlTemplate, host, port, dbName);
    }
}
