package com.hyeja.policyservice.cardnews.entity;

import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.global.baseEntity.BaseEntity;
import jakarta.persistence.CheckConstraint;
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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "card_news",
        uniqueConstraints = @UniqueConstraint(name = "uk_card_news_policy_card_no",
                columnNames = {"policy_id", "card_no"}),
        check = @CheckConstraint(name = "ck_card_news_card_no", constraint = "card_no BETWEEN 1 AND 4"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardNews extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_news_id")
    private Long cardNewsId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_card_news_policy"))
    private Policy policy;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "body", nullable = false, length = 500)
    private String body;

    @Column(name = "card_no", nullable = false)
    private Long cardNo;

    @Builder
    public CardNews(Policy policy, String title, String body, Long cardNo) {
        this.policy = Objects.requireNonNull(policy, "정책은 필수입니다.");
        this.title = title;
        this.body = Objects.requireNonNull(body, "카드 문구는 필수입니다.");
        if (cardNo == null || cardNo < 1 || cardNo > 4) {
            throw new IllegalArgumentException("카드 번호는 1~4여야 합니다.");
        }
        this.cardNo = cardNo;
    }
}
