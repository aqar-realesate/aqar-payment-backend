package com.main.aqarpaymentbackend.security;

import com.main.aqarpaymentbackend.dto.PaymentDueResponseDto;
import com.main.aqarpaymentbackend.model.PaymentDues;
import com.main.aqarpaymentbackend.repository.PaymentDuesRepository;
import com.main.aqarpaymentbackend.util.ReturnObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentDuesRepository paymentDuesRepository;

    @Transactional
    public ResponseEntity<ReturnObject> getAllPaymentDues() {

        List<PaymentDues> paymentDues = paymentDuesRepository.findAll();

        List<PaymentDueResponseDto> response = new ArrayList<>();
        for (PaymentDues due: paymentDues) {
            PaymentDueResponseDto dueDto = PaymentDueResponseDto.builder()
                    .dueId(due.getId())
                    .customerId(due.getCustomerId())
                    .unitId(due.getUnitId())
                    .requestId(due.getRequestId())
                    .amount(due.getAmount())
                    .currency(due.getCurrency())
                    .dueDate(due.getDueDate())
                    .description(due.getDescription())
                    .build();

            response.add(dueDto);
        }

        return new ResponseEntity<>(new ReturnObject(
                "All payment dues fetched successfully",
                true,
                response
        ), HttpStatus.OK);
    }

    public ResponseEntity<ReturnObject> getPaymentDue(Integer dueId) {

        PaymentDues due = paymentDuesRepository.findById(dueId).get();

        PaymentDueResponseDto response = PaymentDueResponseDto.builder()
                .dueId(due.getId())
                .customerId(due.getCustomerId())
                .unitId(due.getUnitId())
                .requestId(due.getRequestId())
                .amount(due.getAmount())
                .currency(due.getCurrency())
                .dueDate(due.getDueDate())
                .description(due.getDescription())
                .build();

        return new ResponseEntity<>(new ReturnObject(
                "Payment due fetched successfully",
                true,
                response
        ), HttpStatus.OK);
    }
}
