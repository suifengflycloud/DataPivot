package com.datapivot.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * XXL-Job 执行器配置
 * <p>本项目作为执行器注册到 XXL-Job 调度中心；调度中心不可达时以"空注册地址"启动本地执行器，
 * 避免周期性注册失败刷 ERROR 日志，仅提示一条明确 WARN。</p>
 */
@Slf4j
@Configuration
public class XxlJobConfig {

    @Value("${xxl.job.enabled:true}")
    private boolean enabled;

    @Value("${xxl.job.admin.addresses:}")
    private String adminAddresses;

    @Value("${xxl.job.accessToken:}")
    private String accessToken;

    @Value("${xxl.job.executor.appname:datapivot-executor}")
    private String appname;

    @Value("${xxl.job.executor.port:9999}")
    private int port;

    @Value("${xxl.job.executor.logpath:./logs/xxl-job}")
    private String logPath;

    @Value("${xxl.job.executor.logretentiondays:30}")
    private int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor xxlJobSpringExecutor() {
        if (!enabled || adminAddresses == null || adminAddresses.isBlank()) {
            log.info("XXL-Job 执行器未启用（xxl.job.enabled=false 或未配置调度中心地址），跳过执行器初始化");
            return null;
        }
        if (!adminReachable(adminAddresses)) {
            // 调度中心不可达：完全不初始化执行器（避免 xxl-job 内核周期性注册失败产生 ERROR/WARN 噪音）
            log.warn("XXL-Job 调度中心[{}]不可达，本次启动不初始化执行器。"
                    + "如需定时任务功能：请先启动调度中心（默认 http://127.0.0.1:8280/xxl-job-admin），"
                    + "再重启本服务；或通过环境变量 XXL_JOB_ADMIN_ADDRESSES 指定调度中心地址", adminAddresses);
            return null;
        }
        log.info("XXL-Job 调度中心[{}]可达，执行器[{}]注册中，端口: {}", adminAddresses, appname, port);
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAccessToken(accessToken);
        executor.setAppname(appname);
        executor.setPort(port);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }

    /**
     * 探测调度中心是否可达：任意 HTTP 响应（200/302/404）即视为可达，仅连接失败视为不可达
     */
    private boolean adminReachable(String addresses) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(addresses.replaceAll("/+$", "") + "/toLogin");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            return code > 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
