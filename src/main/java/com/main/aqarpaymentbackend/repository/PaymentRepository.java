package com.main.aqarpaymentbackend.repository;

import com.main.aqarpaymentbackend.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    Payment findByProviderReference(String providerReference);
    Payment findByProviderOrderId(Long providerOrderId);
}
