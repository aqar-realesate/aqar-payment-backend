package com.main.aqarpaymentbackend.vendor.fawaterk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.aqarpaymentbackend.client.CustomerFeignClient;
import com.main.aqarpaymentbackend.model.Payment;
import com.main.aqarpaymentbackend.model.PaymentDues;
import com.main.aqarpaymentbackend.model.enums.PaymentGateway;
import com.main.aqarpaymentbackend.model.enums.PaymentStatus;
import com.main.aqarpaymentbackend.repository.PaymentDuesRepository;
import com.main.aqarpaymentbackend.repository.PaymentRepository;
import com.main.aqarpaymentbackend.util.JwtUtil;
import com.main.aqarpaymentbackend.util.ReturnObject;
import com.main.aqarpaymentbackend.vendor.fawaterk.client.FawaterkFeignClient;
import com.main.aqarpaymentbackend.vendor.fawaterk.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class FawaterkService {

    private final FawaterkFeignClient fawaterkFeignClient;
    private final CustomerFeignClient customerFeignClient;
    private final PaymentDuesRepository paymentDuesRepository;
    private final JwtUtil jwtUtil;
    private final PaymentRepository paymentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String grantType = "client_credentials";
    @Value("${fawaterk.client-id}")
    private String clientId;
    @Value("${fawaterk.client-secret}")
    private String clientSecret;

    public OAuthResponseDto fawaterkOAuth() {

        OAuthRequestDto request = OAuthRequestDto.builder()
                .grantType(grantType)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();

        return fawaterkFeignClient.fawaterkOAuth(request);
    }

    public ResponseEntity<ReturnObject> getPaymentMethods() {

        OAuthResponseDto oauth = fawaterkOAuth();
        if (oauth == null) {
            return new ResponseEntity<>(
                    new ReturnObject(
                            "Please try again",
                            false,
                            null
                    ), HttpStatus.BAD_REQUEST
            );
        }
        String token = "Bearer " + oauth.getAccessToken();
        GetPaymentMethodsDto response = fawaterkFeignClient.getPaymentMethods(token);

        return new ResponseEntity<>(
                new ReturnObject(
                        "Payment methods fetched successfully",
                        true,
                        response.getData()
                ), HttpStatus.OK
        );
    }

    @Transactional
    public CreateTransactionResponseDto createTransaction(String customerToken,
                                                          Integer dueId) {

        Integer customerId = jwtUtil.extractUserId(customerToken);

        Optional<PaymentDues> optPaymentDue = paymentDuesRepository.findById(dueId);
        if (optPaymentDue.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid due id");
        }
        PaymentDues paymentDue = optPaymentDue.get();

        if (!Objects.equals(customerId, optPaymentDue.get().getCustomerId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This payment due doesn't belong to this customer");
        }

        if (paymentDue.getStatus().equals(PaymentStatus.PAID) || paymentDue.getPaidFlag().equals(1)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The payment due already paid");
        }

        OAuthResponseDto oauth = fawaterkOAuth();

        String token = "Bearer " + oauth.getAccessToken();

        ReturnObject customerDetails = customerFeignClient.getCustomerDetails(customerToken).getBody();
        if (Boolean.FALSE.equals(customerDetails.getStatus()) && customerDetails.getData() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid session, please login again");
        }
        CustomerDetailsDto customerDetailsDto = objectMapper.convertValue(customerDetails.getData(), CustomerDetailsDto.class);

        ReturnObject unitDetails = customerFeignClient.getUnitDetails(customerToken, paymentDue.getUnitId()).getBody();
        if (Boolean.FALSE.equals(unitDetails.getStatus()) && unitDetails.getData() == null){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid session, please login again");
        }
        UnitDetailsDto unitDetailsDto = objectMapper.convertValue(unitDetails.getData(), UnitDetailsDto.class);

        CustomerInfoDto fawaterkCustomerInfo = CustomerInfoDto.builder()
                .firstName(customerDetailsDto.getFirstName())
                .lastName(customerDetailsDto.getLastName())
                .email(customerDetailsDto.getEmail())
                .build();

        CartItemDto fawaterkCartItem = CartItemDto.builder()
                .itemName(unitDetailsDto.getUnitName())
                .itemPrice(unitDetailsDto.getUnitPrice())
                .quantity(1)
                .build();

        RedirectionUrlsDto redirectionUrls = RedirectionUrlsDto.builder()
                .successUrl("https://urgent-moistness-trifle.ngrok-free.dev/api/payment/fawaterk/success")
                .pendingUrl("https://urgent-moistness-trifle.ngrok-free.dev/api/payment/fawaterk/pending")
                .failUrl("https://urgent-moistness-trifle.ngrok-free.dev/api/payment/fawaterk/fail")
                .build();


        CreateTransactionRequestDto request = CreateTransactionRequestDto.builder()
                .cartTotal(paymentDue.getAmount())
                .currency(paymentDue.getCurrency())
                .customer(fawaterkCustomerInfo)
                .cartItems(List.of(fawaterkCartItem))
                .redirectionUrls(redirectionUrls)
                .sendEmail(true)
                .build();

        CreateTransactionResponseDto response = fawaterkFeignClient.createTransaction(token, request);

        Payment payment = Payment.builder()
                .paymentDueId(dueId)
                .customerId(customerDetailsDto.getId())
                .amount(paymentDue.getAmount())
                .currency(paymentDue.getCurrency())
                .status(PaymentStatus.PENDING)
                .paymentGateway(PaymentGateway.FAWATERK)
                .providerReference(response.getData().getIntentKey())
                .checkoutUrl(response.getData().getUrl())
                .checkoutExpiresAt(Instant.ofEpochSecond(response.getData().getExpiresIn()))
                .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                .build();

        paymentRepository.save(payment);
        paymentDue.setIntentKey(response.getData().getIntentKey());
        paymentDue.setPaymentId(payment.getId());
        paymentDuesRepository.save(paymentDue);

        log.info("Transaction creation success, and both payment and payment dues tables are updated, payment: {}, payment due: {}", payment, paymentDue);
        return response;
    }

    @Transactional
    public ResponseEntity<ReturnObject> getTransactionData(String intentKey) {

        OAuthResponseDto oauth = fawaterkOAuth();
        String token = "Bearer " + oauth.getAccessToken();
        GetTransactionDataRequestDto request = GetTransactionDataRequestDto.builder()
                .intentKey(intentKey)
                .build();

        try {
            GetTransactionDataResponseDto transactionData = fawaterkFeignClient.getTransactionData(token, request);
            if (!transactionData.getStatus().equals("success") && transactionData.getData() == null) {
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Failed to fetch the fawaterk success response",
                                false,
                                null
                        ), HttpStatus.BAD_GATEWAY
                );
            }

            return new ResponseEntity<>(
                    new ReturnObject(
                            "Transaction details fetched successfully",
                            true,
                            transactionData.getData()
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

    @Transactional
    public ResponseEntity<ReturnObject> successTransaction(String intentKey) {

        try {
            OAuthResponseDto oauth = fawaterkOAuth();
            String token = "Bearer " + oauth.getAccessToken();
            GetTransactionDataRequestDto request = GetTransactionDataRequestDto.builder()
                    .intentKey(intentKey)
                    .build();

            GetTransactionDataResponseDto transactionData = fawaterkFeignClient.getTransactionData(token, request);
            if (!transactionData.getStatus().equals("success") && transactionData.getData() == null) {
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Failed to fetch the fawaterk success response",
                                false,
                                null
                        ), HttpStatus.BAD_GATEWAY
                );
            }

            PaymentDues paymentDue = paymentDuesRepository.findByIntentKey(intentKey);
            if (paymentDue == null) {
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Invalid intent key",
                                false,
                                null
                        ), HttpStatus.NOT_FOUND
                );
            }
            Payment payment = paymentRepository.findByProviderReference(intentKey);
            if (payment == null) {
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Invalid intent key",
                                false,
                                null
                        ), HttpStatus.NOT_FOUND
                );
            }

            // Check success of the transaction
            if (transactionData.getStatus().equals("success")) {

                if (!transactionData.getData().getPaidFlag().equals(1)) {
                    return new ResponseEntity<>(
                            new ReturnObject("Payment is not confirmed yet",
                                    false,
                                    null),
                            HttpStatus.BAD_REQUEST
                    );
                }

                checkPaidPaymentStatus(payment, paymentDue, transactionData);

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
                                "Success transaction",
                                true,
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

    @Transactional
    public ResponseEntity<ReturnObject> failTransaction(String intentKey, String errorMessage) {

        try {
            OAuthResponseDto oauth = fawaterkOAuth();
            String token = "Bearer " + oauth.getAccessToken();
            GetTransactionDataRequestDto request = GetTransactionDataRequestDto.builder()
                    .intentKey(intentKey)
                    .build();

            GetTransactionDataResponseDto transactionData = fawaterkFeignClient.getTransactionData(token, request);
            if (!transactionData.getStatus().equals("success") && transactionData.getData() == null) {
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Failed to fetch the fawaterk success response",
                                false,
                                null
                        ), HttpStatus.BAD_GATEWAY
                );
            }

            PaymentDues paymentDues = paymentDuesRepository.findByIntentKey(intentKey);
            if (paymentDues == null) {
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Invalid intent key",
                                false,
                                null
                        ), HttpStatus.NOT_FOUND
                );
            }
            Payment payment = paymentRepository.findByProviderReference(intentKey);
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

                if (!paymentDues.getStatus().equals(PaymentStatus.PENDING)) {
                    paymentDues.setStatus(PaymentStatus.PENDING);
                    paymentDues.setPaidFlag(transactionData.getData().getPaidFlag());
                }
                paymentDues.setTransactionLink(transactionData.getData().getTransactionLink());
                paymentDuesRepository.save(paymentDues);

                if (!payment.getStatus().equals(PaymentStatus.PAID) && !payment.getStatus().equals(PaymentStatus.FAILED)) {
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setPaidFlag(0);
                }
                payment.setTransactionLink(transactionData.getData().getTransactionLink());
                payment.setTransactionCreatedAt(transactionData.getData().getTransactionCreatedAt());
                paymentRepository.save(payment);

                FailTransactionResponseDto response = FailTransactionResponseDto.builder()
                        .status("failed")
                        .paymentDueId(paymentDues.getId())
                        .customerId(paymentDues.getCustomerId())
                        .customerEmail(transactionData.getData().getCustomerEmail())
                        .unitId(paymentDues.getUnitId())
                        .paymentId(payment.getId())
                        .paymentStatus(PaymentStatus.FAILED)
                        .errorMessage(errorMessage)
                        .fawaterkIntentKey(transactionData.getData().getIntentKey())
                        .fawaterkTransactionLink(transactionData.getData().getTransactionLink())
                        .fawaterkTransactionCreatedAt(transactionData.getData().getTransactionCreatedAt())
                        .paidFlag(transactionData.getData().getPaidFlag())
                        .paymentGateway(payment.getPaymentGateway())
                        .statusText(transactionData.getData().getStatusText())
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

    @Transactional
    public ResponseEntity<ReturnObject> pendingTransaction(String intentKey, String errorMessage) {

        try {
            OAuthResponseDto oauth = fawaterkOAuth();
            String token = "Bearer " + oauth.getAccessToken();
            GetTransactionDataRequestDto request = GetTransactionDataRequestDto.builder()
                    .intentKey(intentKey)
                    .build();

            GetTransactionDataResponseDto transactionData = fawaterkFeignClient.getTransactionData(token, request);
            if (!transactionData.getStatus().equals("success") && transactionData.getData() == null) {
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Failed to fetch the fawaterk success response",
                                false,
                                null
                        ), HttpStatus.BAD_GATEWAY
                );
            }

            PaymentDues paymentDues = paymentDuesRepository.findByIntentKey(intentKey);
            if (paymentDues == null) {
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Invalid intent key",
                                false,
                                null
                        ), HttpStatus.NOT_FOUND
                );
            }
            Payment payment = paymentRepository.findByProviderReference(intentKey);
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

                if (!paymentDues.getStatus().equals(PaymentStatus.PENDING)) {
                    paymentDues.setStatus(PaymentStatus.PENDING);
                    paymentDues.setPaidFlag(transactionData.getData().getPaidFlag());
                }
                paymentDues.setTransactionLink(transactionData.getData().getTransactionLink());
                paymentDuesRepository.save(paymentDues);

                if (!payment.getStatus().equals(PaymentStatus.PAID) && !payment.getStatus().equals(PaymentStatus.PENDING)) {
                    payment.setStatus(PaymentStatus.PENDING);
                    payment.setPaidFlag(0);
                }
                payment.setTransactionLink(transactionData.getData().getTransactionLink());
                payment.setTransactionCreatedAt(transactionData.getData().getTransactionCreatedAt());
                paymentRepository.save(payment);

                FailTransactionResponseDto response = FailTransactionResponseDto.builder()
                        .status("pending")
                        .paymentDueId(paymentDues.getId())
                        .customerId(paymentDues.getCustomerId())
                        .customerEmail(transactionData.getData().getCustomerEmail())
                        .unitId(paymentDues.getUnitId())
                        .paymentId(payment.getId())
                        .paymentStatus(PaymentStatus.PENDING)
                        .fawaterkIntentKey(transactionData.getData().getIntentKey())
                        .fawaterkTransactionLink(transactionData.getData().getTransactionLink())
                        .fawaterkTransactionCreatedAt(transactionData.getData().getTransactionCreatedAt())
                        .paidFlag(transactionData.getData().getPaidFlag())
                        .paymentGateway(payment.getPaymentGateway())
                        .statusText(transactionData.getData().getStatusText())
                        .errorMessage(errorMessage)
                        .amount(transactionData.getData().getTotal())
                        .currency(transactionData.getData().getCurrency())
                        .build();

                return new ResponseEntity<>(
                        new ReturnObject(
                                "Pending transaction",
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


    public void checkPaidPaymentStatus(Payment payment,
                                       PaymentDues paymentDue,
                                       GetTransactionDataResponseDto transactionData) {
        if (
                !paymentDue.getStatus().equals(PaymentStatus.PAID)
                        || !paymentDue.getPaidFlag().equals(1)
                        || paymentDue.getPaidAt() == null
                        || paymentDue.getTransactionLink() == null
        ) {

            paymentDue.setStatus(PaymentStatus.PAID);
            paymentDue.setPaidAt(Timestamp.from(Instant.parse(transactionData.getData().getPaidAt())));
            paymentDue.setPaidFlag(transactionData.getData().getPaidFlag());
            paymentDue.setTransactionLink(transactionData.getData().getTransactionLink());
            paymentDuesRepository.save(paymentDue);
        }

        if (
                !payment.getStatus().equals(PaymentStatus.PAID)
                        || payment.getProviderTransactionId() == null
                        || payment.getTransactionCreatedAt() == null
                        || !payment.getPaidFlag().equals(1)
                        || payment.getTransactionLink() == null
                        || payment.getPaidAt() == null
        ) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setProviderTransactionId(transactionData.getData().getTransactionId());
            payment.setTransactionCreatedAt(transactionData.getData().getTransactionCreatedAt());
            payment.setPaidFlag(transactionData.getData().getPaidFlag());
            payment.setTransactionLink(transactionData.getData().getTransactionLink());
            payment.setPaidAt(Timestamp.from(Instant.parse(transactionData.getData().getPaidAt())));
            paymentRepository.save(payment);
        }
    }
}
