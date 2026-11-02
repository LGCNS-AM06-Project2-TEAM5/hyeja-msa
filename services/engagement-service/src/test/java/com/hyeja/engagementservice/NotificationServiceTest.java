package com.hyeja.engagementservice;

import com.hyeja.engagementservice.favorite.service.FavoriteService;
import com.hyeja.engagementservice.notification.service.*;
import com.hyeja.engagementservice.policy.entity.Policy;
import com.hyeja.engagementservice.policy.enums.*;
import com.hyeja.engagementservice.policy.repository.PolicyRepository;
import com.hyeja.engagementservice.global.exception.GeneralException;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class NotificationServiceTest {
    @Autowired FavoriteService favorites;
    @Autowired NotificationService notifications;
    @Autowired NotificationGenerationService generation;
    @Autowired PolicyRepository policies;
    @Autowired org.springframework.web.context.WebApplicationContext context;
    final LocalDate today = LocalDate.of(2026, 11, 2);

    @BeforeEach void setup() {
        Policy policy = new Policy();
        policy.setPolicyId("P1"); policy.setPolicyName("월세 100% 지원");
        policy.setCategories(Set.of(PolicyCategory.MONTHLY_RENT));
        policy.setSupportContent("지원 내용"); policy.setApplyEndDate(today.plusDays(7));
        policy.setApplyPeriodCode(PolicyApplyPeriod.SPECIFIC_PERIOD); policy.setActiveYn(true);
        policies.saveAndFlush(policy);
    }

    @Test void deadlineIdempotencyOwnershipAndReadCount() {
        favorites.createFavorite(1L, "P1");
        assertThat(generation.createDeadlineNotifications(today.minusDays(1))).isZero();
        var clock = java.time.Clock.fixed(java.time.Instant.parse("2026-11-01T15:00:00Z"),
                java.time.ZoneId.of("Asia/Seoul"));
        new com.hyeja.engagementservice.notification.scheduler.NotificationScheduler(generation, clock)
                .createDeadlineNotifications();
        assertThat(generation.createDeadlineNotifications(today)).isZero();
        var page = notifications.getNotifications(1L, 0, 8);
        assertThat(page.getUnreadCount()).isEqualTo(1);
        Long id = page.getNotifications().get(0).getNotificationId();
        assertThatThrownBy(() -> notifications.markAsRead(2L, id)).isInstanceOf(GeneralException.class);
        notifications.markAsRead(1L, id);
        assertThat(notifications.getNotifications(1L, 0, 8).getUnreadCount()).isZero();
        notifications.deleteNotification(1L, id);
        assertThat(notifications.getNotifications(1L, 0, 8).getTotalElements()).isZero();
    }


    @Test void manualGenerationEndpointIsAbsent() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/notification/admin/generate")
                        .header("X-Member-Id", "1").param("memberId", "1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }
}
