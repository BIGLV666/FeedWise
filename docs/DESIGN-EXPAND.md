# FeedWise 扩展设计清单：全家桶能力铺满（2026-09-27 第二轮）

> 原则：主业务不变（反馈→候选问题→需求草稿→改进任务），新增能力全部以
> "反馈方向"的业务意义存在，不为展示而展示；组件已同步最新 main 提交（6 个新 SHA）。
> 组件版本：api-governance 0.6.0 / state-kit 0.3.0 / OutboxPro 新 DLQ 批量重放 /
> guard REPLAY 完成哨兵 / web-common Boot4 适配 / cache-kit GTID+streams lag。

## 1. 新增业务能力与组件映射

### A. 权限体系加深（auth-kit）
- **权限位模型**：PM 角色配权限码（issue:confirm、issue:merge、draft:convert、feedback:export、dlq:replay）；
  `PermissionProvider` 按角色返回权限集；接口从 `@RequireRole` 升级为 `@RequirePermission`（候选问题确认/合并、草稿转换、导出、DLQ 重放）——角色与权限位并存展示。
- **二级认证 @RequireSafe**："合并候选问题"是敏感操作，前端弹密码框 → `openSafe`（AuthKit 门面）→ 再执行。
- **记住我**：登录页加"7 天免登录"复选框 → `AuthKit.login(userId, device, rememberMe)`。
- **管理端点**：`auth-kit.management.*` 开启（在线会话查询/强制下线）→ 前端"在线会话"页（PM）。
- 数据层：`fw_user` 新增 `dept` 列（部门），新增客服主管账号 `lead1`（SUPPORT_LEAD，dept=反馈组）。

### B. 数据权限扩展（data-scope）
- `ScopeResolver` 升级：SUPPORT→`self`、**SUPPORT_LEAD→`deptIn`（本组）**、PM/DEV→`all`。
- 前端：lead1 登录可见本组（support1 属于同组）反馈。

### C. 幂等语义升级（concurrent-guard）
- **转任务改 REPLAY 模式**（配 `guard.idempotent.replay-grace-millis`）：重复"转任务"不再 409 而是**重放上次 taskId**（200），更符合产品语义；REJECT 模式在导入/AI 触发保留，两模式同框演示。
- **GuardRejectedEvent 订阅**：幂等拒绝也写 operation_log（"被幂等拒绝"留痕）。
- **LockTemplate 编程式锁**：合并候选问题操作用编程式锁保护（与注解式分布式锁对照展示）。

### D. 异步治理与告警（api-governance 0.6.0）
- **异步钩子**：AI 整理方法标注 `@AsyncAction(aiExtract)`；`@AsyncHandler(aiExtract)` 四阶段处理器记录耗时与结果 → operation_log。
- **告警 webhook**：`alert.webhook` 指向本地 `/api/internal/governance-alert`（认证令牌校验），慢调用/限流告警与 0.6.0 **恢复通知**写入 operation_log。
- **管理端点** `api.governance.management.*` 开启（令牌）：前端"治理面板"读指标。
- `@NoLog` 用在 `/api/auth/me`（高频轮询不刷治理日志）；0.6.0 标准限流响应头自动生效（前端展示 RateLimit-* 头部）。

### E. 状态机扩展点（state-kit 0.3.0）
- **StateGuard**：验证结果必填从服务层挪入 task 机器 PASS/REJECT 边的守卫（组件扩展点）；
  **StateAction**：PASS 成功后动作里联动把 issue 状态留痕（动作同事务）。
- **StateTransitedEvent 监听**（`@TransactionalEventListener AFTER_COMMIT`）：流转成功后写操作时间线（替代服务层手写，展示组件推荐姿势）。
- **availableActions()**：任务详情/前端按状态机返回的"当前可操作事件"渲染按钮（前端不再写死）。
- **状态机可视化**：`<machine>Exporter.toMermaid()/toDot()` → 前端"状态机"页渲染 mermaid 图。
- **retry 冲突自动重试**：yml 开启（高并发演示场景）。
- 冲突补偿 SPI 与本 demo 场景不契合（需要真正的冲突风暴才可见），保留说明。

