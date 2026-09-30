package com.main.aqarpaymentbackend.model;

import com.main.aqarpaymentbackend.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_dues")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDues {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "customer_id", nullable = false)
    private Integer customerId;

    @Column(name = "unit_id", nullable = false)
    private Integer unitId;

    @Column(name = "request_id", nullable = false)
    private Integer requestId;

    @Column(name = "payment_id")
    private Integer paymentId;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency = "EGP";

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.PENDING;      // PENDING, PAID, FAILED

    @Column(name = "paid_at")
    private Timestamp paidAt;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "intent_key")
    private String intentKey;

    @Column(name = "paid_flag")
    private Integer paidFlag;

    @Column(name = "transactionLink")
    private String transactionLink;

    @Column(name = "description")
    private String description;
}
