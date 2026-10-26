package com.datapivot.datasource;

import com.datapivot.entity.DbConnection;
import com.datapivot.enums.DbTypeEnum;
import com.datapivot.service.CryptoService;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源管理器：按连接 id 缓存源库/目标库的 HikariCP 连接池。
 * <p>连接配置（URL/用户名/密码）变化时自动重建连接池；删除连接时主动驱逐；
 * 应用关闭时统一释放全部池，避免连接泄漏。</p>
 */
@Slf4j
@Component
public class DynamicDataSourceManager implements DisposableBean {

    private final CryptoService cryptoService;

    /** 连接池缓存：key = 连接配置 id */
    private final Map<Long, PoolEntry> poolCache = new ConcurrentHashMap<>();

    public DynamicDataSourceManager(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    /**
     * 获取业务库连接（池化，用完由调用方 close 归还池中）
     */
    public Connection getConnection(DbConnection conn) throws SQLException {
        DbTypeEnum dbType = DbTypeEnum.fromCode(conn.getDbType());
        PoolEntry entry = poolCache.compute(conn.getId(), (id, old) -> {
            // 配置签名一致且池仍存活则复用，否则关闭旧池并重建
            if (old != null && old.signature.equals(signature(conn)) && !old.dataSource.isClosed()) {
                return old;
            }
            if (old != null) {
                closeQuietly(old.dataSource);
            }
            log.info("为连接[{}]({})创建数据源池, url: {}", conn.getConnName(), dbType.getLabel(), conn.getJdbcUrl());
            HikariDataSource ds = ConnectionFactory.createHikariDataSource(conn, cryptoService.decrypt(conn.getPassword()), dbType);
            return new PoolEntry(signature(conn), ds);
        });
        return entry.dataSource.getConnection();
    }

    /** 连接配置被修改/删除时驱逐对应连接池 */
    public void evict(Long connectionId) {
        PoolEntry removed = poolCache.remove(connectionId);
        if (removed != null) {
            closeQuietly(removed.dataSource);
        }
    }

    @Override
    public void destroy() {
        poolCache.values().forEach(entry -> closeQuietly(entry.dataSource));
        poolCache.clear();
    }

    private void closeQuietly(HikariDataSource ds) {
        try {
            ds.close();
        } catch (Exception e) {
            log.warn("关闭动态数据源池失败: {}", e.getMessage());
        }
    }

    /** 配置签名：URL + 用户名 + 密码密文（任一变化即重建连接池，防陈旧池） */
    private String signature(DbConnection conn) {
        return conn.getJdbcUrl() + "|" + conn.getUsername() + "|" + conn.getPassword();
    }

    /** 池缓存条目 */
    private record PoolEntry(String signature, HikariDataSource dataSource) {
    }
}
