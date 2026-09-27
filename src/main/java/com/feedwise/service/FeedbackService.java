package com.feedwise.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feedwise.common.error.FeedWiseErrorCode;
import com.feedwise.entity.Feedback;
import com.feedwise.entity.User;
import com.feedwise.integration.outbox.FeedbackBatchImportedEvent;
import com.feedwise.mapper.FeedbackMapper;
import com.feedwise.mapper.UserMapper;
import io.github.biglv666.webcommon.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 反馈服务：录入、批量导入、查询。
 *
 * <p>红线：反馈原文不可修改、不可删除——本服务不提供任何更新 content / 删除接口。</p>
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final FeedbackMapper feedbackMapper;
    private final UserMapper userMapper;
    private final OperationLogService operationLogService;
    /** OutboxPro 在测试等场景可能被禁用（无 Publisher Bean），此时事件退化为仅日志 */
    private final OutboxPublisherPort outboxPublisherPort;

    public FeedbackService(FeedbackMapper feedbackMapper, UserMapper userMapper,
                           OperationLogService operationLogService,
                           @Autowired(required = false) OutboxPublisherPort outboxPublisherPort) {
        this.feedbackMapper = feedbackMapper;
        this.userMapper = userMapper;
        this.operationLogService = operationLogService;
        this.outboxPublisherPort = outboxPublisherPort;
    }

    /**
     * 单条录入反馈，并自动触发 AI 整理（通过事务 outbox 事件可靠投递）。
     *
     * @param content     脱敏反馈原文
     * @param source      来源渠道 TICKET/SURVEY/CALL
     * @param module      功能模块（客服初判，AI 可在候选卡片中修正归纳）
     * @param customerTag 客户标签（脱敏）
     * @param operatorId  录入客服 id（来自登录态，不信任前端）
     * @return 新反馈 id
     */
    @Transactional
    public Long create(String content, String source, String module, String customerTag, Long operatorId) {
        Feedback feedback = new Feedback();
        feedback.setContent(content);
        feedback.setSource(source);
        feedback.setModule(module);
        feedback.setCustomerTag(customerTag);
        feedback.setStatus("UNPROCESSED");
        feedback.setCreatedBy(operatorId);
        feedbackMapper.insert(feedback);
        operationLogService.log("FEEDBACK", feedback.getId(), "FEEDBACK_CREATED",
                operatorId, displayName(operatorId), "客服录入反馈，等待 AI/人工分类");
        publishBatchImported(List.of(feedback.getId()), operatorId);
        return feedback.getId();
    }

    /**
     * 批量导入脱敏反馈（调用方 Controller 已加 @Idempotent 防重复提交）。
     *
     * @param items      反馈条目（content/source/module/customerTag）
     * @param operatorId 导入客服 id
     * @return 导入数量
     */
    @Transactional
    public int importBatch(List<Feedback> items, Long operatorId) {
        List<Long> ids = new java.util.ArrayList<>();
        for (Feedback item : items) {
            Feedback feedback = new Feedback();
            feedback.setContent(item.getContent());
            feedback.setSource(item.getSource() == null ? "TICKET" : item.getSource());
            feedback.setModule(item.getModule() == null ? "OTHER" : item.getModule());
            feedback.setCustomerTag(item.getCustomerTag());
            feedback.setStatus("UNPROCESSED");
            feedback.setCreatedBy(operatorId);
            feedbackMapper.insert(feedback);
            // 从插入后的对象取回填的自增主键（原始 item 未经过 insert，id 为 null）
            ids.add(feedback.getId());
        }
        operationLogService.log("FEEDBACK", null, "FEEDBACK_IMPORTED",
                operatorId, displayName(operatorId), "批量导入 " + items.size() + " 条反馈");
        publishBatchImported(ids, operatorId);
        return items.size();
    }

    /**
     * 在当前事务内写 outbox 事件：导入完成 → AI 整理消费者。OutboxPro 未启用时仅告警不阻断录入。
     *
     * @param ids        本批反馈 id
     * @param operatorId 操作人
     */
    private void publishBatchImported(List<Long> ids, Long operatorId) {
        if (outboxPublisherPort == null) {
            log.warn("[outbox] OutboxPro 未启用，AI 整理事件降级为日志（反馈 {} 保持未处理，可手工触发 AI）", ids);
            return;
        }
        outboxPublisherPort.publish(new FeedbackBatchImportedEvent(ids, operatorId));
    }

    /**
     * 分页查询反馈。SUPPORT 角色由 data-scope 自动过滤为本人录入（SQL 改写），PM/DEV 全量。
     */
    public Page<Feedback> page(Page<Feedback> page, String module, String status, String keyword) {
        LambdaQueryWrapper<Feedback> qw = Wrappers.lambdaQuery(Feedback.class)
                .eq(module != null && !module.isBlank(), Feedback::getModule, module)
                .eq(status != null && !status.isBlank(), Feedback::getStatus, status)
                .like(keyword != null && !keyword.isBlank(), Feedback::getContent, keyword)
                .orderByDesc(Feedback::getId);
        Page<Feedback> result = feedbackMapper.selectScoped(page, qw);
        fillCreatorNames(result.getRecords());
        return result;
    }

    /** 批量填充录入客服姓名（走 cache-kit 缓存的用户查询）。 */
    private void fillCreatorNames(List<Feedback> records) {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (Feedback fb : records) {
            if (fb.getCreatedBy() != null) {
                ids.add(fb.getCreatedBy());
            }
        }
        Map<Long, String> names = new java.util.HashMap<>();
        for (Long id : ids) {
            User user = userMapper.getUserById(id);
            if (user != null) {
                names.put(id, user.getDisplayName());
            }
        }
        for (Feedback fb : records) {
            fb.setCreatedByName(names.getOrDefault(fb.getCreatedBy(), "用户#" + fb.getCreatedBy()));
        }
    }

    /**
     * 反馈详情 + 归属校验：SUPPORT 只能查看自己录入的反馈（列表已过滤，详情二次校验防越权）。
     *
     * @param id         反馈 id
     * @param viewerId   当前用户 id
     * @param viewerRole 当前用户角色
     * @return 反馈实体
     */
    public Feedback getOwned(Long id, Long viewerId, String viewerRole) {
        Feedback feedback = feedbackMapper.selectOne(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getId, id));
        if (feedback == null) {
            throw new BusinessException(FeedWiseErrorCode.FEEDBACK_NOT_FOUND);
        }
        if ("SUPPORT".equals(viewerRole) && !feedback.getCreatedBy().equals(viewerId)) {
            throw new BusinessException(FeedWiseErrorCode.DATA_NOT_OWNED);
        }
        return feedback;
    }

    /** 操作人展示名（时间线可读性）。 */
    public String displayName(Long userId) {
        User user = userMapper.getUserById(userId);
        return user == null ? "用户#" + userId : user.getDisplayName();
    }

    /** Outbox 发布端口：隔离 OutboxPro 的可选性，便于测试环境降级。 */
    public interface OutboxPublisherPort {
        void publish(FeedbackBatchImportedEvent event);
    }
}
