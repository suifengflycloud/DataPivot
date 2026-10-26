package com.datapivot.service;

import com.datapivot.enums.TriggerTypeEnum;
import com.datapivot.vo.SyncProgressVO;

/**
 * 同步执行服务：按映射关系读取源表数据，逐批写入目标表，并维护实时进度。
 */
public interface SyncExecuteService {

    /**
     * 异步执行同步任务
     *
     * @return 执行记录 id（用于轮询进度）
     */
    Long executeTask(Long taskId, TriggerTypeEnum triggerType);

    /** 查询执行进度（recordId 不存在或已清理时返回 null） */
    SyncProgressVO getProgress(Long recordId);

    /**
     * 阻塞等待执行完成（XXL-Job 调度线程使用，便于执行结果回传调度中心）
     *
     * @return 最终状态：SUCCESS / FAILED；超时或找不到进度返回 null
     */
    String awaitCompletion(Long recordId, long timeoutMillis);
}
