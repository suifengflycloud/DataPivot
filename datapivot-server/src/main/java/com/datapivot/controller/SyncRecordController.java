package com.datapivot.controller;

import com.datapivot.common.PageResult;
import com.datapivot.common.R;
import com.datapivot.service.SyncRecordService;
import com.datapivot.vo.RecordVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务执行记录接口
 */
@Validated
@RestController
@RequestMapping("/api/record")
@RequiredArgsConstructor
public class SyncRecordController {

    private final SyncRecordService recordService;

    /** 分页查询（可按任务/状态过滤） */
    @GetMapping("/page")
    public R<PageResult<RecordVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") long current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") long size,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String status) {
        return R.ok(recordService.page(current, size, taskId, status));
    }

    /** 记录详情（含全量执行日志） */
    @GetMapping("/{id}")
    public R<RecordVO> detail(@PathVariable Long id) {
        return R.ok(recordService.getDetail(id));
    }

    /** 删除记录 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        recordService.delete(id);
        return R.ok("记录删除成功", null);
    }

    /** 某任务最近一次执行记录（定时任务页展示用） */
    @GetMapping("/latest")
    public R<RecordVO> latest(@RequestParam Long taskId) {
        return R.ok(recordService.latestByTaskId(taskId));
    }
}
