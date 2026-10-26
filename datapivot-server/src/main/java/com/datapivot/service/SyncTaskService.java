package com.datapivot.service;

import com.datapivot.common.PageResult;
import com.datapivot.dto.SyncTaskDTO;
import com.datapivot.vo.SyncTaskDetailVO;
import com.datapivot.vo.SyncTaskVO;

import java.util.List;

/**
 * 同步任务配置服务
 */
public interface SyncTaskService {

    /** 分页查询 */
    PageResult<SyncTaskVO> page(long current, long size, String keyword);

    /** 简要列表（下拉用） */
    List<SyncTaskVO> listAll();

    /** 任务详情（含表映射与字段映射） */
    SyncTaskDetailVO getDetail(Long id);

    /** 新建任务（含全部映射） */
    void create(SyncTaskDTO dto);

    /** 编辑任务（子映射全删全插） */
    void update(Long id, SyncTaskDTO dto);

    /** 删除任务（级联删除映射与定时配置） */
    void delete(Long id);
}
