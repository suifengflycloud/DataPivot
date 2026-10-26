package com.datapivot.enums;

import lombok.Getter;

/**
 * 同步任务执行记录状态
 */
@Getter
public enum RecordStatusEnum {

    /** 执行中 */
    RUNNING("RUNNING", "执行中"),
    /** 全部成功 */
    SUCCESS("SUCCESS", "成功"),
    /** 失败（含中断） */
    FAILED("FAILED", "失败");

    private final String code;
    private final String label;

    RecordStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
