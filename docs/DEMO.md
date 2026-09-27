# FeedWise 核心演示脚本（10 分钟）

> 演示前置：`docker compose up -d` + `SERVER_PORT=8081 mvn spring-boot:run` + `BACKEND_PORT=8081 npm run dev`。
> 浏览器打开 <http://localhost:5173>。演示账号密码统一 123456。

## Act 1 · 客服录入与 AI 自动整理（3 分钟）

1. 以 **support1（客服小王）** 登录（登录页点"客服小王"一键填充）。
2. 「用户反馈」→ 批量导入，粘贴 4 行：
   ```
   发票上传的时候一直转圈，最后提示上传失败
   发票传不上去，点上传按钮没反应
   一上传发票就报错，这周提交报销全卡在这
   上传发票总是失败，而且也看不到报销单被退回的原因
   ```
   → 提示导入成功、已触发 AI。**此时什么都不用做**：OutboxPro 事务事件 → RabbitMQ → AI 消费者
   会在几秒内自动生成候选卡片（管理台 <http://localhost:15672> guest/guest 可看队列消息轨迹）。
3. 「候选问题」刷新：出现《发票上传失败（N 条反馈）》和《报销单退回原因不可见》——
   **第 4 条混合反馈被自动拆进两张卡**，且两张卡都带"请复核是否合并"提示（AI 不自己合并）。
4. 反制演示（可选）：再点一次"AI 整理未处理反馈"→ 无重复卡片（已归档反馈不会重复入卡）。

## Act 2 · 产品经理决策（3 分钟）

5. 退出，以 **pm（王产品）** 登录。「候选问题」→ 点开《发票上传失败》：
   - 查看关联的**原始反馈回查**（每张卡必须能回到原文）；
   - 点 **确认成立**。
6. 确认后点 **AI 起草需求** → 提示草稿已生成。
7. 「需求草稿」→ 查看/编辑（改一条验收条件）→ **确认** → **转任务**：
   指派给"张工"、优先级 P1 → 任务建立，草稿状态变"已转任务"。
8. 反制演示（可选）：对一张"待确认"的卡直接执行"AI 起草需求"→ 40902 仅已确认问题可起草。

## Act 3 · 开发推进与禁跳（2 分钟）

9. 退出，以 **dev1（张工）** 登录。「改进任务」→ 任务列表只见指派给自己的任务（data-scope）。
10. 任务详情：先按"验证通过"？——按钮列表里根本没有（前端只渲染合法事件）。
    依次 **开始开发 → 提交验证 → 验证不通过，退回**（填验证结果）→ 再 **开始开发 → 提交验证 → 验证通过**。
11. curl 反制演示（绕过前端直接跳步，需要拿到 dev1 的 token）：
    ```bash
    curl -X POST http://localhost:8081/api/tasks/1/fire -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" -d '{"event":"PASS","note":"跳步"}'
    # → {"code":40900,"message":"状态流转失败: 状态机 [task] 当前所处状态 [TODO] 上尝试事件 [PASS]，当前可尝试事件: [START]"}
    ```

## Act 4 · 留痕与权限反制（2 分钟）

12. 「操作历史」→ 查询对象=改进任务 + 刚才的 ID：时间线里同时有
    **操作日志**（王产品/张工 + 处理说明）与 **状态机流转历史**（TODO→IN_PROGRESS→...）。
13. 重新以 **support1** 登录：反馈列表只有自己录入的（data-scope）；直接访问 dev1 的任务详情 URL → 40904 无权访问。
14. 收尾：`GET /web-common/error-codes` 看错误码字典；`/api-governance/management`（api-governance 管理端）与
    RabbitMQ 管理台展示可观测性。

## 附：纯 curl 版主链路（Windows PowerShell / bash 通用）

```bash
B=http://localhost:8081
SUPPORT=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"username":"support1","password":"123456"}' | jq -r .data.token)
PM=$(curl -s     -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"username":"pm","password":"123456"}'      | jq -r .data.token)
DEV=$(curl -s    -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"username":"dev1","password":"123456"}'    | jq -r .data.token)

curl -s -X POST $B/api/feedbacks/import -H "Authorization: Bearer $SUPPORT" -H 'Content-Type: application/json' \
  -d '{"items":[{"content":"发票上传总是失败"},{"content":"上传发票一直报错"}]}'
sleep 8                                                    # 等 outbox → rabbit → AI 消费者
curl -s "$B/api/issues?status=PENDING_REVIEW" -H "Authorization: Bearer $PM"
curl -s -X POST $B/api/issues/2/confirm -H "Authorization: Bearer $PM" -H 'Content-Type: application/json' -d '{"note":"确认"}'
curl -s -X POST $B/api/ai/draft -H "Authorization: Bearer $PM" -H 'Content-Type: application/json' -d '{"issueId":2}'
curl -s -X POST $B/api/drafts/1/confirm   -H "Authorization: Bearer $PM"
curl -s -X POST $B/api/drafts/1/convert   -H "Authorization: Bearer $PM" -H 'Content-Type: application/json' -d '{"assigneeId":4,"priority":"P1"}'
curl -s -X POST $B/api/tasks/1/fire      -H "Authorization: Bearer $DEV" -H 'Content-Type: application/json' -d '{"event":"START"}'
curl -s "$B/api/history?objectType=TASK&objectId=1" -H "Authorization: Bearer $PM"
```
