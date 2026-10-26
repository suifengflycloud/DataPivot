# DataPivot - 多源数据库迁移与结构对比工具

一套前后端分离的企业级数据库工具：支持 **MySQL / Oracle / 达梦DM / 人大金仓KingbaseES / PostgreSQL** 五种数据库之间的**数据同步迁移**与**结构差异对比（自动生成 DDL 升级脚本）**，并集成 XXL-Job 实现定时调度。

```
┌─────────────────────────┐        ┌──────────────────────────────┐
│  datapivot-web (5173)   │  /api  │  datapivot-server (8081)      │
│  Vue3 + TS + ElementPlus│ ─────► │  Spring Boot3 + MyBatis-Plus  │
└─────────────────────────┘        └──────┬───────────────┬───────┘
                                          │ Druid         │ HikariCP动态池
                                   ┌──────▼─────┐  ┌──────▼──────────────┐
                                   │ 自身配置库   │  │ 源库A ⇄ 目标库B       │
                                   │ MySQL       │  │ MySQL/Oracle/DM/    │
                                   │ (datapivot) │  │ Kingbase/PostgreSQL │
                                   └─────────────┘  └─────────────────────┘
                                          ▲ XXL-Job 执行器注册(9999)
                                   ┌──────┴──────────────┐
                                   │ XXL-Job 调度中心 8280 │
                                   └─────────────────────┘
```

## 一、功能总览

| 模块 | 说明 |
| --- | --- |
| 连接管理 | 五种数据库连接的增删改查、连通性测试、元数据浏览（表清单 + 字段详情）；密码 AES-GCM 加密存储、接口脱敏返回 |
| 元数据拉取 | 全部使用 JDBC `DatabaseMetaData` 标准 API，不拼接任何系统表 SQL，天然规避方言差异 |
| 同步任务 | 源库→目标库多组表映射、双栏字段映射（支持字段名不一致、跨类型自动转换）、主键冲突策略（覆盖更新/跳过/报错中断）、批次大小、全量/增量（预留）开关；执行时进度条 + 实时日志 |
| 结构对比 | 两库表级/字段级差异（新增/删除/类型/长度/可空/主键/注释），按方言自动生成 DDL 升级脚本，页面高亮预览 + 下载 .sql |
| 定时任务 | 为同步任务配置 Cron（快捷周期可选），自动注册到 XXL-Job 调度中心，支持启停/立即触发/查看执行结果 |
| 执行记录 | 历史执行记录分页查询、成功/失败统计、完整执行日志查看 |

## 二、技术栈

- **后端**：JDK 17 · Spring Boot 3.3.4 · MyBatis-Plus 3.5.7 · Druid（自身配置库连接池）· HikariCP（动态业务库连接池）· Lombok · Spring Validation · XXL-Job 2.4.0
- **前端**：Vue 3.5 · Vite 5 · TypeScript 5.6 · Element Plus · Pinia · Axios · Vue Router · ESLint 9(扁平配置) + Prettier
- **数据库驱动**：mysql-connector-j / ojdbc11 23.4 / DmJdbcDriver18 8.1.3.140 / kingbase8 9.0.1 / postgresql（均已验证存在于 Maven 中央仓库）

## 三、环境要求

| 组件 | 要求 | 本机说明 |
| --- | --- | --- |
| JDK | 17 | 本机路径：`D:\dev\java\jdk17\jdk-17.0.13_windows-x64_bin` |
| Maven | 3.6+（推荐 3.9.x） | 本机已有 `D:\dev\apache-maven-3.9.16` |
| Node.js | 18+（含 npm） | 本机 v25.8.0 / npm 11 |
| MySQL | 5.7+ 或 8.0 | 存放项目自身配置库（库名 `datapivot`） |
| XXL-Job 调度中心 | 2.4.x | 仅使用"定时任务"功能时需要，见下文部署说明 |

> 首次构建需联网下载 Maven / npm 依赖。

## 四、启动步骤（按顺序执行）

### 第 1 步：部署 XXL-Job 调度中心（仅需定时任务功能时）

调度中心只需部署一次，与本工具通过 HTTP 交互：

