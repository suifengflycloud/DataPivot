package com.datapivot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.datapivot.common.BusinessException;
import com.datapivot.common.PageResult;
import com.datapivot.dto.ScheduleDTO;
import com.datapivot.entity.ScheduleConfig;
import com.datapivot.entity.SyncRecord;
import com.datapivot.entity.SyncTask;
import com.datapivot.mapper.ScheduleConfigMapper;
import com.datapivot.mapper.SyncRecordMapper;
import com.datapivot.mapper.SyncTaskMapper;
import com.datapivot.service.ScheduleService;
import com.datapivot.service.XxlJobAdminClient;
import com.datapivot.vo.ScheduleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 定时任务配置服务实现：本地配置表与 XXL-Job 调度中心保持双向同步。
 * <p>保存配置时自动登录调度中心创建任务并启动；修改/启停/删除同样同步到调度中心。</p>
 */
@Slf4j
@Service
public class ScheduleServiceImpl implements ScheduleService {

    /** XXL-Job 执行器统一 handler 名（param 传同步任务 id） */
    private static final String EXECUTOR_HANDLER = "syncTaskExecuteJob";

    private final ScheduleConfigMapper scheduleConfigMapper;
    private final SyncTaskMapper taskMapper;
    private final SyncRecordMapper recordMapper;
    private final XxlJobAdminClient xxlJobAdminClient;
    private final String appname;

    public ScheduleServiceImpl(ScheduleConfigMapper scheduleConfigMapper,
                               SyncTaskMapper taskMapper,
                               SyncRecordMapper recordMapper,
                               XxlJobAdminClient xxlJobAdminClient,
                               @Value("${xxl.job.executor.appname:datapivot-executor}") String appname) {
        this.scheduleConfigMapper = scheduleConfigMapper;
        this.taskMapper = taskMapper;
        this.recordMapper = recordMapper;
        this.xxlJobAdminClient = xxlJobAdminClient;
        this.appname = appname;
    }

