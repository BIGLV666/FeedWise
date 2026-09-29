package com.feedwise.controller;

import com.feedwise.entity.DashboardSnapshot;
import com.feedwise.service.DashboardService;
import io.github.biglv666.authkit.annotation.RequireLogin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工作台统计（扩展功能）。 */
@RestController
@RequestMapping("/api/dashboard")
@RequireLogin
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** @return 统计快照（cache-kit @CacheHandle 缓存 30 秒） */
    @GetMapping
    public DashboardSnapshot snapshot() {
        return dashboardService.snapshot();
    }
}
