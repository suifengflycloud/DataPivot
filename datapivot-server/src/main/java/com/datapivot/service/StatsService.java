package com.datapivot.service;

import com.datapivot.vo.StatsVO;

/**
 * 首页统计服务
 */
public interface StatsService {

    /** 汇总统计 */
    StatsVO overview();
}
