package com.main.aqarpaymentbackend.model;

import com.main.aqarpaymentbackend.model.enums.PaymentStatus;
import com.main.aqarpaymentbackend.model.enums.PaymentGateway;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.sql.Timestamp;
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
    private Integer id;

    @Column(name = "payment_due_id")
    private Integer paymentDueId;

    @Column(name = "customer_id")
    private Integer customerId;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "currency")
    private String currency;
    // EGP
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;      // PENDING, PAID, FAILED

    @Column(name = "payment_gateway")
    @Enumerated(EnumType.STRING)
    private PaymentGateway paymentGateway;

    @Column(name = "payment_method_id")
    private Integer paymentMethodId;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "provider_transaction_id")
    private Integer providerTransactionId;

    @Column(name = "checkout_url")
    private String checkoutUrl;

    @Column(name = "checkout_expires_at")
    private Instant checkoutExpiresAt;

    @Column(name = "transaction_created_at")
    private String transactionCreatedAt;

    @Column(name = "paid_flag")
    private Integer paidFlag;

    @Column(name = "transactionLink")
    private String transactionLink;

    @Column(name = "created_at")
    @CreationTimestamp
    private Timestamp createdAt;

    @Column(name = "paid_at")
    private Timestamp paidAt;
}
