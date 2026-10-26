package com.datapivot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.datapivot.common.BusinessException;
import com.datapivot.common.PageResult;
import com.datapivot.datasource.ConnectionFactory;
import com.datapivot.datasource.DynamicDataSourceManager;
import com.datapivot.dto.DbConnectionDTO;
import com.datapivot.entity.DbConnection;
import com.datapivot.entity.SyncTask;
import com.datapivot.enums.DbTypeEnum;
import com.datapivot.mapper.DbConnectionMapper;
import com.datapivot.mapper.SyncTaskMapper;
import com.datapivot.service.CryptoService;
import com.datapivot.service.DbConnectionService;
import com.datapivot.vo.DbConnectionVO;
import com.datapivot.vo.TestResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据库连接管理服务实现
 */
@Service
@RequiredArgsConstructor
public class DbConnectionServiceImpl implements DbConnectionService {

    /** 测试连接超时秒数 */
    private static final int TEST_TIMEOUT_SECONDS = 8;

    private final DbConnectionMapper connectionMapper;
    private final SyncTaskMapper syncTaskMapper;
    private final CryptoService cryptoService;
    private final DynamicDataSourceManager dataSourceManager;

    @Override
    public PageResult<DbConnectionVO> page(long current, long size, String keyword) {
        LambdaQueryWrapper<DbConnection> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(DbConnection::getConnName, keyword)
                    .or().like(DbConnection::getHost, keyword)
                    .or().like(DbConnection::getDbName, keyword));
        }
        wrapper.orderByDesc(DbConnection::getId);
        Page<DbConnection> page = connectionMapper.selectPage(new Page<>(current, size), wrapper);
        List<DbConnectionVO> records = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize(), vo -> vo);
    }

    @Override
    public List<DbConnectionVO> listAll() {
        return connectionMapper.selectList(new LambdaQueryWrapper<DbConnection>()
                        .orderByDesc(DbConnection::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public DbConnectionVO getDetail(Long id) {
        return toVO(getByIdNotNull(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(DbConnectionDTO dto) {
        DbTypeEnum dbType = DbTypeEnum.fromCode(dto.getDbType());
        if (!StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException("新建连接必须填写密码");
        }
        // 连接名称唯一性校验（含逻辑删除外的数据）
        checkNameUnique(dto.getConnName(), null);
        DbConnection conn = new DbConnection();
        applyDto(conn, dto, dbType);
        conn.setPassword(cryptoService.encrypt(dto.getPassword()));
        connectionMapper.insert(conn);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DbConnectionDTO dto) {
        DbTypeEnum dbType = DbTypeEnum.fromCode(dto.getDbType());
        DbConnection exist = getByIdNotNull(id);
        checkNameUnique(dto.getConnName(), id);
        applyDto(exist, dto, dbType);
        // 密码留空表示沿用原密码；填写则重新加密
        if (StringUtils.hasText(dto.getPassword())) {
            exist.setPassword(cryptoService.encrypt(dto.getPassword()));
        }
        connectionMapper.updateById(exist);
        // 配置已变化，驱逐旧连接池
        dataSourceManager.evict(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getByIdNotNull(id);
        // 被同步任务引用的连接禁止删除，避免任务悬空
        Long usedCount = syncTaskMapper.selectCount(new LambdaQueryWrapper<SyncTask>()
                .eq(SyncTask::getSourceConnId, id).or().eq(SyncTask::getTargetConnId, id));
        if (usedCount != null && usedCount > 0) {
            throw new BusinessException("该连接已被同步任务引用，请先删除或修改相关同步任务");
        }
        connectionMapper.deleteById(id);
        dataSourceManager.evict(id);
    }

    @Override
    public TestResultVO testById(Long id) {
        DbConnection conn = getByIdNotNull(id);
        String plainPassword = cryptoService.decrypt(conn.getPassword());
        return doTest(conn.getJdbcUrl(), conn.getUsername(), plainPassword);
    }

    @Override
    public TestResultVO testByDto(DbConnectionDTO dto) {
        DbTypeEnum.fromCode(dto.getDbType());
        if (!StringUtils.hasText(dto.getJdbcUrl())) {
            throw new BusinessException("JDBC URL 不能为空");
        }
        return doTest(dto.getJdbcUrl(), dto.getUsername(), dto.getPassword() == null ? "" : dto.getPassword());
    }

    @Override
    public DbConnection getByIdNotNull(Long id) {
        if (id == null) {
            throw new BusinessException("连接 id 不能为空");
        }
        DbConnection conn = connectionMapper.selectById(id);
        if (conn == null) {
            throw new BusinessException("数据库连接不存在或已删除");
        }
        return conn;
    }

    /** 真正执行连通性测试：建连 + isValid + 元数据探测 */
    private TestResultVO doTest(String jdbcUrl, String username, String password) {
        long start = System.currentTimeMillis();
        TestResultVO vo = new TestResultVO();
        try (Connection connection = ConnectionFactory.openRawConnection(jdbcUrl, username, password,
                TEST_TIMEOUT_SECONDS)) {
            boolean valid = connection.isValid(3);
            DatabaseMetaData metaData = connection.getMetaData();
            String product = metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion();
            vo.setSuccess(valid);
            vo.setDbProduct(product);
            vo.setMessage(valid ? "连接成功" : "连接已建立但有效性校验失败");
        } catch (SQLException e) {
            vo.setSuccess(false);
            vo.setMessage("连接失败: " + e.getMessage());
        }
        vo.setCostMs(System.currentTimeMillis() - start);
        return vo;
    }

    /** DTO -> 实体（密码字段单独处理） */
    private void applyDto(DbConnection conn, DbConnectionDTO dto, DbTypeEnum dbType) {
        conn.setConnName(dto.getConnName().trim());
        conn.setDbType(dbType.getCode());
        conn.setHost(dto.getHost().trim());
        conn.setPort(dto.getPort());
        conn.setDbName(dto.getDbName().trim());
        conn.setUsername(dto.getUsername().trim());
        conn.setDriverClass(StringUtils.hasText(dto.getDriverClass())
                ? dto.getDriverClass().trim() : dbType.getDefaultDriver());
        conn.setJdbcUrl(dto.getJdbcUrl().trim());
        conn.setExtraParams(dto.getExtraParams());
        conn.setRemark(dto.getRemark());
    }

    private void checkNameUnique(String connName, Long excludeId) {
        Long count = connectionMapper.selectCount(new LambdaQueryWrapper<DbConnection>()
                .eq(DbConnection::getConnName, connName)
                .ne(excludeId != null, DbConnection::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("连接名称 [" + connName + "] 已存在，请更换名称");
        }
    }

    /** 实体 -> VO（密码脱敏，绝不返回明文/密文） */
    private DbConnectionVO toVO(DbConnection conn) {
        DbConnectionVO vo = new DbConnectionVO();
        vo.setId(conn.getId());
        vo.setConnName(conn.getConnName());
        vo.setDbType(conn.getDbType());
        vo.setDbTypeLabel(DbTypeEnum.fromCodeOrNull(conn.getDbType()) == null
                ? conn.getDbType() : DbTypeEnum.fromCode(conn.getDbType()).getLabel());
        vo.setHost(conn.getHost());
        vo.setPort(conn.getPort());
        vo.setDbName(conn.getDbName());
        vo.setUsername(conn.getUsername());
        // 仅展示脱敏形式（解密后脱敏，长度信息保真）
        vo.setPasswordMasked(cryptoService.mask(cryptoService.decrypt(conn.getPassword())));
        vo.setDriverClass(conn.getDriverClass());
        vo.setJdbcUrl(conn.getJdbcUrl());
        vo.setExtraParams(conn.getExtraParams());
        vo.setRemark(conn.getRemark());
        vo.setCreateTime(conn.getCreateTime());
        vo.setUpdateTime(conn.getUpdateTime());
        return vo;
    }
}
