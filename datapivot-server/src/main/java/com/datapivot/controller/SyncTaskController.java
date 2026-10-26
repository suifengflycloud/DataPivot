package com.datapivot.controller;

import com.datapivot.common.BusinessException;
import com.datapivot.common.PageResult;
import com.datapivot.common.R;
import com.datapivot.dto.SyncTaskDTO;
import com.datapivot.enums.TriggerTypeEnum;
import com.datapivot.service.SyncExecuteService;
import com.datapivot.service.SyncTaskService;
import com.datapivot.vo.SyncProgressVO;
import com.datapivot.vo.SyncTaskDetailVO;
import com.datapivot.vo.SyncTaskVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 同步任务接口
 */
@Validated
@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class SyncTaskController {

    private final SyncTaskService taskService;
    private final SyncExecuteService executeService;

    /** 分页查询 */
    @GetMapping("/page")
    public R<PageResult<SyncTaskVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") long current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") long size,
            @RequestParam(required = false) String keyword) {
        return R.ok(taskService.page(current, size, keyword));
    }

    /** 简要列表（下拉用） */
    @GetMapping("/list")
    public R<List<SyncTaskVO>> list() {
        return R.ok(taskService.listAll());
    }

    /** 任务详情（含映射） */
    @GetMapping("/{id}")
    public R<SyncTaskDetailVO> detail(@PathVariable Long id) {
        return R.ok(taskService.getDetail(id));
    }

    /** 新建任务 */
    @PostMapping
    public R<Void> create(@Valid @RequestBody SyncTaskDTO dto) {
        taskService.create(dto);
        return R.ok("任务创建成功", null);
    }

    /** 编辑任务 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SyncTaskDTO dto) {
        taskService.update(id, dto);
        return R.ok("任务更新成功", null);
    }

    /** 删除任务 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return R.ok("任务删除成功", null);
    }

    /** 手动立即执行（异步），返回执行记录 id 用于轮询进度 */
    @PostMapping("/{id}/execute")
    public R<Long> execute(@PathVariable Long id) {
        return R.ok(executeService.executeTask(id, TriggerTypeEnum.MANUAL));
    }

    /** 轮询执行进度 */
    @GetMapping("/progress/{recordId}")
    public R<SyncProgressVO> progress(@PathVariable Long recordId) {
        SyncProgressVO progress = executeService.getProgress(recordId);
        if (progress == null) {
            throw new BusinessException("执行进度不存在或已过期（结束超过30分钟的记录请到执行记录页查看）");
        }
        return R.ok(progress);
    }
}
