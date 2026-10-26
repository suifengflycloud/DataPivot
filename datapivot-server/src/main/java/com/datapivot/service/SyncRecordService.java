package com.datapivot.service;

import com.datapivot.common.PageResult;
import com.datapivot.vo.RecordVO;

/**
 * 任务执行记录服务
 */
public interface SyncRecordService {

    /** 分页查询（taskId/status 可选过滤） */
    PageResult<RecordVO> page(long current, long size, Long taskId, String status);

    /** 记录详情（含全量日志） */
    RecordVO getDetail(Long id);

    /** 删除记录 */
    void delete(Long id);

    /** 某任务最近一次执行记录（无则 null） */
    RecordVO latestByTaskId(Long taskId);
}
