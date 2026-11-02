package com.hyeja.engagementservice.notification.entity;


import com.hyeja.engagementservice.policy.entity.Policy;
import com.hyeja.engagementservice.global.baseEntity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notification", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_notification_member_policy_deadline",
                columnNames = {"member_id", "policy_id", "deadline_date"}
        )
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_notification_policy"))
    private Policy policy;

    @Column(name = "read_yn", nullable = false)
    private Boolean readYn;

    @Column(name = "deadline_date", nullable = false, updatable = false)
    private LocalDate deadlineDate;

    @Builder
    public Notification(Long memberId, Policy policy, LocalDate deadlineDate) {
        this.memberId = Objects.requireNonNull(memberId, "회원은 필수입니다.");
        this.policy = Objects.requireNonNull(policy, "정책은 필수입니다.");
        this.deadlineDate = Objects.requireNonNull(deadlineDate, "알림 대상 마감일은 필수입니다.");
        this.readYn = false;
    }

    public void markAsRead() {
        this.readYn = true;
    }
}
