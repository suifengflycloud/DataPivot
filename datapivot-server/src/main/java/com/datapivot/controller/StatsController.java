package com.datapivot.controller;

import com.datapivot.common.R;
import com.datapivot.service.StatsService;
import com.datapivot.vo.StatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页统计接口
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 汇总统计 */
    @GetMapping("/overview")
    public R<StatsVO> overview() {
        return R.ok(statsService.overview());
    }
}