```bash
# 方式一：Docker 一键部署（推荐；映射到 8280 避免与本机 8080 冲突）
docker run -d --name xxl-job-admin -p 8280:8080 \
  -e PARAMS="--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&autoReconnect=true&serverTimezone=Asia/Shanghai" \
  xuxueli/xxl-job-admin:2.4.0

# 方式二：源码部署
git clone https://github.com/xuxueli/xxl-job.git
# 1) 执行 doc/db/tables_xxl_job.sql 初始化 xxl_job 库
# 2) 修改 xxl-job-admin/src/main/resources/application.properties 中数据库连接与端口
# 3) mvn spring-boot:run 启动
```

部署后：

1. 访问 `http://127.0.0.1:8280/xxl-job-admin`，默认账号 `admin / 123456`；
2. 【执行器管理】→ 新增执行器：AppName 填 `datapivot-executor`，名称随意，注册方式选"自动注册"；
3. 【系统设置】确认 AccessToken 为 `default_token`（与后端默认值一致，也可两边同时改）。

> 不启动调度中心不影响其它功能：后端会正常启动并给出一条明确提示，定时任务页面操作时会得到"无法连接调度中心"的友好报错。

### 第 2 步：初始化项目自身配置库

```bash
# 用本机 mysql 客户端执行初始化脚本（脚本幂等，可重复执行）
"D:\dev\mysql\mysql-5.7.28-winx64\bin\mysql.exe" -uroot -p < D:\code_cloud\DataPivot\datapivot-server\sql\datapivot_init.sql
```

脚本自动创建 `datapivot` 库与 6 张表（连接配置/同步任务/表映射/字段映射/执行记录/定时配置）。

### 第 3 步：启动后端（端口 8081）

```bash
# Windows (Git Bash / CMD / PowerShell) —— 本机 JDK17 路径
set JAVA_HOME=D:\dev\java\jdk17\jdk-17.0.13_windows-x64_bin   # CMD
# export JAVA_HOME='D:\dev\java\jdk17\jdk-17.0.13_windows-x64_bin'  # Git Bash

cd D:\code_cloud\DataPivot\datapivot-server

# 方式一：Maven 直接运行
mvn spring-boot:run

# 方式二：打包后运行
mvn clean package -DskipTests
java -jar target\datapivot-server-1.0.0.jar
```

看到 `Started DataPivotApplication` 即启动成功。数据库等连接信息默认值已适配本机（root/root），生产环境请用环境变量覆盖：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DP_DB_URL` | `jdbc:mysql://127.0.0.1:3306/datapivot?...` | 自身配置库地址 |
| `DP_DB_USER` / `DP_DB_PASSWORD` | `root` / `root` | 自身配置库账号 |
| `DP_AES_KEY` | `DataPivot@2026#Key` | 连接密码 AES 加密密钥（生产必改） |
| `XXL_JOB_ENABLED` | `true` | 定时任务总开关（false 则完全不初始化执行器） |
| `XXL_JOB_ADMIN_ADDRESSES` | `http://127.0.0.1:8280/xxl-job-admin` | 调度中心地址 |
| `XXL_JOB_ADMIN_USER` / `XXL_JOB_ADMIN_PASSWORD` | `admin` / `123456` | 调度中心登录账号 |
| `XXL_JOB_ACCESS_TOKEN` | `default_token` | 与调度中心一致的通信令牌 |
| `XXL_JOB_EXECUTOR_PORT` | `9999` | 执行器端口 |

### 第 4 步：启动前端（端口 5173）

```bash
cd D:\code_cloud\DataPivot\datapivot-web
npm install        # 首次执行
npm run dev        # 开发模式，自动代理 /api -> 127.0.0.1:8081
```

浏览器访问 `http://localhost:5173`。生产构建：`npm run build`（输出 `dist/`，可交给 Nginx，将 `/api` 反代到 8081）。

## 五、功能使用说明

### 1. 连接管理（/connection）

