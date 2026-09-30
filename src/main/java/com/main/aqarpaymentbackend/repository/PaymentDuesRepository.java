package com.main.aqarpaymentbackend.repository;

import com.main.aqarpaymentbackend.model.PaymentDues;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentDuesRepository extends JpaRepository<PaymentDues, Integer> {

    PaymentDues findByIntentKey(String intentKey);
}
