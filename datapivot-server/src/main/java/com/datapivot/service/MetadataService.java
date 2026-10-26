package com.datapivot.service;

import com.datapivot.entity.DbConnection;
import com.datapivot.vo.ColumnMetaVO;
import com.datapivot.vo.TableMetaVO;

import java.util.List;

/**
 * 元数据读取服务：仅使用 JDBC DatabaseMetaData 标准 API，不拼接任何系统表 SQL。
 */
public interface MetadataService {

    /** 拉取连接下全部用户表（表名 + 表注释） */
    List<TableMetaVO> listTables(DbConnection conn);

    /** 拉取指定表的字段详情（字段名/注释/类型/长度/精度/主键/可空/默认值） */
    List<ColumnMetaVO> listColumns(DbConnection conn, String catalog, String schema, String tableName);
}
