package com.datapivot.service.support;

import com.datapivot.common.BusinessException;
import com.datapivot.vo.ColumnMetaVO;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Date;

/**
 * 增量水位编解码：负责水位值在「dp_table_mapping.inc_last_value 存储字符串」与
 * 「JDBC 查询绑定参数/结果值」之间的转换与比较。
 * <p>增量字段仅支持两类（见 ColumnMetaVO#typeCategory）：</p>
 * <ul>
 *   <li>DATETIME：水位归一化为 LocalDateTime，存储为 "yyyy-MM-dd HH:mm:ss[.纳秒]"（空格分隔，便于人工查看）</li>
 *   <li>NUMBER：水位归一化为 BigDecimal，存储为十进制字符串</li>
 * </ul>
 */
public final class IncrementalWatermark {

    private IncrementalWatermark() {
    }

    /** 校验增量字段类型是否支持（执行同步前调用，给出友好提示） */
    public static void checkTypeSupport(ColumnMetaVO incMeta, String sourceDisplay, String incColumn) {
        String category = incMeta.getTypeCategory();
        if (!"DATETIME".equals(category) && !"NUMBER".equals(category)) {
            throw new BusinessException("源表 [" + sourceDisplay + "] 增量字段 [" + incColumn
                    + "] 类型为 " + incMeta.getTypeName() + "，仅支持日期时间或数值类型字段作为增量依据");
        }
    }

    /**
     * 把结果集读出的原始值归一化为可比较的水位对象（LocalDateTime / BigDecimal）。
     * 各驱动返回类型不一（MySQL 返回 LocalDateTime、Oracle 返回 Timestamp 等），统一在此收口；null 原样返回。
     */
    public static Object normalize(ColumnMetaVO incMeta, Object raw) {
        if (raw == null) {
            return null;
        }
        if ("DATETIME".equals(incMeta.getTypeCategory())) {
            if (raw instanceof LocalDateTime dateTime) {
                return dateTime;
            }
            if (raw instanceof Timestamp timestamp) {
                return timestamp.toLocalDateTime();
            }
            if (raw instanceof OffsetDateTime offsetDateTime) {
                return offsetDateTime.toLocalDateTime();
            }
            if (raw instanceof Date date) {
                return new Timestamp(date.getTime()).toLocalDateTime();
            }
        } else if ("NUMBER".equals(incMeta.getTypeCategory())) {
            return new BigDecimal(raw.toString());
        }
        throw new BusinessException("增量字段值无法识别: " + raw.getClass().getSimpleName()
                + "，请更换增量字段（仅支持日期时间或数值类型）");
    }

    /** 水位比较值绑定参数：LocalDateTime 转 Timestamp，BigDecimal 原样 */
    public static Object toBindParam(Object watermark) {
        if (watermark instanceof LocalDateTime dateTime) {
            return Timestamp.valueOf(dateTime);
        }
        return watermark;
    }

    /** 从存储字符串恢复水位对象（任务配置被手工篡改时给出友好报错） */
    public static Object parse(ColumnMetaVO incMeta, String stored) {
        try {
            if ("DATETIME".equals(incMeta.getTypeCategory())) {
                return LocalDateTime.parse(stored.replace(' ', 'T'));
            }
            return new BigDecimal(stored);
        } catch (RuntimeException e) {
            throw new BusinessException("增量水位值 [" + stored + "] 无法解析，请编辑任务重新选择增量字段以重置水位");
        }
    }

    /** 水位对象转为存储字符串 */
    public static String toStored(Object watermark) {
        if (watermark instanceof LocalDateTime dateTime) {
            return dateTime.toString().replace('T', ' ');
        }
        return ((BigDecimal) watermark).toPlainString();
    }

    /** 同类型水位比较：a 大于 b 返回正数 */
    public static int compare(Object a, Object b) {
        if (a instanceof LocalDateTime left) {
            return left.compareTo((LocalDateTime) b);
        }
        return ((BigDecimal) a).compareTo((BigDecimal) b);
    }
}
