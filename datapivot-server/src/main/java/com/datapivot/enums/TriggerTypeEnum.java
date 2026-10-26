package com.datapivot.enums;

import lombok.Getter;

/**
 * 任务执行触发类型
 */
@Getter
public enum TriggerTypeEnum {

    /** 页面手动执行 */
    MANUAL("MANUAL", "手动执行"),
    /** XXL-Job 定时调度触发 */
    SCHEDULED("SCHEDULED", "定时调度");

    private final String code;
    private final String label;

    TriggerTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
