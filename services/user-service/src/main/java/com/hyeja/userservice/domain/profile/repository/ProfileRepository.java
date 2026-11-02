package com.hyeja.userservice.domain.profile.repository;

import com.hyeja.userservice.domain.profile.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// 회원 조건(profile 테이블) 조회·저장을 담당합니다. PK는 회원 이메일입니다.
@Repository
public interface ProfileRepository extends JpaRepository<Profile, String> {
}
