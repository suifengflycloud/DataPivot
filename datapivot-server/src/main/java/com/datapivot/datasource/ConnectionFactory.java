package com.datapivot.datasource;

import com.datapivot.entity.DbConnection;
import com.datapivot.enums.DbTypeEnum;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 连接工厂：创建动态数据源连接池 / 一次性测试连接。
 * <p>项目自身配置库使用 Druid（见 application.yml）；源库/目标库均为业务库，
 * 运行时按连接配置动态创建 HikariCP 连接池，由 {@link DynamicDataSourceManager} 统一缓存管理。</p>
 */
public final class ConnectionFactory {

    private static final int MAX_POOL_SIZE = 5;
    private static final long CONNECTION_TIMEOUT_MS = 8000;

    /** 测试连接兜底线程池（守护线程，防网络黑洞导致请求长时间挂起） */
    private static final ExecutorService CONNECT_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "conn-tester");
        thread.setDaemon(true);
        return thread;
    });

    private ConnectionFactory() {
    }

    /**
     * 按连接配置创建 HikariCP 数据源（不缓存，由调用方管理生命周期）
     *
     * @param conn            连接配置
     * @param plainPassword   解密后的明文密码
     * @param dbType          数据库类型（用于池命名）
     */
    public static HikariDataSource createHikariDataSource(DbConnection conn, String plainPassword, DbTypeEnum dbType) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("dp-dynamic-" + dbType.getCode().toLowerCase() + "-" + conn.getId());
        config.setJdbcUrl(conn.getJdbcUrl());
        config.setUsername(conn.getUsername());
        config.setPassword(plainPassword);
        if (conn.getDriverClass() != null && !conn.getDriverClass().isBlank()) {
            config.setDriverClassName(conn.getDriverClass());
        }
        // 业务库只需小池：元数据读取与批量同步均为短连接复用场景
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        // JDBC4 isValid 校验，无需各方言 test query
        return new HikariDataSource(config);
    }

    /**
     * 创建一次性原生连接（用于"测试连接"，不入池）。
     * 采用异步 + 超时兜底：部分驱动无法通过 URL 参数控制建连超时。
     *
     * @param timeoutSeconds 建连超时秒数
     * @throws SQLException 超时或驱动层连接失败
     */
    public static Connection openRawConnection(String jdbcUrl, String username, String plainPassword,
                                               int timeoutSeconds) throws SQLException {
        Future<Connection> future = CONNECT_EXECUTOR.submit(
                () -> DriverManager.getConnection(jdbcUrl, username, plainPassword));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new SQLException("连接超时（超过 " + timeoutSeconds + " 秒），请检查 IP/端口/防火墙配置");
        } catch (java.util.concurrent.ExecutionException e) {
            future.cancel(true);
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof SQLException sqlException) {
                throw sqlException;
            }
            throw new SQLException(cause.getMessage() == null ? "连接失败" : cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("连接操作被中断");
        }
    }
}
