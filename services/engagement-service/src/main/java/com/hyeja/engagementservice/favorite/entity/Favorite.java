package com.hyeja.engagementservice.favorite.entity;


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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "favorite", uniqueConstraints = {
        @UniqueConstraint(name = "uk_favorite_member_policy", columnNames = {"member_id", "policy_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "favorite_id")
    private Long favoriteId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_favorite_policy"))
    private Policy policy;

    // 관심 해제는 실제 행 삭제로 처리하며, 재등록 시 새 엔티티를 저장합니다.
    @Builder
    public Favorite(Long memberId, Policy policy) {
        this.memberId = Objects.requireNonNull(memberId, "회원은 필수입니다.");
        this.policy = Objects.requireNonNull(policy, "정책은 필수입니다.");
    }
}
