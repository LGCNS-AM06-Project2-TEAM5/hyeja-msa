package com.hyeja.policyservice.region.repository;

import com.hyeja.policyservice.region.entity.Region;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegionRepository extends JpaRepository<Region, String> {
    List<Region> findAllByRegionCodeStartingWith(String sidoPrefix);
}
