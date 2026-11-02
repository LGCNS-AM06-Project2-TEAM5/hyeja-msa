package com.hyeja.engagementservice.policy.repository;
import com.hyeja.engagementservice.policy.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PolicyRepository extends JpaRepository<Policy, String> {}
