package com.datapivot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.datapivot.common.BusinessException;
import com.datapivot.common.PageResult;
import com.datapivot.dto.SyncTaskDTO;
import com.datapivot.dto.TableMappingDTO;
import com.datapivot.entity.ColumnMapping;
import com.datapivot.entity.DbConnection;
import com.datapivot.entity.SyncTask;
import com.datapivot.entity.TableMapping;
import com.datapivot.enums.ConflictStrategyEnum;
import com.datapivot.mapper.ColumnMappingMapper;
import com.datapivot.mapper.DbConnectionMapper;
import com.datapivot.mapper.SyncTaskMapper;
import com.datapivot.mapper.TableMappingMapper;
import com.datapivot.service.DbConnectionService;
import com.datapivot.service.ScheduleService;
import com.datapivot.service.SyncTaskService;
import com.datapivot.vo.SyncTaskDetailVO;
import com.datapivot.vo.SyncTaskVO;
import com.datapivot.vo.TableMappingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 同步任务配置服务实现
 */
@Service
@RequiredArgsConstructor
public class SyncTaskServiceImpl implements SyncTaskService {

    private final SyncTaskMapper taskMapper;
    private final TableMappingMapper tableMappingMapper;
    private final ColumnMappingMapper columnMappingMapper;
    private final DbConnectionMapper connectionMapper;
    private final DbConnectionService connectionService;
    private final ScheduleService scheduleService;

