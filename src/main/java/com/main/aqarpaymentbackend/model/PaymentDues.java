package com.main.aqarpaymentbackend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency = "EGP";


    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "description")
    private String description;
}
