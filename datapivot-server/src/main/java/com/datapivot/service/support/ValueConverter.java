package com.datapivot.service.support;

import com.datapivot.common.BusinessException;

import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * 跨库字段值转换器：源字段值按目标字段的 JDBC 类型大类做自动转换。
 * <p>支持 字符串 / 数字 / 日期时间 / 布尔 / 二进制 五大类之间的互转，
 * 用于字段映射中"源字段与目标字段类型不一致"的场景（如源 VARCHAR -> 目标 BIGINT）。</p>
 */
public final class ValueConverter {

    /** 字符串日期时间的常用格式（按顺序尝试解析） */
    private static final DateTimeFormatter[] DATETIME_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    };
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private ValueConverter() {
    }

    /** JDBC 类型大类 */
    public enum TypeCategory {
        STRING, NUMBER, BOOLEAN, DATETIME, BINARY, OTHER
    }

    /** java.sql.Types -> 类型大类 */
    public static TypeCategory categoryOf(int jdbcType) {
        return switch (jdbcType) {
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
                    Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> TypeCategory.STRING;
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.FLOAT,
                    Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> TypeCategory.NUMBER;
            case Types.BIT, Types.BOOLEAN -> TypeCategory.BOOLEAN;
            case Types.DATE, Types.TIME, Types.TIMESTAMP, Types.TIME_WITH_TIMEZONE,
                    Types.TIMESTAMP_WITH_TIMEZONE -> TypeCategory.DATETIME;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> TypeCategory.BINARY;
            default -> TypeCategory.OTHER;
        };
    }

    /**
     * 值转换主入口
     *
     * @param src      源值（非 null）
     * @param srcType  源字段 JDBC 类型码
     * @param tgtType  目标字段 JDBC 类型码
     * @return 可直接 setObject 到目标 PreparedStatement 的值
     */
    public static Object convert(Object src, int srcType, int tgtType) {
        if (src == null) {
            return null;
        }
        TypeCategory from = categoryOf(srcType);
        TypeCategory to = categoryOf(tgtType);
        // 同大类直接透传（驱动自行完成细微类型适配）
        if (from == to) {
            return src;
        }
        return switch (to) {
            case STRING -> toStringValue(src);
            case NUMBER -> toNumber(src, tgtType);
            case DATETIME -> toDatetime(src);
            case BOOLEAN -> toBoolean(src);
            case BINARY -> toBinary(src);
            default -> src;
        };
    }

    /** 任意值 -> 字符串 */
    private static String toStringValue(Object src) {
        if (src instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (src instanceof Number number) {
            return number.toString();
        }
        if (src instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(TS_FORMAT);
        }
        if (src instanceof LocalDateTime ldt) {
            return ldt.format(TS_FORMAT);
        }
        if (src instanceof java.sql.Date date) {
            return date.toLocalDate().format(DATE_FORMAT);
        }
        if (src instanceof LocalDate ld) {
            return ld.format(DATE_FORMAT);
        }
        if (src instanceof Time time) {
            return time.toLocalTime().format(TIME_FORMAT);
        }
        if (src instanceof LocalTime lt) {
            return lt.format(TIME_FORMAT);
        }
        if (src instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (src instanceof Boolean bool) {
            return bool ? "1" : "0";
        }
        return src.toString();
    }

    /** 任意值 -> 数字 */
    private static Object toNumber(Object src, int tgtType) {
        BigDecimal decimal;
        if (src instanceof Boolean bool) {
            decimal = bool ? BigDecimal.ONE : BigDecimal.ZERO;
        } else if (src instanceof Number number) {
            decimal = new BigDecimal(number.toString());
        } else {
            String text = src.toString().trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                decimal = new BigDecimal(text);
            } catch (NumberFormatException e) {
                throw new BusinessException("值 [" + text + "] 无法转换为数字类型");
            }
        }
        // 整型目标收敛为 long，避免小数被截断报错时信息不明确
        if (tgtType == Types.TINYINT || tgtType == Types.SMALLINT || tgtType == Types.INTEGER
                || tgtType == Types.BIGINT) {
            return decimal.longValue();
        }
        return decimal;
    }

    /** 任意值 -> 时间戳（数字按 epoch 毫秒处理） */
    private static Timestamp toDatetime(Object src) {
        if (src instanceof Timestamp ts) {
            return ts;
        }
        if (src instanceof java.util.Date date) {
            return new Timestamp(date.getTime());
        }
        if (src instanceof LocalDateTime ldt) {
            return Timestamp.valueOf(ldt);
        }
        if (src instanceof LocalDate ld) {
            return Timestamp.valueOf(ld.atStartOfDay());
        }
        if (src instanceof Number number) {
            return new Timestamp(number.longValue());
        }
        String text = src.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter formatter : DATETIME_FORMATS) {
            try {
                return Timestamp.valueOf(LocalDateTime.parse(text, formatter));
            } catch (Exception ignored) {
                // 尝试下一种格式
            }
        }
        try {
            return Timestamp.valueOf(LocalDate.parse(text).atStartOfDay());
        } catch (Exception ignored) {
            throw new BusinessException("值 [" + text + "] 无法识别为日期时间格式");
        }
    }

    /** 任意值 -> 布尔 */
    private static Boolean toBoolean(Object src) {
        if (src instanceof Boolean bool) {
            return bool;
        }
        if (src instanceof Number number) {
            return number.longValue() != 0L;
        }
        String text = src.toString().trim();
        if ("1".equals(text) || "true".equalsIgnoreCase(text) || "y".equalsIgnoreCase(text)
                || "yes".equalsIgnoreCase(text) || "on".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("0".equals(text) || "false".equalsIgnoreCase(text) || "n".equalsIgnoreCase(text)
                || "no".equalsIgnoreCase(text) || "off".equalsIgnoreCase(text) || text.isEmpty()) {
            return Boolean.FALSE;
        }
        try {
            return new BigDecimal(text).longValue() != 0L;
        } catch (NumberFormatException e) {
            throw new BusinessException("值 [" + text + "] 无法转换为布尔类型");
        }
    }

    /** 任意值 -> 二进制 */
    private static Object toBinary(Object src) {
        if (src instanceof byte[] bytes) {
            return bytes;
        }
        String text = src.toString();
        try {
            // 优先按 Base64 还原（toStringValue 的逆过程）
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
