package com.feedwise.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feedwise.common.CurrentUser;
import com.feedwise.entity.Feedback;
import com.feedwise.service.FeedbackService;
import io.github.biglv666.authkit.annotation.RequirePermission;
import io.github.biglv666.webcommon.annotation.NoWrap;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 反馈导出（@NoWrap 豁免响应自动包装——文件下载不套 Result 外壳）。
 * 导出范围与列表一致：SUPPORT 只导出自己录入的，SUPPORT_LEAD 导出本组的，PM 全量。
 */
@RestController
@RequestMapping("/api/feedbacks")
public class FeedbackExportController {

    private static final String[] HEADERS = {"ID", "内容", "来源", "模块", "客户标签", "状态", "录入人ID", "录入时间"};

    private final FeedbackService feedbackService;

    public FeedbackExportController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /**
     * 导出 CSV（UTF-8 BOM 开头，Excel 直接打开不乱码）。
     * 权限位：feedback:export（PM 与客服主管持有）。
     */
    @GetMapping("/export")
    @NoWrap
    @RequirePermission("feedback:export")
    public void exportCsv(HttpServletResponse response) throws IOException {
        Page<Feedback> page = feedbackService.page(new Page<>(1, 1000), null, null, null);
        List<Feedback> records = page.getRecords();
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=feedback-export.csv");
        PrintWriter writer = response.getWriter();
        // UTF-8 BOM：Excel 直接双击打开识别为 UTF-8
        writer.write('﻿');
        writer.println(String.join(",", HEADERS));
        for (Feedback fb : records) {
            writer.println(String.join(",",
                    String.valueOf(fb.getId()),
                    escape(fb.getContent()),
                    fb.getSource(),
                    fb.getModule(),
                    escape(fb.getCustomerTag() == null ? "" : fb.getCustomerTag()),
                    fb.getStatus(),
                    String.valueOf(fb.getCreatedBy()),
                    String.valueOf(fb.getCreatedAt()).replace('T', ' ')));
        }
        writer.flush();
    }

    /** CSV 单元格转义：含逗号/引号/换行时加引号包裹，引号翻倍。 */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
