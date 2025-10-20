package org.amway.service;

import org.amway.dto.response.ActivityResponse;
import org.amway.dto.response.PrizeResponse;
import org.amway.entity.LotteryActivity;
import org.amway.entity.Prize;
import org.amway.repository.LotteryActivityRepository;
import org.amway.repository.PrizeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final LotteryActivityRepository activityRepository;
    private final PrizeRepository prizeRepository;

    /**
     * 獲取所有進行中的活動
     */
    @Cacheable(value = "activeActivities", key = "'all'")
    public List<ActivityResponse> getActiveActivities() {
        List<LotteryActivity> activities = activityRepository
                .findActiveActivities(LocalDateTime.now());

        return activities.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 獲取活動詳情
     */
    @Cacheable(value = "activity", key = "#activityId")
    public ActivityResponse getActivityDetail(Long activityId) {
        LotteryActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("活動不存在"));

        return convertToResponse(activity);
    }

    /**
     * 轉換為響應對象
     */
    private ActivityResponse convertToResponse(LotteryActivity activity) {
        List<Prize> prizes = prizeRepository.findByActivityId(activity.getId());

        List<PrizeResponse> prizeResponses = prizes.stream()
                .map(prize -> PrizeResponse.builder()
                        .id(prize.getId())
                        .name(prize.getName())
                        .description(prize.getDescription())
                        .totalStock(prize.getTotalStock())
                        .remainingStock(prize.getRemainingStock())
                        .probability(prize.getProbability())
                        .prizeType(prize.getPrizeType().name())
                        .imageUrl(prize.getImageUrl())
                        .build())
                .collect(Collectors.toList());

        return ActivityResponse.builder()
                .id(activity.getId())
                .name(activity.getName())
                .description(activity.getDescription())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .maxDrawsPerUser(activity.getMaxDrawsPerUser())
                .status(activity.getStatus().name())
                .prizes(prizeResponses)
                .build();
    }
}