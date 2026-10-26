package com.datapivot.service;

import com.datapivot.vo.CompareResultVO;

/**
 * 数据库结构对比服务：对比两个库的表结构差异（精确到字段级）
 */
public interface SchemaCompareService {

    /**
     * 执行结构对比并生成 DDL 升级脚本
     *
     * @param baseConnId   基准库连接 id
     * @param targetConnId 对比库连接 id（DDL 脚本执行目标）
     */
    CompareResultVO compare(Long baseConnId, Long targetConnId);
}
