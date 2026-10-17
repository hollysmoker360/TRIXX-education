package com.schoolsphere.subscriptions.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderAndProviderReference(String provider, String providerReference);

    List<Payment> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);
}
