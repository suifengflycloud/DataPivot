package com.datapivot.service.support;

import com.datapivot.enums.RecordStatusEnum;
import com.datapivot.vo.SyncProgressVO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 同步执行进度（内存对象）
 * <p>单个执行记录一份；工作线程写、HTTP 轮询线程读，日志队列以 synchronized 保护，
 * 计数采用 AtomicLong 保证可见性。任务结束后保留一段时间供前端拉取终态，由定时清理任务回收。</p>
 */
public class SyncProgress {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Long recordId;
    private final Long taskId;
    private final String taskName;
    private final int maxLogLines;

    /** 执行状态（volatile：单写多读） */
    private volatile String status = RecordStatusEnum.RUNNING.getCode();
    private volatile long total;
    private volatile long successCount;
    private volatile long failCount;
    private volatile String currentTable = "";
    private volatile String errorMessage;
    private volatile LocalDateTime finishTime;

    /** 环形日志缓冲（仅保留最近 N 条） */
    private final Deque<String> logs = new ArrayDeque<>();

    public SyncProgress(Long recordId, Long taskId, String taskName, int maxLogLines) {
        this.recordId = recordId;
        this.taskId = taskId;
        this.taskName = taskName;
        this.maxLogLines = maxLogLines;
    }

    /** 追加一条执行日志（自动加时间戳） */
    public synchronized void appendLog(String message) {
        logs.addLast(LocalDateTime.now().format(TIME_FORMAT) + " " + message);
        while (logs.size() > maxLogLines) {
            logs.pollFirst();
        }
    }

    /** 累加总行数（各源表 COUNT 阶段调用） */
    public void increaseTotal(long count) {
        total += count;
    }

    /** 累加成功条数 */
    public void addSuccess(long count) {
        successCount += count;
    }

    /** 标记失败：失败数 = 总数 - 成功数（同表内已提交部分计为成功） */
    public void markFailed(String message) {
        this.errorMessage = message;
        this.status = RecordStatusEnum.FAILED.getCode();
        this.failCount = Math.max(0, total - successCount);
        this.finishTime = LocalDateTime.now();
    }

    /** 正常结束 */
    public void finishSuccess() {
        this.status = RecordStatusEnum.SUCCESS.getCode();
        this.finishTime = LocalDateTime.now();
    }

    public boolean isRunning() {
        return RecordStatusEnum.RUNNING.getCode().equals(status);
    }

    /** 导出为前端轮询 VO */
    public SyncProgressVO toVO() {
        SyncProgressVO vo = new SyncProgressVO();
        vo.setRecordId(recordId);
        vo.setTaskId(taskId);
        vo.setTaskName(taskName);
        vo.setStatus(status);
        vo.setTotal(total);
        vo.setSuccessCount(successCount);
        vo.setFailCount(failCount);
        vo.setProcessed(successCount + failCount);
        vo.setCurrentTable(currentTable);
        // 总数为 0 时：已结束视为 100%，执行中视为 0%
        if (total <= 0) {
            vo.setPercent(isRunning() ? 0 : 100);
        } else {
            vo.setPercent((int) Math.min(100, (successCount + failCount) * 100 / total));
        }
        List<String> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(logs);
        }
        vo.setLogs(snapshot);
        return vo;
    }

    /** 导出全部日志文本（持久化到执行记录 error_log 字段） */
    public String dumpLogs() {
        synchronized (this) {
            return String.join("\n", logs);
        }
    }

    public Long getRecordId() {
        return recordId;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public long getFailCount() {
        return failCount;
    }

    public long getTotal() {
        return total;
    }

    public void setCurrentTable(String currentTable) {
        this.currentTable = currentTable;
    }
}
