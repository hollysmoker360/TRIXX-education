package com.schoolsphere.subscriptions.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<SchoolSubscription, UUID> {

    Optional<SchoolSubscription> findBySchoolId(UUID schoolId);

    List<SchoolSubscription> findByStatusIn(List<String> statuses);
}
