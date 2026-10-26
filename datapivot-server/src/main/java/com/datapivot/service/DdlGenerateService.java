package com.datapivot.service;

import com.datapivot.enums.DbTypeEnum;
import com.datapivot.vo.TableDiffVO;

import java.util.List;

/**
 * DDL 升级脚本生成服务：根据结构差异按目标库方言生成 DDL
 */
public interface DdlGenerateService {

    /**
     * 生成 DDL 升级脚本
     *
     * @param targetDbType 目标库（对比库）方言
     * @param tableDiffs   结构差异列表
     * @param baseConnName 基准库名称（脚本头注释）
     * @param targetConnName 对比库名称（脚本头注释）
     */
    String generate(DbTypeEnum targetDbType, List<TableDiffVO> tableDiffs, String baseConnName,
                    String targetConnName);
}