1. 点击【新增连接】，选择数据库类型（自动带出默认端口/驱动/JDBC URL），填写 IP、端口、库名、账号密码；
2. 【测试连接】验证连通性（返回数据库产品与版本或具体错误）；URL 支持手动修改，改过之后不再自动拼接，可点"重置"恢复；
3. 列表支持按名称/IP/库名搜索；【浏览】打开元数据浏览器：左侧表清单（表名+注释，可搜索分页），点击表名右侧展示字段详情（类型/长度/精度/主键/可空/默认值/注释）；
4. 密码密文存储，详情中仅显示脱敏形式；编辑时密码留空表示不修改。

### 2. 同步任务（/task）

1. 【新建任务】→ 选择源库 A 与目标库 B（选定后自动拉取两端表清单）；
2. 【表映射】添加多组"源表→目标表"（A1→B1、A2→B2...）；
3. 点【字段映射】配置双栏映射：左列勾选参与同步的源字段，右列下拉选择目标字段（默认同名自动匹配）；类型大类不一致时显示"类型自动转换"提示（字符串/数字/日期/布尔/二进制互转）；
4. 【高级配置】：主键冲突策略（覆盖更新/跳过/报错中断；前两者要求目标表有主键）、批次大小（默认 1000）、全量/增量开关（增量预留）；
5. 列表【执行】→ 实时进度弹窗（进度条、已处理/成功/失败计数、滚动日志），关闭弹窗不影响后台执行；
6. 执行结果进入「任务执行记录」页面。

### 3. 结构对比（/compare）

1. 选择基准库 A 与对比库 B，点击【开始对比】（两库类型不一致会有提示，DDL 按对比库方言生成）；
2. 查看汇总卡片（新增表/删除表/变更表/差异明细数）与按表分组的差异列表（绿色新增/红色删除/黄色修改）；
3. DDL 升级脚本自动生成（CREATE TABLE / ALTER ADD·MODIFY·DROP COLUMN / 注释语句；删表仅生成注释建议），支持高亮预览、一键复制、下载 .sql；
4. 只对比结构元信息，不读取业务数据。

### 4. 任务执行记录（/record）

按任务/状态筛选历史记录；查看单条记录的成功/失败计数、耗时与完整执行日志；支持删除。

### 5. 定时任务（/schedule）

1. 【新建定时任务】：选择同步任务 → 选择执行周期（每分钟/每小时/每天/每周/每月/自定义 6 位 Cron）→ 填写描述与负责人；
2. 保存后自动在 XXL-Job 调度中心创建并启动任务；也可在调度中心控制台查看状态、执行日志、手动触发、暂停/启动；
3. 页面展示下次执行时间（按 Cron 计算）与最近一次执行结果；支持立即触发。

## 六、安全设计说明

- **SQL 注入防护**：所有表名/字段名经白名单正则校验（`^[A-Za-z0-9_$#\u4e00-\u9fa5]+$`）+ 执行期与 JDBC 元数据双重核对；统一按方言加引号转义；数据值一律 `PreparedStatement` 占位符；元数据只走 `DatabaseMetaData` 标准 API。
- **密码安全**：AES-256-GCM 加密存储（随机 IV），接口只返回脱敏形式（前两位+****+后两位），日志不打印明文；密钥支持环境变量注入。
- **XSS 防护**：前端默认文本插值自动转义；唯一的 `v-html`（SQL 高亮组件）先对内容做完整 HTML 转义再注入高亮标签，并有注释说明与 ESLint 记录。
- **文件下载**：下载文件名由服务端生成并做白名单校验（`[A-Za-z0-9_\-.]`），杜绝路径穿越与响应头注入。
- **参数校验**：所有接口入参使用 Spring Validation（含嵌套对象），非法参数直接拦截返回友好提示。
- **配置安全**：数据库密码、XXL-Job 账号、AES 密钥均支持环境变量注入，不硬编码。

## 七、代码结构与规范

