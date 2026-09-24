package com.main.aqarpaymentbackend.model;

import com.main.aqarpaymentbackend.model.enums.PaymentStatus;
import com.main.aqarpaymentbackend.model.enums.PaymentGateway;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer customerId;

    private Integer unitRequestId;

    private Long amountCents;

    private String currency;
    // EGP
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;      // PENDING, PAID, FAILED

    @Enumerated(EnumType.STRING)
    private PaymentGateway gateway;

    private String providerReference;

    private String providerTransactionId;

    private String checkoutUrl;

    private Instant checkoutExpiresAt;

    private Instant createdAt;

    private Instant paidAt;
}
