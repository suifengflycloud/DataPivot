package com.datapivot.service;

import com.datapivot.common.PageResult;
import com.datapivot.dto.ScheduleDTO;
import com.datapivot.vo.ScheduleVO;

/**
 * 定时任务配置服务：本地配置 + XXL-Job 调度中心双向同步
 */
public interface ScheduleService {

    /** 分页查询（含下次执行时间与最近执行结果） */
    PageResult<ScheduleVO> page(long current, long size);

    /** 新建定时配置：自动在 XXL-Job 注册任务并启动 */
    void create(ScheduleDTO dto);

    /** 修改定时配置：同步更新 XXL-Job 任务 */
    void update(Long id, ScheduleDTO dto);

    /** 删除定时配置：同步删除 XXL-Job 任务 */
    void delete(Long id);

    /** 启用（恢复调度） */
    void enable(Long id);

    /** 暂停调度 */
    void disable(Long id);

    /** 通过 XXL-Job 立即触发一次 */
    void trigger(Long id);

    /** 删除某同步任务下的全部定时配置（任务删除时级联） */
    void removeByTaskId(Long taskId);
}
