# FeedWise 设计清单（架构先行）

> 企业报销软件「用户反馈 → 候选问题 → 需求草稿 → 改进任务」整理闭环演示系统。
> 本文档是实现的执行清单，也是最终验收的核对表。

## 1. 目标与验收标准

- 一条必须完整跑通的主链路：**客服录入/导入脱敏反馈 → AI（受约束工具调用）生成候选问题卡片 → 产品经理确认/合并/驳回 → AI 起草需求 → PM 确认草稿并建立改进任务 → 开发/测试更新状态并记录验证结果**，全程留痕。
- 整合全家桶 8 个组件（JitPack 锁最新 commit 消费，Central 只发大版本）。
- 页面/接口/数据库真实连通；六类反馈形态（加载/空/成功/失败/冲突/无权限）可见。
- 新目录/新环境：`docker compose up` + `mvn spring-boot:run` + `npm run dev` 即可完成核心演示。

## 2. 核心角色（3 类）与演示账号

| 角色 | 账号 | 能做 | 不能做 |
|---|---|---|---|
| 客服 SUPPORT | support1 / support2（密码 123456） | 录入/导入反馈、查看自己录入的反馈 | 确认问题、定优先级、建任务（40300） |
| 产品经理 PM | pm（密码 123456） | AI 整理触发、确认/合并/驳回、改草稿、建任务、定优先级 | 修改/删除原始反馈（接口不存在） |
| 开发/测试 DEV | dev1（密码 123456） | 任务状态流转、记录验证结果 | 需求确认/建任务（40300） |

## 3. 核心业务对象（6 + 审计）

1. `fw_user` 用户（auth-kit SPI 对接，角色 SUPPORT/PM/DEV）
2. `feedback` 用户反馈（来源/功能模块/状态/录入人）
3. `candidate_issue` 候选问题卡片（AI 或 PM 手工创建，关联原文）
4. `issue_feedback_link` 卡片-原文关联（实现修订：同一反馈可归入多张卡片——混合反馈拆分场景是合法路径；仅"同一张卡片内重复"被禁止）
5. `requirement_draft` 需求草稿（AI 草稿或手写，验收条件 JSON 数组）
6. `improvement_task` 改进任务（来自草稿，指派 + 优先级 + 验证结果）
7. `operation_log` 操作时间线（时间/操作人/动作/说明；state-kit 流转历史并入同一时间线查询）

## 4. 业务状态（有限状态机，state-kit 三台机器声明）

| 对象 | 状态 | 允许流转 | 典型禁跳 |
|---|---|---|---|
| feedback | UNPROCESSED → PROCESSED | 被候选卡片关联时 | — |
| candidate_issue | PENDING_REVIEW / CONFIRMED / REJECTED / MERGED | CONFIRM、REJECT、MERGE（仅出 PENDING_REVIEW） | REJECTED→CONFIRMED、CONFIRMED→REJECTED |
| requirement_draft | DRAFT / CONFIRMED / CONVERTED | CONFIRM、CONVERT | DRAFT→CONVERTED（必须先确认） |
| improvement_task | TODO / IN_PROGRESS / PENDING_VERIFY / DONE | START、SUBMIT、PASS、REJECT(验证不通过退回) | TODO→DONE、IN_PROGRESS→DONE |

CAS 条件更新保证并发正确；禁跳抛 `IllegalTransitionException` → web-common 映射 **40900 冲突**。

## 5. AI 约束式工具调用（不是自由发挥）

- AI 只能调用注册在 `ToolRegistry` 白名单里的两个内置工具，入参按 JSON Schema 校验，业务红线在工具实现内强制：
  - `create_candidate_issue(title, module, problem, demand, feedbackIds[])`：反馈必须存在；同一反馈不得在同一张卡片内重复；同一反馈允许归入多张卡（拆分）；产物 `ai_generated=1`、状态 PENDING_REVIEW（等待 PM 确认）。
  - `draft_requirement(issueId, title, background, description, acceptance[])`：仅对 CONFIRMED 卡片；一卡一草稿；产物 DRAFT 状态。
- AI 无合并工具——合并只能由 PM 手工执行（AI 只在卡片备注里给"疑似相似"提示）。
- `LlmClient` 双实现：`mock`（默认，确定性规则：模块关键词 + 现象指纹聚类；混合反馈拆两张卡；关键词相似但含义不同**不合并**）与 `openai`（OpenAI 兼容 function calling，超时/失败抛 `AiUnavailableException`）。
- 模型不可用：Orchestrator 记录失败日志，反馈保持未处理，PM 走手工建卡/手工起草，闭环不中断。