```
DataPivot
├── datapivot-server                          # 后端（Spring Boot 3 / JDK17）
│   ├── pom.xml                               # 依赖锁定 release=17，不受本机 Maven 全局配置影响
│   ├── sql/datapivot_init.sql                # 自身配置库初始化脚本（幂等）
│   └── src/main/java/com/datapivot
│       ├── common/                           # R 统一返回体 / PageResult / BusinessException
│       ├── config/                           # 全局异常处理 / MyBatis-Plus / CORS / XXL-Job 执行器
│       ├── enums/                            # 数据库类型/冲突策略/差异类型等枚举
│       ├── entity/ mapper/                   # 6 张配置表实体与 Mapper
│       ├── dto/ vo/                          # 请求（带校验注解）与响应对象
│       ├── datasource/                       # 动态数据源管理(HikariCP)/连接工厂/标识符安全工具
│       ├── service/                          # 接口 + impl 实现（连接/元数据/任务/执行/对比/DDL/定时/记录/统计）
│       │   └── support/                      # SQL构建器/类型转换器/进度对象
│       ├── controller/                       # 7 个 Controller（不写业务逻辑）
│       └── job/                              # XXL-Job 执行器 handler
└── datapivot-web                             # 前端（Vue3 + TS + Element Plus）
    └── src
        ├── api/ types/ constants/ utils/     # 接口层 / 类型契约 / 常量 / 工具
        ├── stores/                           # Pinia（连接列表共享状态）
        ├── layouts/                          # 深色侧边栏布局
        ├── components/SqlCode.vue            # SQL 高亮（转义后渲染）
        └── views/                            # 首页/连接管理/同步任务/结构对比/执行记录/定时任务
```

后端遵循阿里巴巴 Java 开发手册（大驼峰/小驼峰/分层单一职责）；前端遵循 Vue3 组合式 API + `<script setup lang="ts">`，全量类型定义、无 `any`。

## 八、常见问题

| 问题 | 说明 |
| --- | --- |
| 后端为什么用 8081 端口？ | 本机 8080 已被占用；如需修改：`server.port` 或启动参数 `--server.port=xxxx`，同时改前端 `vite.config.ts` 的代理目标 |
| 后端启动出现 XXL-Job WARN | 属预期行为：调度中心未启动时，执行器不初始化并给出一条明确指引；启动调度中心后重启后端即可；不需要定时功能可设 `XXL_JOB_ENABLED=false` |
| 编译报"不支持发行版本 21" | 本机全局 Maven settings.xml 强制了 `maven.compiler.release=21`；本项目 pom 已显式锁定 release=17，无需处理（此为其他项目的问题） |
| 定时任务保存报"无法连接调度中心" | 先按第 1 步部署调度中心，并确认【执行器管理】中已创建 AppName=`datapivot-executor` 的执行器 |
| 测试连接报"连接超时" | 检查目标库 IP/端口/防火墙；不同数据库默认端口：MySQL 3306 / Oracle 1521 / DM 5236 / Kingbase 54321 / PostgreSQL 5432 |
| 覆盖更新/跳过策略执行报错 | 该策略要求目标表有主键（upsert 依赖），可为目标表补主键或改用"报错中断" |
| MySQL 表注释不显示 | 已在默认 URL 中内置 `useInformationSchema=true`；旧连接请编辑 URL 补充该参数 |

## 九、自检记录

- 后端：`mvn clean package -DskipTests` → **BUILD SUCCESS**（JDK17 编译，可执行 jar 55MB 含全部驱动）；
- 前端：`npm run build`（vue-tsc 严格类型检查 + vite build）→ **0 error / 0 warning**；`npx eslint .` → **0 error / 0 warning**；
- 建表脚本在 MySQL 5.7.28 实际执行成功（幂等）；
- 运行时冒烟测试（本机 MySQL 5.7.28 真实数据）：连接增删改查/测试/脱敏 ✓、元数据表+字段拉取（含表/字段注释）✓、同步任务执行（5 行 3 批次、字段改名映射、DECIMAL 精度转换、DATETIME→VARCHAR 转换、中文数据）✓、执行进度轮询与记录 ✓、结构对比（新增表/删除表/字段级差异/注释差异）✓、生成的 DDL 在真实 MySQL 执行验证 ✓、下载接口（Content-Disposition 规范）✓、SQL 注入尝试被白名单拦截 ✓、XXL-Job 未启动时友好报错 ✓；
- 后端启动日志：**0 ERROR**；唯一 WARN 为调度中心未启动时的明确指引（属预期行为）。
