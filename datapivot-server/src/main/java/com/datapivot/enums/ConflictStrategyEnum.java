package com.datapivot.enums;

import com.datapivot.common.BusinessException;
import lombok.Getter;

import java.util.Arrays;

/**
 * 主键冲突策略
 */
@Getter
public enum ConflictStrategyEnum {

    /** 覆盖更新：目标表存在相同主键时更新其余字段 */
    OVERWRITE("OVERWRITE", "覆盖更新"),
    /** 跳过：目标表存在相同主键时忽略该行 */
    SKIP("SKIP", "跳过"),
    /** 报错中断：目标表存在相同主键时插入报错，任务失败结束 */
    ERROR("ERROR", "报错中断");

    private final String code;
    private final String label;

    ConflictStrategyEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ConflictStrategyEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException("不合法的主键冲突策略: " + code));
    }
}
