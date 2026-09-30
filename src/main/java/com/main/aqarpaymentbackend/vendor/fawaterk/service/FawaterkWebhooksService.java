package com.main.aqarpaymentbackend.vendor.fawaterk.service;

import com.main.aqarpaymentbackend.model.Payment;
import com.main.aqarpaymentbackend.model.PaymentDues;
import com.main.aqarpaymentbackend.model.enums.PaymentStatus;
import com.main.aqarpaymentbackend.repository.PaymentDuesRepository;
import com.main.aqarpaymentbackend.repository.PaymentRepository;
import com.main.aqarpaymentbackend.util.ReturnObject;
import com.main.aqarpaymentbackend.vendor.fawaterk.client.FawaterkFeignClient;
import com.main.aqarpaymentbackend.vendor.fawaterk.dto.*;
import com.main.aqarpaymentbackend.vendor.fawaterk.util.CalculateHashKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class FawaterkWebhooksService {

    private final PaymentRepository paymentRepository;
    private final PaymentDuesRepository paymentDuesRepository;
    private final FawaterkService fawaterkService;
    private final FawaterkFeignClient fawaterkFeignClient;

    @Value("${fawaterk.hash-api-key}")
    private String hashApiKey;

    public ResponseEntity<ReturnObject> paidWebhook(PaidWebhookRequestDto request) {

        try {
            String calculatedHash = CalculateHashKeys.calculateWebhookHash(
                    request.getIntentKey(),
                    request.getTransactionId(),
                    request.getPaymentMethod(),
                    hashApiKey
            );

            if (!calculatedHash.equals(request.getTransactionHashKey())) {
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Invalid webhook signature",
                                false
                                ,null
                        ), HttpStatus.BAD_REQUEST
                );
            }


            OAuthResponseDto oauth = fawaterkService.fawaterkOAuth();
            String token = "Bearer " + oauth.getAccessToken();
            GetTransactionDataRequestDto intentKey = GetTransactionDataRequestDto.builder()
                    .intentKey(request.getIntentKey())
                    .build();

            GetTransactionDataResponseDto transactionData = fawaterkFeignClient.getTransactionData(token, intentKey);

            if (!transactionData.getStatus().equals("success") && transactionData.getData() == null) {
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Failed to fetch the fawaterk success response",
                                false,
                                null
                        ), HttpStatus.BAD_GATEWAY
                );
            }

            PaymentDues paymentDue = paymentDuesRepository.findByIntentKey(request.getIntentKey());
            if (paymentDue == null) {
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Invalid intent key",
                                false,
                                null
                        ), HttpStatus.NOT_FOUND
                );
            }
            Payment payment = paymentRepository.findByProviderReference(request.getIntentKey());
            if (payment == null) {
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Invalid intent key",
                                false,
                                null
                        ), HttpStatus.NOT_FOUND
                );
            }


            if (!transactionData.getData().getPaidFlag().equals(1)) {
                return new ResponseEntity<>(
                        new ReturnObject("Payment is not confirmed yet",
                                false,
                                null),
                        HttpStatus.BAD_REQUEST
                );
            }

            fawaterkService.checkPaidPaymentStatus(payment, paymentDue, transactionData);

            SuccessTransactionResponseDto response = SuccessTransactionResponseDto.builder()
                    .status(transactionData.getStatus())
                    .paymentDueId(paymentDue.getId())
                    .customerId(paymentDue.getCustomerId())
                    .customerEmail(transactionData.getData().getCustomerEmail())
                    .unitId(paymentDue.getUnitId())
                    .paymentId(payment.getId())
                    .paymentStatus(payment.getStatus())
                    .fawaterkIntentKey(payment.getProviderReference())
                    .fawaterkTransactionId(payment.getProviderTransactionId())
                    .fawaterkTransactionLink(payment.getTransactionLink())
                    .fawaterkTransactionCreatedAt(transactionData.getData().getTransactionCreatedAt())
                    .paidFlag(transactionData.getData().getPaidFlag())
                    .paidAt(transactionData.getData().getPaidAt())
                    .paymentGateway(payment.getPaymentGateway())
                    .statusText(transactionData.getData().getStatusText())
                    .amoundPaid(transactionData.getData().getTotal())
                    .currency(transactionData.getData().getCurrency())
                    .build();


            return new ResponseEntity<>(
                    new ReturnObject(
                            "Paid webhook received successfully",
                            true,
                            response
                    ), HttpStatus.OK
            );

        } catch (Exception e) {
            return new ResponseEntity<>(
                    new ReturnObject(
                            e.getMessage(),
                            false,
                            null
                    ), HttpStatus.BAD_REQUEST
            );
        }
    }

    public ResponseEntity<ReturnObject> failedWebhook(FailedWebhookRequestDto request) {

        try {

            String calculatedHash = CalculateHashKeys.calculateWebhookHash(
                    request.getIntentKey(),
                    request.getTransactionId(),
                    request.getPaymentMethod(),
                    hashApiKey
            );

            if (!calculatedHash.equals(request.getHashKey())) {
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Invalid webhook signature",
                                false
                                ,null
                        ), HttpStatus.BAD_REQUEST
                );
            }

            OAuthResponseDto oauth = fawaterkService.fawaterkOAuth();
            String token = "Bearer " + oauth.getAccessToken();
            GetTransactionDataRequestDto intentKey = GetTransactionDataRequestDto.builder()
                    .intentKey(request.getIntentKey())
                    .build();

            GetTransactionDataResponseDto transactionData = fawaterkFeignClient.getTransactionData(token, intentKey);

            if (!transactionData.getStatus().equals("success") && transactionData.getData() == null) {
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Failed to fetch the fawaterk success response",
                                false,
                                null
                        ), HttpStatus.BAD_GATEWAY
                );
            }

            PaymentDues paymentDue = paymentDuesRepository.findByIntentKey(request.getIntentKey());
            if (paymentDue == null) {
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Invalid intent key",
                                false,
                                null
                        ), HttpStatus.NOT_FOUND
                );
            }
            Payment payment = paymentRepository.findByProviderReference(request.getIntentKey());
            if (payment == null) {
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Invalid intent key",
                                false,
                                null
                        ), HttpStatus.NOT_FOUND
                );
            }

            if (transactionData.getData().getPaidFlag().equals(1)) {
                fawaterkService.checkPaidPaymentStatus(payment, paymentDue, transactionData);
                return new ResponseEntity<>(
                        new ReturnObject("Payment is already paid", true, null),
                        HttpStatus.OK
                );
            }

            if (payment.getStatus().equals(PaymentStatus.PAID) || paymentDue.getStatus().equals(PaymentStatus.PAID)) {
                return new ResponseEntity<>(
                        new ReturnObject("Paid payment left unchanged", true, null),
                        HttpStatus.OK
                );
            }

            if (!transactionData.getData().getPaidFlag().equals(1)) {

                if (!paymentDue.getStatus().equals(PaymentStatus.PENDING)) {
                    paymentDue.setStatus(PaymentStatus.PENDING);
                    paymentDue.setPaidFlag(transactionData.getData().getPaidFlag());
                }
                paymentDue.setTransactionLink(transactionData.getData().getTransactionLink());
                paymentDuesRepository.save(paymentDue);

                if (!payment.getStatus().equals(PaymentStatus.PAID) && !payment.getStatus().equals(PaymentStatus.FAILED)) {
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setPaidFlag(0);
                }
                payment.setTransactionLink(transactionData.getData().getTransactionLink());
                payment.setTransactionCreatedAt(transactionData.getData().getTransactionCreatedAt());
                paymentRepository.save(payment);

                FailTransactionResponseDto response = FailTransactionResponseDto.builder()
                        .status("failed")
                        .paymentDueId(paymentDue.getId())
                        .customerId(paymentDue.getCustomerId())
                        .customerEmail(transactionData.getData().getCustomerEmail())
                        .unitId(paymentDue.getUnitId())
                        .paymentId(payment.getId())
                        .paymentStatus(PaymentStatus.FAILED)
                        .fawaterkIntentKey(transactionData.getData().getIntentKey())
                        .fawaterkTransactionLink(transactionData.getData().getTransactionLink())
                        .fawaterkTransactionCreatedAt(transactionData.getData().getTransactionCreatedAt())
                        .paidFlag(transactionData.getData().getPaidFlag())
                        .paymentGateway(payment.getPaymentGateway())
                        .statusText(transactionData.getData().getStatusText())
                        .errorMessage(request.getErrorMessage())
                        .amount(transactionData.getData().getTotal())
                        .currency(transactionData.getData().getCurrency())
                        .build();

                return new ResponseEntity<>(
                        new ReturnObject(
                                "Failed transaction",
                                false,
                                response
                        ), HttpStatus.OK
                );
            }

        } catch (Exception e) {
            return new ResponseEntity<>(
                    new ReturnObject(
                            e.getMessage(),
                            false,
                            null
                    ), HttpStatus.BAD_REQUEST
            );
        }

        return new ResponseEntity<>(
                new ReturnObject(
                        "Bad Request",
                        false,
                        null
                ),HttpStatus.BAD_REQUEST
        );

    }
}