    @Override
    public PageResult<ScheduleVO> page(long current, long size) {
        Page<ScheduleConfig> page = scheduleConfigMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<ScheduleConfig>().orderByDesc(ScheduleConfig::getId));
        List<ScheduleVO> records = new ArrayList<>();
        for (ScheduleConfig config : page.getRecords()) {
            records.add(toVO(config));
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize(), vo -> vo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(ScheduleDTO dto) {
        SyncTask task = getTaskNotNull(dto.getTaskId());
        String cron = validateCron(dto.getCronExpr());
        // 调度中心：查执行器分组 -> 新建任务 -> 启动
        xxlJobAdminClient.checkAlive();
        int jobGroupId = xxlJobAdminClient.queryJobGroupId(appname);
        int xxlJobId = xxlJobAdminClient.addJob(jobGroupId, buildJobDesc(dto, task),
                dto.getOwner(), cron, EXECUTOR_HANDLER, String.valueOf(task.getId()));
        xxlJobAdminClient.startJob(xxlJobId);

        ScheduleConfig config = new ScheduleConfig();
        config.setTaskId(task.getId());
        config.setCronExpr(cron);
        config.setJobDesc(dto.getJobDesc().trim());
        config.setOwner(dto.getOwner().trim());
        config.setXxlJobId(xxlJobId);
        config.setStatus("ENABLED");
        scheduleConfigMapper.insert(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ScheduleDTO dto) {
        ScheduleConfig exist = getByIdNotNull(id);
        SyncTask task = getTaskNotNull(dto.getTaskId());
        String cron = validateCron(dto.getCronExpr());
        xxlJobAdminClient.checkAlive();
        if (exist.getXxlJobId() != null) {
            int jobGroupId = xxlJobAdminClient.queryJobGroupId(appname);
            xxlJobAdminClient.updateJob(exist.getXxlJobId(), jobGroupId, buildJobDesc(dto, task),
                    dto.getOwner(), cron, EXECUTOR_HANDLER, String.valueOf(task.getId()));
        }
        ScheduleConfig config = new ScheduleConfig();
        config.setId(id);
        config.setTaskId(task.getId());
        config.setCronExpr(cron);
        config.setJobDesc(dto.getJobDesc().trim());
        config.setOwner(dto.getOwner().trim());
        scheduleConfigMapper.updateById(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ScheduleConfig config = getByIdNotNull(id);
        removeXxlJobQuietly(config);
        scheduleConfigMapper.deleteById(id);
    }

    @Override
    public void enable(Long id) {
        ScheduleConfig config = getByIdNotNull(id);
        if (config.getXxlJobId() != null) {
            xxlJobAdminClient.startJob(config.getXxlJobId());
        }
        ScheduleConfig update = new ScheduleConfig();
        update.setId(id);
        update.setStatus("ENABLED");
        scheduleConfigMapper.updateById(update);
    }

    @Override
    public void disable(Long id) {
        ScheduleConfig config = getByIdNotNull(id);
        if (config.getXxlJobId() != null) {
            xxlJobAdminClient.stopJob(config.getXxlJobId());
        }
        ScheduleConfig update = new ScheduleConfig();
        update.setId(id);
        update.setStatus("DISABLED");
        scheduleConfigMapper.updateById(update);
    }

    @Override
    public void trigger(Long id) {
        ScheduleConfig config = getByIdNotNull(id);
        if (config.getXxlJobId() == null) {
            throw new BusinessException("该定时配置未关联 XXL-Job 任务，无法触发");
        }
        xxlJobAdminClient.triggerJob(config.getXxlJobId(), String.valueOf(config.getTaskId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeByTaskId(Long taskId) {
        List<ScheduleConfig> configs = scheduleConfigMapper.selectList(
                new LambdaQueryWrapper<ScheduleConfig>().eq(ScheduleConfig::getTaskId, taskId));
        for (ScheduleConfig config : configs) {
            removeXxlJobQuietly(config);
            scheduleConfigMapper.deleteById(config.getId());
        }
    }

    /** 删除调度中心任务（调度中心不可达时仅记录日志，不阻塞本地删除） */
    private void removeXxlJobQuietly(ScheduleConfig config) {
        if (config.getXxlJobId() == null) {
            return;
        }
        try {
            xxlJobAdminClient.removeJob(config.getXxlJobId());
        } catch (Exception e) {
            log.warn("移除 XXL-Job 任务[{}]失败(不影响本地删除): {}", config.getXxlJobId(), e.getMessage());
        }
    }

    private String buildJobDesc(ScheduleDTO dto, SyncTask task) {
        return dto.getJobDesc().trim() + " - " + task.getTaskName();
    }

    /** Cron 校验：使用 Spring CronExpression（与 XXL-Job 同为 6 位格式） */
    private String validateCron(String cronExpr) {
        String cron = cronExpr == null ? "" : cronExpr.trim().replaceAll("\\s+", " ");
        if (!CronExpression.isValidExpression(cron)) {
            throw new BusinessException("Cron 表达式不合法: [" + cron + "]，请使用 6 位格式（秒 分 时 日 月 周）");
        }
        return cron;
    }

    private ScheduleConfig getByIdNotNull(Long id) {
        ScheduleConfig config = scheduleConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("定时任务配置不存在或已删除");
        }
        return config;
    }

    private SyncTask getTaskNotNull(Long taskId) {
        SyncTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("同步任务不存在或已删除");
        }
        return task;
    }

    /** 实体 -> VO（填充任务名、下次执行时间、最近执行结果） */
    private ScheduleVO toVO(ScheduleConfig config) {
        ScheduleVO vo = new ScheduleVO();
        vo.setId(config.getId());
        vo.setTaskId(config.getTaskId());
        vo.setCronExpr(config.getCronExpr());
        vo.setJobDesc(config.getJobDesc());
        vo.setOwner(config.getOwner());
        vo.setXxlJobId(config.getXxlJobId());
        vo.setStatus(config.getStatus());
        vo.setCreateTime(config.getCreateTime());
        vo.setUpdateTime(config.getUpdateTime());
        // 任务名
        SyncTask task = taskMapper.selectById(config.getTaskId());
        vo.setTaskName(task == null ? "(任务已删除)" : task.getTaskName());
        // 下次执行时间：按 Cron 计算（ENABLED 状态）
        if ("ENABLED".equals(config.getStatus()) && config.getCronExpr() != null) {
            try {
                CronExpression expression = CronExpression.parse(config.getCronExpr());
                LocalDateTime next = expression.next(LocalDateTime.now());
                vo.setNextTriggerTime(next);
            } catch (Exception ignored) {
                // 非法 Cron 已在保存时拦截，此处兜底不抛错
            }
        }
        // 最近一次执行结果
        SyncRecord latest = recordMapper.selectOne(new LambdaQueryWrapper<SyncRecord>()
                .eq(SyncRecord::getTaskId, config.getTaskId())
                .orderByDesc(SyncRecord::getId)
                .last("LIMIT 1"));
        if (latest != null) {
            vo.setLastStatus(latest.getStatus());
            vo.setLastEndTime(latest.getEndTime());
        }
        return vo;
    }
}