    @Override
    public PageResult<SyncTaskVO> page(long current, long size, String keyword) {
        LambdaQueryWrapper<SyncTask> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SyncTask::getTaskName, keyword);
        }
        wrapper.orderByDesc(SyncTask::getId);
        Page<SyncTask> page = taskMapper.selectPage(new Page<>(current, size), wrapper);
        List<SyncTaskVO> records = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        fillConnNames(records);
        fillMappingCount(records);
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize(), vo -> vo);
    }

    @Override
    public List<SyncTaskVO> listAll() {
        List<SyncTaskVO> list = taskMapper.selectList(new LambdaQueryWrapper<SyncTask>()
                        .orderByDesc(SyncTask::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
        fillConnNames(list);
        return list;
    }

    @Override
    public SyncTaskDetailVO getDetail(Long id) {
        SyncTask task = getByIdNotNull(id);
        SyncTaskDetailVO detail = new SyncTaskDetailVO();
        copyTaskFields(detail, task);
        detail.setConflictStrategyLabel(ConflictStrategyEnum.fromCode(task.getConflictStrategy()).getLabel());
        List<TableMapping> mappings = tableMappingMapper.selectList(
                new LambdaQueryWrapper<TableMapping>().eq(TableMapping::getTaskId, id));
        List<TableMappingVO> mappingVOs = new ArrayList<>();
        for (TableMapping mapping : mappings) {
            TableMappingVO mappingVO = new TableMappingVO();
            mappingVO.setId(mapping.getId());
            mappingVO.setSourceCatalog(mapping.getSourceCatalog());
            mappingVO.setSourceSchema(mapping.getSourceSchema());
            mappingVO.setSourceTable(mapping.getSourceTable());
            mappingVO.setSourceDisplayName(buildDisplayName(mapping.getSourceSchema(), mapping.getSourceTable()));
            mappingVO.setTargetCatalog(mapping.getTargetCatalog());
            mappingVO.setTargetSchema(mapping.getTargetSchema());
            mappingVO.setTargetTable(mapping.getTargetTable());
            mappingVO.setTargetDisplayName(buildDisplayName(mapping.getTargetSchema(), mapping.getTargetTable()));
            columnMappingMapper.selectList(new LambdaQueryWrapper<ColumnMapping>()
                            .eq(ColumnMapping::getMappingId, mapping.getId()))
                    .forEach(cm -> {
                        TableMappingVO.ColumnMappingVO columnVO = new TableMappingVO.ColumnMappingVO();
                        columnVO.setSourceColumn(cm.getSourceColumn());
                        columnVO.setTargetColumn(cm.getTargetColumn());
                        mappingVO.getColumns().add(columnVO);
                    });
            mappingVOs.add(mappingVO);
        }
        detail.setMappings(mappingVOs);
        // 填充连接名
        List<SyncTaskVO> single = new ArrayList<>();
        single.add(detail);
        fillConnNames(single);
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(SyncTaskDTO dto) {
        validate(dto);
        SyncTask task = new SyncTask();
        applyDto(task, dto);
        taskMapper.insert(task);
        saveMappings(task.getId(), dto.getMappings());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SyncTaskDTO dto) {
        getByIdNotNull(id);
        validate(dto);
        SyncTask task = new SyncTask();
        task.setId(id);
        applyDto(task, dto);
        taskMapper.updateById(task);
        // 映射配置整体替换：先删旧再插新（子表物理删除）
        tableMappingMapper.delete(new LambdaQueryWrapper<TableMapping>().eq(TableMapping::getTaskId, id));
        saveMappings(id, dto.getMappings());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getByIdNotNull(id);
        // 级联删除定时配置（同步移除调度中心任务）
        scheduleService.removeByTaskId(id);
        tableMappingMapper.delete(new LambdaQueryWrapper<TableMapping>().eq(TableMapping::getTaskId, id));
        taskMapper.deleteById(id);
    }

    /** 保存表映射与字段映射 */
    private void saveMappings(Long taskId, List<TableMappingDTO> mappingDTOs) {
        for (TableMappingDTO mappingDTO : mappingDTOs) {
            TableMapping mapping = new TableMapping();
            mapping.setTaskId(taskId);
            mapping.setSourceCatalog(mappingDTO.getSourceCatalog());
            mapping.setSourceSchema(mappingDTO.getSourceSchema());
            mapping.setSourceTable(mappingDTO.getSourceTable());
            mapping.setTargetCatalog(mappingDTO.getTargetCatalog());
            mapping.setTargetSchema(mappingDTO.getTargetSchema());
            mapping.setTargetTable(mappingDTO.getTargetTable());
            tableMappingMapper.insert(mapping);
            for (var columnDTO : mappingDTO.getColumns()) {
                ColumnMapping columnMapping = new ColumnMapping();
                columnMapping.setMappingId(mapping.getId());
                columnMapping.setSourceColumn(columnDTO.getSourceColumn());
                columnMapping.setTargetColumn(columnDTO.getTargetColumn());
                columnMappingMapper.insert(columnMapping);
            }
        }
    }

    /** 保存前校验：连接存在、源目标不同连接、映射无重复 */
    private void validate(SyncTaskDTO dto) {
        connectionService.getByIdNotNull(dto.getSourceConnId());
        connectionService.getByIdNotNull(dto.getTargetConnId());
        ConflictStrategyEnum.fromCode(dto.getConflictStrategy());
        // 同一组映射内字段映射不得重复
        for (TableMappingDTO mapping : dto.getMappings()) {
            long distinct = mapping.getColumns().stream()
                    .map(c -> c.getSourceColumn().toLowerCase() + "->" + c.getTargetColumn().toLowerCase())
                    .distinct().count();
            if (distinct != mapping.getColumns().size()) {
                throw new BusinessException("表映射 [" + mapping.getSourceTable() + " → "
                        + mapping.getTargetTable() + "] 存在重复的字段映射");
            }
        }
    }

    private void applyDto(SyncTask task, SyncTaskDTO dto) {
        task.setTaskName(dto.getTaskName().trim());
        task.setSourceConnId(dto.getSourceConnId());
        task.setTargetConnId(dto.getTargetConnId());
        task.setConflictStrategy(ConflictStrategyEnum.fromCode(dto.getConflictStrategy()).getCode());
        task.setBatchSize(dto.getBatchSize());
        task.setFullSync(Boolean.TRUE.equals(dto.getFullSync()) ? 1 : 0);
        task.setRemark(dto.getRemark());
    }

    private SyncTask getByIdNotNull(Long id) {
        SyncTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("同步任务不存在或已删除");
        }
        return task;
    }

    private SyncTaskVO toVO(SyncTask task) {
        SyncTaskVO vo = new SyncTaskVO();
        copyTaskFields(vo, task);
        vo.setConflictStrategyLabel(ConflictStrategyEnum.fromCode(task.getConflictStrategy()).getLabel());
        return vo;
    }

    private void copyTaskFields(SyncTaskVO vo, SyncTask task) {
        vo.setId(task.getId());
        vo.setTaskName(task.getTaskName());
        vo.setSourceConnId(task.getSourceConnId());
        vo.setTargetConnId(task.getTargetConnId());
        vo.setConflictStrategy(task.getConflictStrategy());
        vo.setBatchSize(task.getBatchSize());
        vo.setFullSync(task.getFullSync() != null && task.getFullSync() == 1);
        vo.setRemark(task.getRemark());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
    }

    /** 批量填充源/目标连接名称 */
    private void fillConnNames(List<SyncTaskVO> vos) {
        if (vos.isEmpty()) {
            return;
        }
        List<Long> connIds = vos.stream()
                .flatMap(vo -> java.util.stream.Stream.of(vo.getSourceConnId(), vo.getTargetConnId()))
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (connIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = connectionMapper.selectBatchIds(connIds).stream()
                .collect(Collectors.toMap(DbConnection::getId, DbConnection::getConnName));
        vos.forEach(vo -> {
            vo.setSourceConnName(nameMap.get(vo.getSourceConnId()));
            vo.setTargetConnName(nameMap.get(vo.getTargetConnId()));
        });
    }

    /** 批量填充映射数量 */
    private void fillMappingCount(List<SyncTaskVO> vos) {
        if (vos.isEmpty()) {
            return;
        }
        List<Long> taskIds = vos.stream().map(SyncTaskVO::getId).toList();
        Map<Long, Long> countMap = tableMappingMapper.selectList(new LambdaQueryWrapper<TableMapping>()
                        .in(TableMapping::getTaskId, taskIds))
                .stream().collect(Collectors.groupingBy(TableMapping::getTaskId, Collectors.counting()));
        vos.forEach(vo -> vo.setMappingCount(countMap.getOrDefault(vo.getId(), 0L)));
    }

    private String buildDisplayName(String schema, String table) {
        return StringUtils.hasText(schema) ? schema + "." + table : table;
    }
}
