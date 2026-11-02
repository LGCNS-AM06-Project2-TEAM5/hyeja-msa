package com.hyeja.userservice.domain.region.repository;

import com.hyeja.userservice.domain.region.entity.Region;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegionRepository extends JpaRepository<Region, String> {
    List<Region> findAllByRegionCodeStartingWith(String sidoPrefix);
}
