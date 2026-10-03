package com.main.aqarpaymentbackend.repository;

import com.main.aqarpaymentbackend.model.PaymentDues;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

import java.util.List;

@Repository
public interface PaymentDuesRepository extends JpaRepository<PaymentDues, Integer> {

    PaymentDues findByIntentKey(String intentKey);
}
