# FeedWise · 企业报销软件「用户反馈与功能改进助手」

> 一个 demo 集合所有组件的完整业务系统：把零散的用户反馈，经 **AI 约束式整理 → 产品经理确认**，
> 变成可交给开发的**功能改进任务**，全程留痕、可回查原文。
>
> 后端整合 [biglv666 全家桶](https://github.com/BIGLV666) 8 个 Spring Boot 组件（经 JitPack 锁 commit 消费），
> 前端 Vue 3 + Element Plus，基础设施 MySQL 8.4 + Redis 7 + RabbitMQ 3（docker compose 一键起）。

## 1. 业务场景与角色

一家开发企业报销软件的科技公司，一周收到 60 条用户反馈。AI 负责第一轮整理（提取、聚类、起草），
产品经理负责所有决策（确认、合并、优先级、建任务），开发/测试负责实现与验证。

| 角色 | 账号 | 能做 | 不能做 |
|---|---|---|---|
| 客服 SUPPORT | support1 / support2（密码 123456） | 录入/批量导入脱敏反馈、查看自己录入的反馈 | 确认问题、定优先级、建任务（→40300） |
| 客服主管 SUPPORT_LEAD | lead1（密码 123456） | 查看本组（同 dept）客服的反馈、导出 CSV、手工建卡 | 确认问题、定优先级、建任务（→40300） |
| 产品经理 PM | pm（密码 123456） | 触发 AI、确认/合并/驳回、改草稿、建任务、定优先级 | 修改/删除任何反馈原文（接口不存在） |
| 开发/测试 DEV | dev1（密码 123456） | 任务状态流转、记录验证结果 | 需求确认、建任务（→40300） |

## 2. 主业务链路（必须完整跑通的一条）

```
客服录入/导入脱敏反馈
  → (事务 outbox 事件 → RabbitMQ → AI 消费者，可靠投递)
AI 按功能模块+问题现象聚类，生成候选问题卡片（混合反馈自动拆分并提示 PM 复核）
  → 产品经理查看原文回查，确认 / 修改 / 合并 / 驳回
AI 为已确认问题生成需求草稿和验收条件
  → 产品经理修改、确认后建立改进任务（幂等 + 分布式锁双保险）
开发/测试推进 待开发→开发中→待验证→已完成（验证不通过可退回），记录验证结果
  → 全程操作时间线（时间/操作人/处理说明 + 状态机流转历史）
```

**AI 业务约束（不是自由发挥）**：AI 只能调用系统注册的两个受限工具
（`create_candidate_issue` / `draft_requirement`），入参过 JSON Schema 校验，业务红线在工具实现内强制；
AI 不产出任何"已生效"数据——候选卡与需求草稿都必须 PM 确认；AI 无合并、无优先级、无删改原文的能力；
模型不可用时自动降级，客服/PM 手工路径完成同样的闭环。

## 3. 状态机（state-kit，禁跳一律 40900）

| 对象 | 状态与合法流转 |
|---|---|
| 反馈 | UNPROCESSED → PROCESSED（被候选卡关联） |
| 候选问题 | 待确认 → 确认 / 驳回 / 合并（只能从"待确认"出边） |
| 需求草稿 | DRAFT → CONFIRMED → CONVERTED（未确认直接转任务 = 禁跳） |
| 改进任务 | 待开发 → 开发中 → 待验证 → 已完成；待验证可退回开发中 |

## 4. 全家桶组件映射（8/8）

| 组件 | 在本项目中的用途 |
|---|---|
| **auth-kit** | 四角色会话登录（不透明 token + Redis）、`@RequireRole`+`@RequirePermission` 两级模型、`@RequireSafe` 二级认证（合并敏感操作）、记住我、同端顶号、在线会话与强制下线 |
| **web-common** | 统一 Result + 全局异常 + 错误码分段；错误码字典端点；`@NoWrap` CSV 导出；`@DefaultErrorCode` 异常映射 |
| **api-governance** | 限流（0.6.0 标准响应头）、慢调用日志、`@AsyncAction/@AsyncHandler` 异步钩子（AI 整理四阶段观察）、告警 webhook+恢复通知→时间线、管理端点、`@NoLog` |
| **concurrent-guard** | `@Idempotent` 双模式：REJECT（导入/AI，40906）+ REPLAY（转任务，重放上次 taskId）+ 完成哨兵宽限；`@DistributedLock` 与 `LockTemplate` 编程式锁；`GuardRejectedEvent` 留痕 |
| **state-kit** | 三台状态机：CAS 唯一写入口、流转历史、`StateGuard`（验证结果必填）/`StateAction`（PASS 联动）、`StateTransitedEvent` 时间线、`availableActions` 可操作按钮、Mermaid 可视化导出、冲突自动重试 |
| **cache-kit** | 三级缓存：MP 自动缓存、`@CachedQuery`、`@CacheEntity`+`@CacheHandle`（工作台快照 30s）；binlog 直连失效（GTID 位点）兜底绕过 MP 的写；`withDb` 强一致读 |
| **data-scope** | 行级权限：SUPPORT→self、**SUPPORT_LEAD→deptIn 本组**、DEV→被指派任务、PM→all（SQL 自动改写，fail-closed） |
| **OutboxPro** | 导入事务 outbox→RabbitMQ→AI 消费者（RELIABLE）；`@OutboxEvent/@OutboxHandler` 注解式 BEST_EFFORT 通知；`@NonRetryable` 直进死信；死信台账+人工重放（权限位判权） |

依赖消费策略：`pom.xml` 经 JitPack（`com.github.BIGLV666:...:<commit-sha>`）锁定各组件**最新 main 提交**；
组件日常迭代走 JitPack，Maven Central 只发大版本（大版本走 BOM `io.github.biglv666:biglv666-spring-boot-bom`）。

## 5. 快速开始（新环境可复现）

前置：JDK 21、Maven 3.9+、Node 18+、Docker。

```bash
# ① 基础设施（MySQL 8.4 + Redis 7 + RabbitMQ 3；首次启动自动建表 + 演示数据）
docker compose up -d
# 端口：MySQL 3310（root/root，库 feedwise，GTID 开启）、Redis 6381、RabbitMQ 5672/管理台 15672

# ② 后端（默认连上述端口；本机 8080 被占时用 SERVER_PORT 换端口）
mvn spring-boot:run                      # 或 SERVER_PORT=8081 mvn spring-boot:run

# ③ 前端
cd frontend
npm install
BACKEND_PORT=8081 npm run dev            # http://localhost:5173
```

登录 <http://localhost:5173>，右上角演示账号一键填充。完整 10 分钟演示脚本见 `docs/DEMO.md`。

## 6. 页面（14 个）

登录页（记住我）/ 工作台统计 / 反馈列表（录入·导入·触发 AI·导出 CSV）/ 反馈详情 /
候选问题列表 / 候选问题详情（原文回查·确认·驳回·合并[二级认证]·AI 起草）/ 需求草稿（编辑·确认·转任务）/
改进任务看板 / 任务详情（availableActions 动态按钮·验证结果·时间线）/
全局历史查询 / **全家桶能力地图（8 组件能力矩阵+运行指标）** / **状态机可视化（Mermaid）** /
**死信管理（台账+重放）** / **在线会话（踢人）**。

六类反馈形态全覆盖：loading 骨架、空数据插画、成功/失败 Message、
40900 状态冲突弹条、40906 重复提交提示、40300/40904 无权限提示、401 自动跳登录。
（视觉与交互已逐页浏览器实操走查，见 docs/TEST-REPORT.md 第五节）

## 7. 测试

```bash
mvn test    # 29 用例全绿（H2 + mock AI，含主链路集成、权限矩阵、状态机禁跳、AI 约束、幂等冲突、缓存命中）
```

详见 `docs/TEST-REPORT.md`（含测试揪出的 6 个真实缺陷，其中"批量导入事件携带 null 主键"只有真机 outbox 链路能暴露）。

## 8. 目录结构

```
FeedWise/
├── src/main/java/com/feedwise/
│   ├── ai/            # AI 编排器 + 受限工具（白名单 + Schema 校验 + 业务红线）+ mock/openai 双引擎
│   ├── config/        # auth-kit SPI / data-scope 决策 / state-kit / OutboxPro 事件订阅
│   ├── controller/    # 8 个 Controller（@RequireRole 声明角色）
│   ├── service/       # 业务核心（反馈 / 候选问题 / 草稿 / 任务 / 时间线 / 工作台）
│   ├── integration/   # OutboxPro 事件与消费者
│   └── common/        # 错误码分段 + guard 异常映射
├── src/main/resources/Sql/    # 01_schema.sql + 02_data.sql（compose 首启自动执行）
├── frontend/          # Vue3 + Vite + Element Plus（10 页面）
├── docker-compose.yml
└── docs/              # DESIGN.md 设计清单 / TEST-REPORT.md / DEMO.md 演示脚本
```

## 9. 设计文档

- [docs/DESIGN.md](docs/DESIGN.md) —— 架构先行清单（对象/状态/链路/页面/组件映射/不采用的方案与理由）
- [docs/DESIGN-EXPAND.md](docs/DESIGN-EXPAND.md) —— 第二轮扩展清单（全家桶能力铺满：REPLAY/二级认证/deptIn/DLQ 重放/异步钩子/状态机扩展点等）
- [docs/TEST-REPORT.md](docs/TEST-REPORT.md) —— 测试结果与缺陷修复记录
- [docs/DEMO.md](docs/DEMO.md) —— 10 分钟核心演示脚本（含 curl 版）