## 6. 组件映射（全部 8 个 + BOM）

| 组件 | 在本项目的用途 |
|---|---|
| auth-kit | 登录会话（不透明 token + Redis）、`@RequireRole` 角色拦截、防爆破 |
| web-common | 统一 Result/全局异常/错误码分段；新错误码字典端点 `@EnableErrorCodeEndpoint` |
| api-governance | 接口限流（登录/AI 触发）、慢调用日志（500ms）、Micrometer 指标 |
| concurrent-guard | `@Idempotent` 防重复导入/重复触发 AI/重复建任务；`@DistributedLock` 防并发转任务 |
| state-kit | 三台状态机（CAS + 历史表 + 操作人自动带 auth-kit 上下文） |
| cache-kit | 实体读缓存（BaseMapper 自动拦截 + `@CachedQuery` 用户查询），写后自动失效 |
| data-scope | 行级权限：SUPPORT 只看自己录入的反馈、DEV 只看指派给自己的任务（SQL 自动改写，fail-closed） |
| OutboxPro | 批量导入事务内写 outbox，RabbitMQ 可靠投递触发 AI 整理消费者（RELIABLE + 重试 + DLQ） |
| BOM | 版本管理基线（Central 大版本）；demo 因 JitPack groupId 不同直接锁 SHA，README 说明 |

## 7. 页面清单（10 个）

1. 登录页（演示账号一键填充）
2. 工作台 Dashboard（**唯一扩展功能**：状态分布统计 + 最近动态）
3. 反馈列表（录入 / 批量导入 / 触发 AI / 手工分类入口）
4. 反馈详情（原文 + 归属卡片 + 时间线）
5. 候选问题列表
6. 候选问题详情（原文回查 / 确认 / 驳回 / 合并 / 发起 AI 起草）
7. 需求草稿列表 / 详情（编辑、确认、转任务）
8. 改进任务列表（按状态分区看板）
9. 任务详情（状态流转操作 + 验证结果 + 完整时间线）
10. 全局历史查询（按对象检索操作时间线）

## 8. 接口与鉴权要点

- 所有接口 `/api/**`；登录接口在 auth-kit 白名单；`@RequireRole` 类/方法级声明角色。
- 反馈归属：列表走 data-scope SQL 改写；详情走服务层归属校验（双保险）。
- 客户端提交的 userId 一律不信任：操作人从 `AuthContext` 取。
- 六类反馈形态：统一 Result code（0 成功 / 40100 未登录 / 40300 无权限 / 40400 不存在 / 40900 冲突&禁跳 / 409xx 业务冲突 / 503xx AI 不可用）。

## 9. 数据与部署

- `Sql/schema.sql` + `Sql/data.sql`（MySQL 8.4），由 docker-compose 挂载到 `/docker-entrypoint-initdb.d` 首次启动自动执行；演示账号 BCrypt 固定哈希。
- 基础设施：MySQL 8.4 + Redis 7 + RabbitMQ 3（compose）；应用 `mvn spring-boot:run`；前端 `npm run dev`（Vite 代理 `/api`）。

## 10. 测试计划

| 测试 | 覆盖 |
|---|---|
| StateMachineTest | 正常流转 / 禁跳 40900 / 终态再流转 |
| RolePermissionTest | 未登录 40100、客服越权 40300、PM 放行 |
| DataScopeTest | 两个客服各自只见自己的反馈；PM 见全部 |
| AiExtractionTest | 多种说法聚一张卡 / 混合反馈拆两张卡 / 相似关键词不误合并 / 已处理反馈不再入卡 |
| AiDegradedTest | LLM 挂掉→反馈未处理→PM 手工建卡闭环 |
| IdempotentConflictTest | 重复导入被拒；重复转任务被拒 |
| FullChainIntegrationTest | 录入→AI→确认→起草→确认→转任务→START/SUBMIT/PASS→时间线完整 |
| CacheKitSmokeTest | 有 Redis 时用户查询走缓存（无 Redis 自动跳过） |

测试环境：H2(MODE=MySQL) + mock AI + outboxpro 关闭（生产/Docker 环境开启）。

## 11. 不采用的替代方案

- JWT：auth-kit 不透明 token + Redis 会话已是组件标准能力。
- LangChain4j：demo 只需 function calling，自写 ~60 行 OpenAI 兼容客户端更可控、可降级。
- 手写 if/else 状态校验：state-kit 的 CAS + 历史就是为此而生。
- BOM 直接 import：JitPack 坐标 groupId 与 Central 不同，BOM 锁不住 SHA；demo 显式锁 SHA 并在 README 说明取舍。
