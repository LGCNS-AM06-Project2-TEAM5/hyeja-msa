package com.hyeja.engagementservice.notification.scheduler;

import com.hyeja.engagementservice.notification.service.NotificationGenerationService;
import com.hyeja.engagementservice.global.config.SchedulingConfig;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationGenerationService notificationGenerationService;
    private final Clock clock;

    @Scheduled(cron = "0 5 0 * * *", zone = SchedulingConfig.KOREA_TIME_ZONE)
    public void createDeadlineNotifications() {
        LocalDate baseDate = LocalDate.now(clock);
        int createdCount = notificationGenerationService.createDeadlineNotifications(baseDate);
        log.info("관심 정책 마감 D-7 알림 {}건 생성 완료 - 기준일: {}", createdCount, baseDate);
    }
}
