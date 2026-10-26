package com.datapivot.service;

import com.datapivot.common.PageResult;
import com.datapivot.dto.DbConnectionDTO;
import com.datapivot.entity.DbConnection;
import com.datapivot.vo.DbConnectionVO;
import com.datapivot.vo.TestResultVO;

import java.util.List;

/**
 * 数据库连接管理服务
 */
public interface DbConnectionService {

    /** 分页查询（keyword 匹配连接名称/IP/库名） */
    PageResult<DbConnectionVO> page(long current, long size, String keyword);

    /** 全量列表（下拉选择用） */
    List<DbConnectionVO> listAll();

    /** 详情（密码脱敏） */
    DbConnectionVO getDetail(Long id);

    /** 新增 */
    void create(DbConnectionDTO dto);

    /** 编辑（密码留空表示不修改） */
    void update(Long id, DbConnectionDTO dto);

    /** 删除（被同步任务引用时拒绝删除） */
    void delete(Long id);

    /** 测试已保存连接的连通性 */
    TestResultVO testById(Long id);

    /** 测试表单连接（未保存配置）的连通性 */
    TestResultVO testByDto(DbConnectionDTO dto);

    /** 按 id 获取实体（不存在抛业务异常） */
    DbConnection getByIdNotNull(Long id);
}