### F. 事务消息全链路（OutboxPro 新端点）
- **注解式事件**：新增 `AiExtractionDoneEvent`（`@OutboxEvent` + `@OutboxHandler` + `AnnotatedOutboxHandler`）→
  **BEST_EFFORT** 订阅写"工作台动态"；与编程式 `feedback.batch.imported`（RELIABLE）两风格并存。
- **DLQ 管理页**：`dlq.replay.enabled=true` + `DlqReplayAuthorizer`（按 scope 判权：PM 才允许 replay）；
  前端死信列表（ops 端点检索）+ 重放按钮（`POST /actuator/outboxpro/dlq/{eventId}/replay`）。
- **@NonRetryable**：AiUnavailableException 标注——模型不可用直接进死信等待人工重放（与"降级手工路径"说明：HTTP 同步触发仍 50301 降级；事件通道进 DLQ）。
- **事务模板契约校验**（新能力）与 **Sink 降级指标**自动生效。

### G. 缓存体系（cache-kit）
- **@CacheEntity + @CacheHandle**：`DashboardSnapshot` 声明可缓存实体（ttl=30s），工作台统计走手动句柄
  `get("snapshot", loader)`——30 秒内统计不重复计算。
- **CacheKit.withDb 强一致读**：合并候选问题的目标校验用旁路读（合并决策不能被缓存旧状态误导）。
- **GTID 位点模式**：`cache-kit.binlog.gtid-enabled=true`（compose MySQL 8.4 验证 GTID_MODE 后开启）。
- **指标展示**：`/actuator/metrics/cache-kit.l1.requests` 等挂到"能力地图"页。

### H. web-common
- **@DefaultErrorCode**：`AiUnavailableException` 标注 `@DefaultErrorCode(AI_UNAVAILABLE)`，替代手写映射。
- **@NoWrap**：`GET /api/feedbacks/export` CSV 下载（data-scope 范围一致）。
- `http-status-mode: SEMANTIC` 不采用（改模式影响前端全部响应，代价大收益小，README 说明理由）。

### I. 前端新页面（+4，共 14）
1. **能力地图** `/capability`：8 组件卡片（用途/已用能力/演示入口/运行指标）——"组件集合展示"主入口。
2. **状态机** `/machines`：三台机器 mermaid 图 + 当前可操作事件（availableActions 联动）。
3. **死信管理** `/dlq`（PM）：死信列表、原因分类、重放（含 DLQ 制造演示按钮：强制 AI 失败）。
4. **在线会话** `/sessions`（PM）：在线会话列表 + 强制下线。
5. 工作台"动态"区（BEST_EFFORT 通知 + GuardRejected 留痕）；反馈列表加"导出 CSV"；合并加二级认证弹窗；任务详情按钮来自 availableActions。

## 2. 数据与配置变更

- `fw_user` 加 `dept` 列；`data.sql` 新增 `lead1` 账号（SUPPORT_LEAD/反馈组）；H2 测试 schema 同步。
- yml 新增：auth-kit.management、api.governance.alert.webhook/management、outboxpro.dlq.replay.enabled、
  cache-kit.binlog.gtid-enabled、state-kit retry。
- 测试：REPLAY 重放、@RequireSafe 未认证拒绝、deptIn 范围、CSV 导出、availableActions、
  StateGuard 拒绝（验证结果必填）；原有 29 例保持绿。

## 3. 不采用的替代（记录）

- 状态机冲突补偿 SPI：demo 无真实冲突风暴场景，强行制造演示失真，README 说明。
- cache-kit streams 广播模式：单实例 demo 无重启窗口丢失效问题。
- OutboxPro DLQ 告警 webhook（DeadLetterAlertNotifier）：与 api-governance 告警同源，只演示一个通道。
