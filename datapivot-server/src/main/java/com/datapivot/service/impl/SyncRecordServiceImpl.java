package com.datapivot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.datapivot.common.BusinessException;
import com.datapivot.common.PageResult;
import com.datapivot.entity.SyncRecord;
import com.datapivot.mapper.SyncRecordMapper;
import com.datapivot.service.SyncRecordService;
import com.datapivot.vo.RecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务执行记录服务实现
 */
@Service
@RequiredArgsConstructor
public class SyncRecordServiceImpl implements SyncRecordService {

    private final SyncRecordMapper recordMapper;

    @Override
    public PageResult<RecordVO> page(long current, long size, Long taskId, String status) {
        LambdaQueryWrapper<SyncRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(taskId != null, SyncRecord::getTaskId, taskId);
        wrapper.eq(StringUtils.hasText(status), SyncRecord::getStatus, status);
        wrapper.orderByDesc(SyncRecord::getId);
        Page<SyncRecord> page = recordMapper.selectPage(new Page<>(current, size), wrapper);
        List<RecordVO> records = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize(), vo -> vo);
    }

    @Override
    public RecordVO getDetail(Long id) {
        SyncRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("执行记录不存在");
        }
        return toVO(record);
    }

    @Override
    public void delete(Long id) {
        recordMapper.deleteById(id);
    }

    @Override
    public RecordVO latestByTaskId(Long taskId) {
        SyncRecord latest = recordMapper.selectOne(new LambdaQueryWrapper<SyncRecord>()
                .eq(SyncRecord::getTaskId, taskId)
                .orderByDesc(SyncRecord::getId)
                .last("LIMIT 1"));
        return latest == null ? null : toVO(latest);
    }

    private RecordVO toVO(SyncRecord record) {
        RecordVO vo = new RecordVO();
        vo.setId(record.getId());
        vo.setTaskId(record.getTaskId());
        vo.setTaskName(record.getTaskName());
        vo.setTriggerType(record.getTriggerType());
        vo.setStatus(record.getStatus());
        vo.setTotalCount(record.getTotalCount());
        vo.setSuccessCount(record.getSuccessCount());
        vo.setFailCount(record.getFailCount());
        vo.setStartTime(record.getStartTime());
        vo.setEndTime(record.getEndTime());
        vo.setErrorLog(record.getErrorLog());
        if (record.getStartTime() != null && record.getEndTime() != null) {
            vo.setDurationMs(Duration.between(record.getStartTime(), record.getEndTime()).toMillis());
        }
        return vo;
    }
}
