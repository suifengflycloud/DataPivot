package com.datapivot.controller;

import com.datapivot.common.PageResult;
import com.datapivot.common.R;
import com.datapivot.dto.ScheduleDTO;
import com.datapivot.service.ScheduleService;
import com.datapivot.vo.ScheduleVO;
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

/**
 * 定时任务配置接口
 */
@Validated
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /** 分页查询（含下次执行时间、最近执行结果） */
    @GetMapping("/page")
    public R<PageResult<ScheduleVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") long current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") long size) {
        return R.ok(scheduleService.page(current, size));
    }

    /** 新建定时配置（自动注册到 XXL-Job 并启动） */
    @PostMapping
    public R<Void> create(@Valid @RequestBody ScheduleDTO dto) {
        scheduleService.create(dto);
        return R.ok("定时任务创建成功，已注册到调度中心", null);
    }

    /** 修改定时配置 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ScheduleDTO dto) {
        scheduleService.update(id, dto);
        return R.ok("定时任务更新成功", null);
    }

    /** 删除定时配置（同步移除调度中心任务） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return R.ok("定时任务删除成功", null);
    }

    /** 启用（恢复调度） */
    @PostMapping("/{id}/enable")
    public R<Void> enable(@PathVariable Long id) {
        scheduleService.enable(id);
        return R.ok("定时任务已启用", null);
    }

    /** 暂停调度 */
    @PostMapping("/{id}/disable")
    public R<Void> disable(@PathVariable Long id) {
        scheduleService.disable(id);
        return R.ok("定时任务已暂停", null);
    }

    /** 通过调度中心立即触发一次 */
    @PostMapping("/{id}/trigger")
    public R<Void> trigger(@PathVariable Long id) {
        scheduleService.trigger(id);
        return R.ok("已触发执行，稍后可在任务执行记录中查看结果", null);
    }
}
