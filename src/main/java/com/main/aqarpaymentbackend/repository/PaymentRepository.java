package com.main.aqarpaymentbackend.repository;

import com.main.aqarpaymentbackend.model.Payment;
import com.main.aqarpaymentbackend.model.enums.PaymentGateway;
import com.main.aqarpaymentbackend.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    // Does this due already have a successful payment?
    boolean existsByPaymentDueIdAndStatus(
            Integer paymentDueId,
            PaymentStatus status
    );

    // Find the newest pending attempt for this gateway
    Optional<Payment>
    findFirstByPaymentDueIdAndStatusAndPaymentGatewayOrderByIdDesc(
            Integer paymentDueId,
            PaymentStatus status,
            PaymentGateway paymentGateway
    );

    // Find the Fawaterk checkout associated with this notification
    Optional<Payment> findByProviderReferenceAndPaymentGateway(
            String providerReference,
            PaymentGateway paymentGateway
    );
}
