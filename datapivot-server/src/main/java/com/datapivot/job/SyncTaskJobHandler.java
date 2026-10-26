package com.datapivot.job;

import com.datapivot.common.BusinessException;
import com.datapivot.service.SyncExecuteService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * XXL-Job 同步任务执行器
 * <p>调度中心所有 DataPivot 同步任务共用该 handler，任务参数为同步任务 id。
 * 执行流程：触发同步 -> 阻塞等待完成 -> 将最终状态回传调度中心（成功/失败）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncTaskJobHandler {

    /** 等待同步完成的上限：24 小时（超时仅影响本次调度回传，不影响同步继续执行） */
    private static final long AWAIT_TIMEOUT_MILLIS = 24L * 60 * 60 * 1000;

    private final SyncExecuteService syncExecuteService;

    @XxlJob("syncTaskExecuteJob")
    public void syncTaskExecuteJob() {
        // 任务参数 = 同步任务 id
        String param = XxlJobHelper.getJobParam();
        Long taskId;
        try {
            taskId = Long.parseLong(param == null ? "" : param.trim());
        } catch (NumberFormatException e) {
            XxlJobHelper.log("任务参数不合法，期望为同步任务 id，实际: [" + param + "]");
            XxlJobHelper.handleFail("任务参数不合法，期望为同步任务 id");
            return;
        }
        XxlJobHelper.log("开始执行同步任务, taskId=" + taskId);
        try {
            Long recordId = syncExecuteService.executeTask(taskId,
                    com.datapivot.enums.TriggerTypeEnum.SCHEDULED);
            XxlJobHelper.log("同步已提交, 执行记录id=" + recordId + ", 等待执行完成...");
            String finalStatus = syncExecuteService.awaitCompletion(recordId, AWAIT_TIMEOUT_MILLIS);
            if ("SUCCESS".equals(finalStatus)) {
                XxlJobHelper.log("同步任务执行成功");
                XxlJobHelper.handleSuccess("同步成功, 记录id=" + recordId);
            } else if ("FAILED".equals(finalStatus)) {
                XxlJobHelper.log("同步任务执行失败, 详情见 DataPivot 执行记录 id=" + recordId);
                XxlJobHelper.handleFail("同步失败, 记录id=" + recordId);
            } else {
                // 等待超时或进度对象被清理
                XxlJobHelper.log("等待同步完成超时/状态未知, 最终状态=" + finalStatus + ", 可在 DataPivot 执行记录中查看 id=" + recordId);
                XxlJobHelper.handleSuccess("执行已提交, 结果请查看 DataPivot 执行记录 id=" + recordId);
            }
        } catch (BusinessException e) {
            XxlJobHelper.log("同步任务执行失败: " + e.getMessage());
            XxlJobHelper.handleFail(e.getMessage());
        } catch (Exception e) {
            log.error("同步任务执行异常, taskId={}", taskId, e);
            XxlJobHelper.log("同步任务执行异常: " + e.getMessage());
            XxlJobHelper.handleFail("系统异常: " + e.getMessage());
        }
    }
}
