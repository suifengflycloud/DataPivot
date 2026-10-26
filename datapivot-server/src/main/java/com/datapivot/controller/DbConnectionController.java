package com.datapivot.controller;

import com.datapivot.common.PageResult;
import com.datapivot.common.R;
import com.datapivot.dto.DbConnectionDTO;
import com.datapivot.service.DbConnectionService;
import com.datapivot.vo.DbConnectionVO;
import com.datapivot.vo.TestResultVO;
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
 * 数据库连接管理接口
 */
@Validated
@RestController
@RequestMapping("/api/connection")
@RequiredArgsConstructor
public class DbConnectionController {

    private final DbConnectionService connectionService;

    /** 分页查询 */
    @GetMapping("/page")
    public R<PageResult<DbConnectionVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") long current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") long size,
            @RequestParam(required = false) String keyword) {
        return R.ok(connectionService.page(current, size, keyword));
    }

    /** 全量列表（下拉选择用） */
    @GetMapping("/list")
    public R<List<DbConnectionVO>> list() {
        return R.ok(connectionService.listAll());
    }

    /** 连接详情（密码脱敏） */
    @GetMapping("/{id}")
    public R<DbConnectionVO> detail(@PathVariable Long id) {
        return R.ok(connectionService.getDetail(id));
    }

    /** 新增连接 */
    @PostMapping
    public R<Void> create(@Valid @RequestBody DbConnectionDTO dto) {
        connectionService.create(dto);
        return R.ok("连接创建成功", null);
    }

    /** 编辑连接 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody DbConnectionDTO dto) {
        connectionService.update(id, dto);
        return R.ok("连接更新成功", null);
    }

    /** 删除连接（二次确认由前端负责，后端校验引用关系） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        connectionService.delete(id);
        return R.ok("连接删除成功", null);
    }

    /** 测试已保存连接 */
    @GetMapping("/test/{id}")
    public R<TestResultVO> testById(@PathVariable Long id) {
        return R.ok(connectionService.testById(id));
    }

    /** 测试表单连接（未保存的配置直接测试） */
    @PostMapping("/test")
    public R<TestResultVO> testByDto(@Valid @RequestBody DbConnectionDTO dto) {
        return R.ok(connectionService.testByDto(dto));
    }
}
