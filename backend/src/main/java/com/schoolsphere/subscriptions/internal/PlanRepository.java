package com.schoolsphere.subscriptions.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    Optional<Plan> findByCodeAndActiveTrue(String code);

    List<Plan> findByActiveTrueOrderBySortOrderAsc();
}
