package com.datapivot.service;

import com.datapivot.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * XXL-Job 调度中心管理端 REST 客户端
 * <p>用于在用户保存/修改定时配置时，自动在调度中心创建、更新、启停、触发、删除任务，
 * 免去人工在控制台逐个录入。鉴权方式：模拟登录拿取 Cookie（XXL_JOB_LOGIN_IDENTITY）。</p>
 */
@Slf4j
@Service
public class XxlJobAdminClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String username;
    private final String password;
    private final RestTemplate restTemplate;

    /** 登录态 Cookie（登录成功后缓存，失效自动重登） */
    private volatile String cookieValue;

    public XxlJobAdminClient(@Value("${xxl.job.admin.addresses:}") String addresses,
                             @Value("${xxl.job.admin.username:admin}") String username,
                             @Value("${xxl.job.admin.password:123456}") String password) {
        this.baseUrl = addresses == null ? "" : addresses.replaceAll("/+$", "");
        this.username = username;
        this.password = password;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 按执行器 AppName 查询调度中心中的执行器分组 id
     * <p>分组不存在时自动创建（自动注册模式），执行器随后会把自身地址注册进来。</p>
     */
    public int queryJobGroupId(String appname) {
        int found = findJobGroupId(appname);
        if (found > 0) {
            return found;
        }
        createJobGroup(appname);
        found = findJobGroupId(appname);
        if (found > 0) {
            return found;
        }
        throw new BusinessException("已自动创建执行器分组但查询未生效，请在调度中心【执行器管理】中确认 AppName=["
                + appname + "] 的执行器状态");
    }

    /** 查询执行器分组 id，不存在返回 0 */
    private int findJobGroupId(String appname) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("start", "0");
        form.add("length", "50");
        form.add("appname", appname);
        JsonNode result = post("/jobgroup/pageList", form);
        JsonNode data = result.path("data");
        if (data.isArray()) {
            for (JsonNode item : data) {
                if (appname.equals(item.path("appname").asText())) {
                    return item.path("id").asInt();
                }
            }
        }
        return 0;
    }

    /** 自动创建执行器分组（地址类型=自动注册，执行器上线后自动填充地址） */
    private void createJobGroup(String appname) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("appname", appname);
        // xxl_job_group.title 列仅 VARCHAR(12)，标题必须 ≤12 字符
        form.add("title", "DataPivot");
        form.add("addressType", "0");
        form.add("addressList", "");
        post("/jobgroup/save", form);
        log.info("XXL-Job 执行器分组 [{}] 不存在，已自动创建", appname);
    }

    /**
     * 新建调度任务，返回任务 id
     */
    public int addJob(int jobGroupId, String jobDesc, String author, String cron, String executorHandler,
                      String executorParam) {
        MultiValueMap<String, String> form = baseJobForm(jobGroupId, jobDesc, author, cron, executorHandler,
                executorParam);
        form.add("id", "0");
        JsonNode result = post("/jobinfo/add", form);
        return Integer.parseInt(result.path("content").asText());
    }

    /**
     * 更新调度任务（Cron / 描述 / 负责人）
     */
    public void updateJob(int jobId, int jobGroupId, String jobDesc, String author, String cron,
                          String executorHandler, String executorParam) {
        MultiValueMap<String, String> form = baseJobForm(jobGroupId, jobDesc, author, cron, executorHandler,
                executorParam);
        form.add("id", String.valueOf(jobId));
        post("/jobinfo/update", form);
    }

    /** 删除调度任务 */
    public void removeJob(int jobId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", String.valueOf(jobId));
        post("/jobinfo/remove", form);
    }

    /** 启动任务 */
    public void startJob(int jobId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", String.valueOf(jobId));
        post("/jobinfo/start", form);
    }

    /** 停止（暂停）任务 */
    public void stopJob(int jobId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", String.valueOf(jobId));
        post("/jobinfo/stop", form);
    }

    /** 手动触发一次 */
    public void triggerJob(int jobId, String executorParam) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", String.valueOf(jobId));
        form.add("executorParam", executorParam);
        form.add("addressList", "");
        post("/jobinfo/trigger", form);
    }

    /** 组装任务表单公共字段（路由/阻塞/超时等均取保守默认值） */
    private MultiValueMap<String, String> baseJobForm(int jobGroupId, String jobDesc, String author, String cron,
                                                      String executorHandler, String executorParam) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("jobGroup", String.valueOf(jobGroupId));
        form.add("jobDesc", jobDesc);
        form.add("author", author);
        form.add("alarmEmail", "");
        form.add("scheduleType", "CRON");
        form.add("scheduleConf", cron);
        form.add("schedule_conf_CRON", cron);
        form.add("schedule_conf_FIX", "");
        form.add("glueType", "BEAN");
        form.add("executorHandler", executorHandler);
        form.add("executorParam", executorParam);
        form.add("executorRouteStrategy", "FIRST");
        form.add("childJobId", "");
        form.add("misfireStrategy", "DO_NOTHING");
        form.add("executorBlockStrategy", "SERIAL_EXECUTION");
        form.add("executorTimeout", "0");
        form.add("executorFailRetryCount", "0");
        form.add("glueRemark", "GLUE代码初始化");
        form.add("glueSource", "");
        return form;
    }

    /** 调用管理端接口（登录失效自动重试一次） */
    private JsonNode post(String path, MultiValueMap<String, String> form) {
        if (baseUrl.isBlank()) {
            throw new BusinessException("未配置 XXL-Job 调度中心地址，无法操作定时任务");
        }
        ensureLogin();
        try {
            ResponseEntity<String> response = doPost(path, form);
            JsonNode node = MAPPER.readTree(response.getBody());
            // /jobgroup/pageList 等接口返回 DataTables 分页结构（无 code 字段），仅对 ReturnT 结构校验业务码
            if (node.hasNonNull("code") && node.get("code").asInt() != 200) {
                String msg = node.path("msg").asText("调度中心返回失败");
                // 登录态失效时重登一次再试
                if (msg.contains("登录") || msg.contains("login")) {
                    this.cookieValue = null;
                    login();
                    node = MAPPER.readTree(doPost(path, form).getBody());
                    if (node.hasNonNull("code") && node.get("code").asInt() != 200) {
                        throw new BusinessException("XXL-Job 调度中心操作失败: " + node.path("msg").asText());
                    }
                } else {
                    throw new BusinessException("XXL-Job 调度中心操作失败: " + msg);
                }
            }
            return node;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("无法连接 XXL-Job 调度中心（" + baseUrl + "），请确认调度中心已启动: "
                    + e.getMessage());
        }
    }

    private ResponseEntity<String> doPost(String path, MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (cookieValue != null) {
            headers.add(HttpHeaders.COOKIE, "XXL_JOB_LOGIN_IDENTITY=" + cookieValue);
        }
        return restTemplate.postForEntity(baseUrl + path, new HttpEntity<>(form, headers), String.class);
    }

    /** 模拟登录并缓存 Cookie */
    private void login() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("userName", username);
        form.add("password", password);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + "/login",
                    new HttpEntity<>(form, headers), String.class);
            JsonNode node = MAPPER.readTree(response.getBody());
            if (node.path("code").asInt() != 200) {
                throw new BusinessException("XXL-Job 调度中心登录失败，请检查 xxl.job.admin.username/password 配置");
            }
            List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
            if (cookies == null || cookies.isEmpty()) {
                throw new BusinessException("XXL-Job 调度中心登录响应缺少 Cookie");
            }
            for (String cookie : cookies) {
                if (cookie.startsWith("XXL_JOB_LOGIN_IDENTITY=")) {
                    this.cookieValue = cookie.substring("XXL_JOB_LOGIN_IDENTITY=".length()).split(";")[0];
                    return;
                }
            }
            throw new BusinessException("XXL-Job 调度中心登录响应缺少身份 Cookie");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("无法连接 XXL-Job 调度中心（" + baseUrl + "），请确认调度中心已启动");
        }
    }

    /** 确保已登录（首次调用任何接口前触发） */
    private void ensureLogin() {
        if (cookieValue == null) {
            login();
        }
    }

    /** 供定时任务保存前主动校验连通性 */
    public void checkAlive() {
        cookieValue = null;
        ensureLogin();
    }
}
