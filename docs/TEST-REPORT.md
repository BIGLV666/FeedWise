# FeedWise 测试报告

执行日期：2026-09-27　环境：Windows / JDK 21（Microsoft OpenJDK） / Maven 3.9.16 / H2(MODE=MySQL) + 本机 Redis(cache-kit-redis:6379)

## 一、单元 + 集成测试（mvn test）

```
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

| 测试类 | 用例 | 覆盖 |
|---|---|---|
| FullChainIntegrationTest | 7 | 主链路 7 步：support 批量导入 → PM 触发 AI → 候选卡生成（混合反馈拆两张卡 + 相似提示）→ PM 确认 → AI 起草草稿 → PM 编辑确认 → 转任务(P1) → DEV 推进 START/SUBMIT/PASS（禁跳 40900、验证结果必填 40003、终态再流转 40900）→ 时间线完整 + data-scope 列表隔离 |
| RolePermissionTest | 7 | 无 token 40100 / 伪造 token 40100 / 客服确认问题 40300 / 客服触发 AI 40300 / 开发确认问题 40300 / 客服读他人反馈 40904 / PM 确认自建卡放行 |
| AiConstraintTest | 7 | 反馈可拆分多卡但卡内去重 / 卡内重复 id 拒绝 / 未注册工具名拒绝（白名单） / Schema 校验（长度/枚举/必填/数组下限） / 模型不可用→反馈保持未处理→PM 手工建卡闭环 / 工具白名单只有 create_candidate_issue 与 draft_requirement（AI 无删改原文与合并能力） |
| StateMachineAndScopeTest | 6 | TODO→PASS 禁跳 40900 / REJECT 退回 IN_PROGRESS + verify_result 落库 / DONE 终态再流转 40900 / 未确认草稿转任务拒绝 / DEV 只看指派给自己的任务 / DEV 读他人任务 40904 |
| IdempotentConflictTest | 1 | 30s TTL 内重复导入 → guard REJECT → 40906（无 Redis 环境自动跳过：guard fail-open） |
| CacheKitSmokeTest | 1 | @CachedQuery 二次查询命中缓存（DB 计数不变）/ 直写 DB 后缓存旧值 / updateById 失效后读到新值（无 Redis 环境自动跳过） |

## 二、测试过程中发现并修复的真实缺陷

1. **循环依赖**：AiOrchestrator → DraftRequirementTool → RequirementDraftService → AiOrchestrator。修复：草稿创建核心抽为 `DraftCoreService`。
2. **状态机 CAS 绕过缓存失效**：state-kit 直写 DB 不经过 MyBatis-Plus，cache-kit 对 `selectById` 的缓存读到旧状态。修复：任务/候选问题/草稿的读取全部改为不走缓存的条件查询；cache-kit 保留在用户查询等安全路径。
3. **AI 拆分卡与"已归入"规则冲突**：混合反馈归入第一张卡后状态变 PROCESSED，第二张卡的工具调用被拒。修订业务规则（见 DESIGN.md 修订记录）：同一反馈允许归入多张卡（拆分），仅禁止卡内重复。
4. **批量导入事件携带 null 反馈 id**：importBatch 从未经 insert 的原始对象取主键。修复：从插入后的实体取回填 id。该缺陷只有真机 OutboxPro 链路能暴露（单测里事件被禁用），已列入面试素材。
5. **空反馈列表建卡生成非法 SQL `IN ()`**：手工建卡不关联反馈时报错。修复：空列表短路。
6. **@CachedQuery 方法无 SQL 绑定**：BindingException。修复：补 `@Select`（PaperWise 靠 mapper XML 隐藏了该问题）。

## 三、真机端到端验证（docker compose + 应用 8081）

基础设施：mysql:8.4(3308, 首启自动建表+种子数据) + redis:7(6380) + rabbitmq:3.13(5672/15672)。

| 步骤 | 结果 |
|---|---|
| 登录四账号 | 均签发 token，角色正确 |
| support1 批量导入 2 条反馈 | code=0；事务内写 outbox |
| OutboxPro relay → RabbitMQ → AI 消费者 | outboxpro_outbox.status=SENT、outboxpro_inbox.status=SUCCESS、operation_log 记 AI_EXTRACT_DONE |
| AI 自动生成候选卡 | 发票上传失败（2 条反馈）+ 报销单退回原因不可见（1 条反馈），混合反馈拆分并带"请复核是否合并"提示 |
| PM 确认 → AI 起草 → 确认草稿 → 转任务(P1) | 全部 code=0 |
| dev1 START 任务 | 状态 TODO→IN_PROGRESS |
| 任务时间线 | TASK_CREATED（王产品）+ TASK_STATE_CHANGED（张工）+ 状态机流转历史三源合并，倒序 |
| 幂等/限流/错误码端点 | /web-common/error-codes 正常返回字典 |

## 四、binlog 失效兜底验证（2026-09-27 补充）

针对"state-kit CAS 直写 DB 绕过 MyBatis-Plus → cache-kit 缓存可能脏读"的组合缺口，按组件既定方案启用 **binlog 直连失效**（`cache-kit.binlog.enabled=true` + `com.zendesk:mysql-binlog-connector-java:0.31.0`，MySQL 8.4 默认 log_bin=ROW/FULL 开箱即用）。

真机实验（写库绕过应用，观察角色权限是否即时生效）：

| 步骤 | 响应 |
|---|---|
| ① PM 调 PM 接口（预热缓存） | 业务错 40903（角色校验通过） |
| ② `UPDATE fw_user SET role='DEV' WHERE id=3`（绕过应用）→ 立即再调 | **40300 缺少角色：PM**（binlog 失效生效） |
| ③ 改回 role='PM' → 再调 | 业务错 40903（恢复） |

结论：状态机 CAS 直写、DBA 改库等一切绕过 MP 的写，COMMIT 后由 binlog 自动失效对应实体缓存。
测试环境（H2 无 binlog）保持 `cache-kit.binlog.enabled=false`，并以"状态机实体的读走条件查询（不缓存）"兜底——双保险。

**组件反馈**：cache-kit 在 `binlog.enabled=true` 但 connector 类缺失时静默跳过（无任何 WARN）。建议启动期 fail-fast 或至少告警，避免"以为开了 binlog 失效其实没开"的静默失效——本次实验正是靠行为差异才发现依赖缺失。

## 五、前端浏览器走查（2026-09-27，Chrome 实操）

逐页手动操作走查（登录 → 工作台 → 反馈 → AI 整理 → 候选问题详情 → 确认 → AI 起草 → 草稿确认 → 建任务 → 切换 dev → 状态机四步 → 时间线），发现并修复：

| 缺陷 | 影响 | 修复 |
|---|---|---|
| 三个详情页缺 `import { useRoute } from 'vue-router'` | 候选问题/反馈/任务详情页 **整页白屏**（setup 抛 ReferenceError） | 补 import（这就是"前端几乎没法运行"的主因） |
| 种子数据整体乱码（`çŽ‹äº§å"1`） | 演示账号姓名、12 条种子反馈全乱码；应用内录入的数据正常 | docker initdb 客户端按 Latin1 解析 SQL：两个脚本加 `SET NAMES utf8mb4` + compose 显式 `--character-set-server=utf8mb4`，重建 volume |
| 列表显示"录入人ID/指派给"裸数字 | 可读性差 | 后端补 `createdByName`/`assigneeName`（@TableField(exist=false) 批量填充） |
| mock 起草背景写"涉及原始反馈 0 条" | 草稿内容失真 | orchestrator 传 feedbackCount，mock 读它 |
| 时间线状态机条目显示"用户#4" | 与操作日志条目（张工）不一致 | 后端按 operatorId 映射姓名（走 cache-kit 缓存） |
| 工作台进度条不显示颜色 | 观感 | 改为主题色进度条 + 行首色点 |

视觉整体改版：登录页深蓝渐变 + 品牌卡、侧栏品牌区与圆角菜单高亮、统计卡图标+彩色数字、链路步骤图、表头浅灰、浅色专业配色（无新依赖）。

已验证的反馈形态：加载骨架（v-loading）、空数据（无关联反馈/暂无数据）、成功（Message 提示）、失败与无权限（40300/40904 红色提示 + 空态文案）、冲突（40900 状态机禁跳、40906 幂等重复）。

## 六、已知边界

- 测试中 OutboxPro 关闭（无 RabbitMQ 依赖），其生产端行为由真机 e2e 验证；消费失败重试/DLQ 依赖组件自身 82 例测试。
- api-governance 限流（登录 10 次/分、AI 触发 5 次/分）在真机验证可用，未写入自动化断言（组件自带 600+ 用例）。
- 前端未做自动化测试，以 `npm run build` + 真机手工走查为准。
