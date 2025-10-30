package org.amway.controller;

import org.amway.annotation.RateLimit;
import org.amway.dto.request.DrawRequest;
import org.amway.dto.response.ApiResponse;
import org.amway.dto.response.DrawResponse;
import org.amway.entity.DrawRecord;
import org.amway.service.LotteryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lottery")
@RequiredArgsConstructor
@Tag(name = "抽獎管理", description = "抽獎相關API")
@SecurityRequirement(name = "Bearer Authentication")
public class LotteryController {

    private final LotteryService lotteryService;

    @PostMapping("/draw")
    @RateLimit  // 啟用限流
    @Operation(summary = "執行抽獎", description = "用戶執行單次或多次抽獎")
    public ApiResponse<DrawResponse> draw(
            @Valid @RequestBody DrawRequest request,
            Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();
        DrawResponse response = lotteryService.draw(userId, request);

        return ApiResponse.success("抽獎成功", response);
    }

    @GetMapping("/history")
    @Operation(summary = "查詢抽獎歷史", description = "查詢用戶的抽獎歷史記錄")
    public ApiResponse<List<DrawRecord>> getDrawHistory(
            @Parameter(description = "活動ID，可選")
            @RequestParam(required = false) Long activityId,
            Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();
        List<DrawRecord> history = lotteryService.getUserDrawHistory(userId, activityId);

        return ApiResponse.success(history);
    }

    @GetMapping("/remaining-draws")
    @Operation(summary = "查詢剩餘抽獎次數", description = "查詢用戶在指定活動中的剩餘抽獎次數")
    public ApiResponse<Map<String, Integer>> getRemainingDraws(
            @Parameter(description = "活動ID", required = true)
            @RequestParam Long activityId,
            Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();
        int remainingDraws = lotteryService.getRemainingDraws(userId, activityId);

        Map<String, Integer> result = new HashMap<>();
        result.put("remainingDraws", remainingDraws);

        return ApiResponse.success(result);
    }
}