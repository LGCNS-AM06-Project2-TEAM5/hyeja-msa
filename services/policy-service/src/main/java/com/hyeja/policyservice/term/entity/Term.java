package com.hyeja.policyservice.term.entity;

import com.hyeja.policyservice.global.baseEntity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "term")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Term extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "term_id")
    private Long termId;

    @Column(name = "term", nullable = false, length = 50)
    private String term;

    @Column(name = "easy_description", nullable = false, length = 500)
    private String easyDescription;

    @Column(name = "example", length = 300)
    private String example;

    @Builder
    public Term(String term, String easyDescription, String example) {
        this.term = Objects.requireNonNull(term, "용어는 필수입니다.");
        this.easyDescription = Objects.requireNonNull(easyDescription, "쉬운 설명은 필수입니다.");
        this.example = example;
    }
}
