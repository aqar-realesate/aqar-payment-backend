package com.main.aqarpaymentbackend.controller;

import com.main.aqarpaymentbackend.service.PaymentService;
import com.main.aqarpaymentbackend.util.ReturnObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/dues")
    public ResponseEntity<ReturnObject> getAllPaymentDues() {
        return paymentService.getAllPaymentDues();
    }

    @GetMapping("/dues/{dueId}")
    public ResponseEntity<ReturnObject> getPaymentDue(@PathVariable Integer dueId) {
        return paymentService.getPaymentDue(dueId);
    }
}
