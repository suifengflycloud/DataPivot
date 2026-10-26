package com.datapivot.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 同步任务详情返回对象（含全部表映射与字段映射）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SyncTaskDetailVO extends SyncTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 表映射列表 */
    private List<TableMappingVO> mappings = new ArrayList<>();
}
