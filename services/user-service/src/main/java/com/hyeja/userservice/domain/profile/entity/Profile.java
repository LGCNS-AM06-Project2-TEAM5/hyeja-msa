package com.hyeja.userservice.domain.profile.entity;

import com.hyeja.userservice.global.baseEntity.BaseEntity;
import com.hyeja.userservice.domain.member.entity.Member;
import com.hyeja.userservice.domain.profile.enums.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;

@Getter
@Entity
@Table(name = "profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile extends BaseEntity {

    @Id
    @Column(name = "email", nullable = false, length = 100, updatable = false)
    private String email;

    // PK와 FK가 같은 컬럼인 @OneToOne은 Hibernate가 회원의 PK를 참조하는 것으로 해석합니다.
    // 이메일 참조에는 @ManyToOne을 사용하되, profile.email의 PK 제약으로 실제 관계는 1:1입니다.
    // email 필드가 컬럼을 저장하므로 연관관계에서는 같은 컬럼을 중복 저장하지 않습니다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "email", referencedColumnName = "email",
            nullable = false, unique = true, insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_profile_member_email"))
    private Member member;

    // MSA 전환: region은 user_db의 읽기 전용 복사본이므로 Region 엔티티와 FK로 연결하지 않고
    // 시군구 코드(5자리)만 저장합니다. 원본(모놀리스)은 @ManyToOne Region이었습니다.
    @Column(name = "region_code", nullable = false, length = 5, columnDefinition = "char(5)")
    private String regionCode;

    @Column(name = "birth", nullable = false)
    private LocalDate birth;

    // 기존 VARCHAR 컬럼을 유지하면서 enum 이름을 저장합니다.
    // @JdbcTypeCode(SqlTypes.VARCHAR)를 쓰면 Hibernate 7 + H2에서 CHECK 제약 때문에 insert가 실패하므로
    // columnDefinition으로 지정합니다.
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_code", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private EmploymentStatus employmentCode;

    @Column(name = "houseless_yn", nullable = false)
    private Boolean houselessYn;

    @Enumerated(EnumType.STRING)
    @Column(name = "marriage_code", length = 10, columnDefinition = "varchar(10)")
    private MaritalStatus marriageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "income_range_code", length = 10, columnDefinition = "varchar(10)")
    private IncomeRange incomeRangeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "education_code", length = 30, columnDefinition = "varchar(30)")
    private EducationLevel educationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "housing_type", length = 20, columnDefinition = "varchar(20)")
    private HousingType housingType;

    @Builder
    public Profile(Member member, String regionCode, LocalDate birth, EmploymentStatus employmentCode,
                   Boolean houselessYn, MaritalStatus marriageCode, IncomeRange incomeRangeCode,
                   EducationLevel educationCode, HousingType housingType) {
        this.member = Objects.requireNonNull(member, "회원은 필수입니다.");
        this.email = Objects.requireNonNull(member.getEmail(), "회원 이메일은 필수입니다.");
        // 조건 8개는 수정과 같은 방식으로 채웁니다.
        update(regionCode, birth, employmentCode, houselessYn, marriageCode, incomeRangeCode, educationCode, housingType);
    }

    // 내 조건 수정: 수정 폼 전체를 다시 저장하므로 모든 조건을 통째로 교체합니다.
    // 선택 항목(혼인·소득·학력·주거 형태)에 null이 오면 기존 값이 지워집니다. 회원(이메일)은 바꾸지 않습니다.
    public void update(String regionCode, LocalDate birth, EmploymentStatus employmentCode, Boolean houselessYn,
                       MaritalStatus marriageCode, IncomeRange incomeRangeCode, EducationLevel educationCode,
                       HousingType housingType) {
        this.regionCode = Objects.requireNonNull(regionCode, "거주 지역은 필수입니다.");
        this.birth = birth;
        this.employmentCode = employmentCode;
        this.houselessYn = houselessYn;
        this.marriageCode = marriageCode;
        this.incomeRangeCode = incomeRangeCode;
        this.educationCode = educationCode;
        this.housingType = housingType;
    }
}
