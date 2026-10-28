package com.datapivot.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 表映射返回对象（含字段映射）
 */
@Data
public class TableMappingVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String sourceCatalog;

    private String sourceSchema;

    private String sourceTable;

    /** 源表展示名 */
    private String sourceDisplayName;

    private String targetCatalog;

    private String targetSchema;

    private String targetTable;

    /** 目标表展示名 */
    private String targetDisplayName;

    /** 增量依据字段（增量同步任务返回） */
    private String incColumn;

    /** 上次增量同步水位值（null 表示尚未执行过增量同步） */
    private String incLastValue;

    /** 字段映射 */
    private List<ColumnMappingVO> columns = new ArrayList<>();

    /**
     * 字段映射返回对象
     */
    @Data
    public static class ColumnMappingVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String sourceColumn;

        private String targetColumn;
    }
}
