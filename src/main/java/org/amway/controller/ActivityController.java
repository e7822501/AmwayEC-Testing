package org.amway.controller;

import org.amway.dto.response.ActivityResponse;
import org.amway.dto.response.ApiResponse;
import org.amway.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
@Tag(name = "活動管理", description = "抽獎活動查詢API")
public class ActivityController {
    
    private final ActivityService activityService;
    
    @GetMapping
    @Operation(summary = "查詢進行中的活動", description = "獲取所有正在進行中的抽獎活動列表")
    public ApiResponse<List<ActivityResponse>> getActiveActivities() {
        List<ActivityResponse> activities = activityService.getActiveActivities();
        return ApiResponse.success(activities);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "查詢活動詳情", description = "根據ID查詢活動的詳細信息，包括獎品列表")
    public ApiResponse<ActivityResponse> getActivityDetail(
            @Parameter(description = "活動ID", required = true)
            @PathVariable Long id) {
        ActivityResponse activity = activityService.getActivityDetail(id);
        return ApiResponse.success(activity);
    }
}