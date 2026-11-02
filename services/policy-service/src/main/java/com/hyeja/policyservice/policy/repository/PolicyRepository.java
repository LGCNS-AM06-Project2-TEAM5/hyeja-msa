package com.hyeja.policyservice.policy.repository;

import com.hyeja.policyservice.policy.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// 목록·검색 쿼리는 정책 조회 작업(1차 PolicyRepository)에서 추가합니다.
@Repository
public interface PolicyRepository extends JpaRepository<Policy, String> {
}
