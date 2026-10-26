package com.datapivot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datapivot.entity.DbConnection;
import com.datapivot.entity.ScheduleConfig;
import com.datapivot.entity.SyncRecord;
import com.datapivot.entity.SyncTask;
import com.datapivot.enums.RecordStatusEnum;
import com.datapivot.mapper.DbConnectionMapper;
import com.datapivot.mapper.ScheduleConfigMapper;
import com.datapivot.mapper.SyncRecordMapper;
import com.datapivot.mapper.SyncTaskMapper;
import com.datapivot.service.StatsService;
import com.datapivot.vo.StatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 首页统计服务实现
 */
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final DbConnectionMapper connectionMapper;
    private final SyncTaskMapper taskMapper;
    private final ScheduleConfigMapper scheduleConfigMapper;
    private final SyncRecordMapper recordMapper;

    @Override
    public StatsVO overview() {
        StatsVO vo = new StatsVO();
        vo.setConnectionCount(connectionMapper.selectCount(new LambdaQueryWrapper<DbConnection>()));
        vo.setTaskCount(taskMapper.selectCount(new LambdaQueryWrapper<SyncTask>()));
        vo.setScheduleCount(scheduleConfigMapper.selectCount(new LambdaQueryWrapper<ScheduleConfig>()));

        // 今日执行统计（含执行中的记录）
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<SyncRecord> todayRecords = recordMapper.selectList(new LambdaQueryWrapper<SyncRecord>()
                .ge(SyncRecord::getCreateTime, todayStart));
        vo.setTodayRecordCount(todayRecords.size());
        long success = todayRecords.stream()
                .filter(r -> RecordStatusEnum.SUCCESS.getCode().equals(r.getStatus())).count();
        long failed = todayRecords.stream()
                .filter(r -> RecordStatusEnum.FAILED.getCode().equals(r.getStatus())).count();
        vo.setTodaySuccessCount(success);
        vo.setTodayFailCount(failed);
        long finished = success + failed;
        vo.setSuccessRate(finished == 0 ? 100 : (int) (success * 100 / finished));
        return vo;
    }
}
