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
    @Value("${fawaterk.success-url}")
    private String successUrl;
    @Value("${fawaterk.pending-url}")
    private String pendingUrl;
    @Value("${fawaterk.fail-url}")
    private String failUrl;


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
            log.warn("Fawaterk payment methods unavailable: authentication returned no response");
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
        log.info("Fawaterk payment methods fetched");

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

        // Identify the customer requesting this checkout.
        Integer customerId = jwtUtil.extractUserId(customerToken);
        log.info("Fawaterk checkout requested: dueId={} customerId={}", dueId, customerId);

        // Find the due that the customer wants to pay.
        Optional<PaymentDues> optPaymentDue = paymentDuesRepository.findById(dueId);
        if (optPaymentDue.isEmpty()) {
            log.warn("Fawaterk checkout rejected: due not found, dueId={}", dueId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid due id");
        }
        PaymentDues paymentDue = optPaymentDue.get();

        // Check that this due belongs to the requesting customer.
        if (!Objects.equals(customerId, optPaymentDue.get().getCustomerId())) {
            log.warn("Fawaterk checkout rejected: ownership mismatch, dueId={} customerId={}", dueId, customerId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This payment due doesn't belong to this customer");
        }

        // Stop if the due has already been paid.
        if (paymentDue.getStatus().equals(PaymentStatus.PAID) || paymentDue.getPaidFlag().equals(1)) {
            log.info("Fawaterk checkout rejected: due already paid, dueId={}", dueId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The payment due already paid");
        }

        // Reuse a live Fawaterk checkout or reject a live checkout from another gateway.
        if (paymentDue.getPaymentId() != null) {
            Payment existing = paymentRepository.findById(paymentDue.getPaymentId()).orElse(null);

            if (existing != null
                    && existing.getStatus() == PaymentStatus.PENDING
                    && existing.getCheckoutUrl() != null
                    && existing.getCheckoutExpiresAt() != null
                    && existing.getCheckoutExpiresAt().isAfter(Instant.now())) {

                if (existing.getPaymentGateway() != PaymentGateway.FAWATERK) {
                    log.warn("Fawaterk checkout rejected: another gateway has a live checkout, dueId={} paymentId={} gateway={}",
                            dueId, existing.getId(), existing.getPaymentGateway());
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "Another checkout is still active");
                }

                log.info("Fawaterk checkout reused: dueId={} paymentId={} intentKey={}",
                        dueId, existing.getId(), existing.getProviderReference());
                return CreateTransactionResponseDto.builder()
                        .paymentId(existing.getId())
                        .checkoutUrl(existing.getCheckoutUrl())
                        .intentKey(existing.getProviderReference())
                        .expiresIn((int) java.time.Duration.between(Instant.now(), existing.getCheckoutExpiresAt()).getSeconds())
                        .paymentStatus(existing.getStatus())
                        .paymentGateway(existing.getPaymentGateway())
                        .build();
            }
        }

        // Authenticate before asking Fawaterk to create a transaction.
        OAuthResponseDto oauth = fawaterkOAuth();

        String token = "Bearer " + oauth.getAccessToken();

        // Load the customer details used in the checkout request.
        ReturnObject customerDetails = customerFeignClient.getCustomerDetails(customerToken).getBody();
        if (Boolean.FALSE.equals(customerDetails.getStatus()) && customerDetails.getData() == null) {
            log.warn("Fawaterk checkout rejected: customer details unavailable, dueId={} customerId={}", dueId, customerId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid session, please login again");
        }
        CustomerDetailsDto customerDetailsDto = objectMapper.convertValue(customerDetails.getData(), CustomerDetailsDto.class);

        // Load the unit details used as the checkout item.
        ReturnObject unitDetails = customerFeignClient.getUnitDetails(customerToken, paymentDue.getUnitId()).getBody();
        if (Boolean.FALSE.equals(unitDetails.getStatus()) && unitDetails.getData() == null){
            log.warn("Fawaterk checkout rejected: unit details unavailable, dueId={} unitId={}", dueId, paymentDue.getUnitId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid session, please login again");
        }
        UnitDetailsDto unitDetailsDto = objectMapper.convertValue(unitDetails.getData(), UnitDetailsDto.class);

        // Build the customer information for Fawaterk.
        CustomerInfoDto fawaterkCustomerInfo = CustomerInfoDto.builder()
                .firstName(customerDetailsDto.getFirstName())
                .lastName(customerDetailsDto.getLastName())
                .email(customerDetailsDto.getEmail())
                .build();

        // Build the item being paid for.
        CartItemDto fawaterkCartItem = CartItemDto.builder()
                .itemName(unitDetailsDto.getUnitName())
                .itemPrice(unitDetailsDto.getUnitPrice())
                .quantity(1)
                .build();

        // Tell Fawaterk where to redirect the customer after checkout.
        RedirectionUrlsDto redirectionUrls = RedirectionUrlsDto.builder()
                .successUrl(successUrl)
                .pendingUrl(pendingUrl)
                .failUrl(failUrl)
                .build();


        // Build the Fawaterk transaction request from the due and customer details.
        CreateTransactionRequestDto request = CreateTransactionRequestDto.builder()
                .cartTotal(paymentDue.getAmount())
                .currency(paymentDue.getCurrency())
                .customer(fawaterkCustomerInfo)
                .cartItems(List.of(fawaterkCartItem))
                .redirectionUrls(redirectionUrls)
                .sendEmail(true)
                .build();

        // Create the transaction with Fawaterk.
        FawaterkCreateTransactionResponseDto fawaterkResponse = fawaterkFeignClient.createTransaction(token, request);
        log.info("Fawaterk transaction created: dueId={} intentKey={}",
                dueId, fawaterkResponse.getData().getIntentKey());

        // Save the new local payment attempt and its checkout details.
        Payment payment = Payment.builder()
                .paymentDueId(dueId)
                .customerId(customerDetailsDto.getId())
                .amount(paymentDue.getAmount())
                .currency(paymentDue.getCurrency())
                .status(PaymentStatus.PENDING)
                .paymentGateway(PaymentGateway.FAWATERK)
                .providerReference(fawaterkResponse.getData().getIntentKey())
                .checkoutUrl(fawaterkResponse.getData().getUrl())
                .checkoutExpiresAt(Instant.now().plusSeconds(fawaterkResponse.getData().getExpiresIn()))
                .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                .build();

        paymentRepository.save(payment);
        log.info("Fawaterk payment attempt saved: dueId={} paymentId={} intentKey={}",
                dueId, payment.getId(), payment.getProviderReference());

        // Link the due to this payment attempt.
        paymentDue.setPaymentId(payment.getId());
        paymentDue.setIntentKey(fawaterkResponse.getData().getIntentKey());
        paymentDue.setPaymentId(payment.getId());
        paymentDuesRepository.save(paymentDue);

        // Return the checkout URL and local payment status to the customer.
        CreateTransactionResponseDto response = CreateTransactionResponseDto.builder()
                .paymentId(payment.getId())
                .checkoutUrl(payment.getCheckoutUrl())
                .intentKey(payment.getProviderReference())
                .expiresIn(fawaterkResponse.getData().getExpiresIn())
                .paymentStatus(payment.getStatus())
                .paymentGateway(payment.getPaymentGateway())
                .build();

        log.info("Fawaterk checkout ready: dueId={} paymentId={} intentKey={}",
                dueId, payment.getId(), payment.getProviderReference());
        return response;
    }

    @Transactional
    public ResponseEntity<ReturnObject> getTransactionData(String intentKey) {

        log.info("Fawaterk transaction details requested: intentKey={}", intentKey);
        OAuthResponseDto oauth = fawaterkOAuth();
        String token = "Bearer " + oauth.getAccessToken();
        GetTransactionDataRequestDto request = GetTransactionDataRequestDto.builder()
                .intentKey(intentKey)
                .build();

        try {
            GetTransactionDataResponseDto transactionData = fawaterkFeignClient.getTransactionData(token, request);
            if (!transactionData.getStatus().equals("success") && transactionData.getData() == null) {
                log.warn("Fawaterk transaction details unavailable: intentKey={}", intentKey);
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Failed to fetch the fawaterk success response",
                                false,
                                null
                        ), HttpStatus.BAD_GATEWAY
                );
            }

            log.info("Fawaterk transaction details fetched: intentKey={}", intentKey);
            return new ResponseEntity<>(
                    new ReturnObject(
                            "Transaction details fetched successfully",
                            true,
                            transactionData.getData()
                    ), HttpStatus.OK
            );
        } catch (Exception e) {
            log.error("Fawaterk transaction details request failed: intentKey={}", intentKey, e);
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

        log.info("Fawaterk success redirect received: intentKey={}", intentKey);
        try {
            // Authenticate and request the current transaction details from Fawaterk.
            OAuthResponseDto oauth = fawaterkOAuth();
            String token = "Bearer " + oauth.getAccessToken();
            GetTransactionDataRequestDto request = GetTransactionDataRequestDto.builder()
                    .intentKey(intentKey)
                    .build();

            GetTransactionDataResponseDto transactionData = fawaterkFeignClient.getTransactionData(token, request);
            // Stop when Fawaterk did not return usable transaction details.
            if (!transactionData.getStatus().equals("success") && transactionData.getData() == null) {
                log.warn("Fawaterk success redirect rejected: transaction details unavailable, intentKey={}", intentKey);
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Failed to fetch the fawaterk success response",
                                false,
                                null
                        ), HttpStatus.BAD_GATEWAY
                );
            }

            // Find the due linked to this Fawaterk intent key.
            PaymentDues paymentDue = paymentDuesRepository.findByIntentKey(intentKey);
            if (paymentDue == null) {
                log.warn("Fawaterk success redirect rejected: due not found, intentKey={}", intentKey);
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Invalid intent key",
                                false,
                                null
                        ), HttpStatus.NOT_FOUND
                );
            }

            // Find the local payment attempt linked to this intent key.
            Payment payment = paymentRepository.findByProviderReference(intentKey);
            if (payment == null) {
                log.warn("Fawaterk success redirect rejected: payment not found, intentKey={} dueId={}",
                        intentKey, paymentDue.getId());
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Invalid intent key",
                                false,
                                null
                        ), HttpStatus.NOT_FOUND
                );
            }

            // Continue only when the transaction details request succeeded.
            if (transactionData.getStatus().equals("success")) {

                // Confirm that Fawaterk reports this transaction as paid.
                if (!transactionData.getData().getPaidFlag().equals(1)) {
                    log.info("Fawaterk success redirect still unpaid: intentKey={} dueId={} paymentId={}",
                            intentKey, paymentDue.getId(), payment.getId());
                    return new ResponseEntity<>(
                            new ReturnObject("Payment is not confirmed yet",
                                    false,
                                    null),
                            HttpStatus.BAD_REQUEST
                    );
                }

                // Record the paid status on the payment and due when needed.
                checkPaidPaymentStatus(payment, paymentDue, transactionData);
                log.info("Fawaterk success payment recorded: intentKey={} dueId={} paymentId={}",
                        intentKey, paymentDue.getId(), payment.getId());

                // Build the success details returned to the customer.
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

                log.info("Fawaterk success response ready: intentKey={} dueId={} paymentId={}",
                        intentKey, paymentDue.getId(), payment.getId());
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Success transaction",
                                true,
                                response
                        ), HttpStatus.OK
                );
            }
        } catch (Exception e) {
            log.error("Fawaterk success redirect failed: intentKey={}", intentKey, e);
            return new ResponseEntity<>(
                    new ReturnObject(
                            e.getMessage(),
                            false,
                            null
                    ), HttpStatus.BAD_REQUEST
            );
        }

        log.warn("Fawaterk success redirect rejected: unexpected transaction status, intentKey={}", intentKey);
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

        log.info("Fawaterk fail redirect received: intentKey={}", intentKey);
        try {
            OAuthResponseDto oauth = fawaterkOAuth();
            String token = "Bearer " + oauth.getAccessToken();
            GetTransactionDataRequestDto request = GetTransactionDataRequestDto.builder()
                    .intentKey(intentKey)
                    .build();

            GetTransactionDataResponseDto transactionData = fawaterkFeignClient.getTransactionData(token, request);
            if (!transactionData.getStatus().equals("success") && transactionData.getData() == null) {
                log.warn("Fawaterk fail redirect rejected: transaction details unavailable, intentKey={}", intentKey);
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
                log.warn("Fawaterk fail redirect rejected: due not found, intentKey={}", intentKey);
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
                log.warn("Fawaterk fail redirect rejected: payment not found, intentKey={} dueId={}",
                        intentKey, paymentDues.getId());
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Invalid intent key",
                                false,
                                null
                        ), HttpStatus.NOT_FOUND
                );
            }

            if (!transactionData.getData().getPaidFlag().equals(1)) {

                // Keep the due payable while marking this unsuccessful attempt as failed.
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
                log.info("Fawaterk failed attempt recorded: intentKey={} dueId={} paymentId={}",
                        intentKey, paymentDues.getId(), payment.getId());

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
            log.error("Fawaterk fail redirect failed: intentKey={}", intentKey, e);
            return new ResponseEntity<>(
                    new ReturnObject(
                            e.getMessage(),
                            false,
                            null
                    ), HttpStatus.BAD_REQUEST
            );
        }

        log.warn("Fawaterk fail redirect had no unpaid result: intentKey={}", intentKey);
        return new ResponseEntity<>(
                new ReturnObject(
                        "Bad Request",
                        false,
                        null
                ),HttpStatus.BAD_REQUEST
        );
    }

    // Show a pending result while the customer has not completed payment.
    @Transactional
    public ResponseEntity<ReturnObject> pendingTransaction(String intentKey, String errorMessage) {

        log.info("Fawaterk pending redirect received: intentKey={}", intentKey);
        try {
            OAuthResponseDto oauth = fawaterkOAuth();
            String token = "Bearer " + oauth.getAccessToken();
            GetTransactionDataRequestDto request = GetTransactionDataRequestDto.builder()
                    .intentKey(intentKey)
                    .build();

            GetTransactionDataResponseDto transactionData = fawaterkFeignClient.getTransactionData(token, request);
            if (!transactionData.getStatus().equals("success") && transactionData.getData() == null) {
                log.warn("Fawaterk pending redirect rejected: transaction details unavailable, intentKey={}", intentKey);
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
                log.warn("Fawaterk pending redirect rejected: due not found, intentKey={}", intentKey);
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
                log.warn("Fawaterk pending redirect rejected: payment not found, intentKey={} dueId={}",
                        intentKey, paymentDues.getId());
                return new ResponseEntity<>(
                        new ReturnObject(
                                "Invalid intent key",
                                false,
                                null
                        ), HttpStatus.NOT_FOUND
                );
            }

            if (!transactionData.getData().getPaidFlag().equals(1)) {

                // Keep the due and payment attempt pending until payment is confirmed.
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
                log.info("Fawaterk pending attempt recorded: intentKey={} dueId={} paymentId={}",
                        intentKey, paymentDues.getId(), payment.getId());

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
            log.error("Fawaterk pending redirect failed: intentKey={}", intentKey, e);
            return new ResponseEntity<>(
                    new ReturnObject(
                            e.getMessage(),
                            false,
                            null
                    ), HttpStatus.BAD_REQUEST
            );
        }

        log.warn("Fawaterk pending redirect had no unpaid result: intentKey={}", intentKey);
        return new ResponseEntity<>(
                new ReturnObject(
                        "Bad Request",
                        false,
                        null
                ),HttpStatus.BAD_REQUEST
        );
    }


    @Transactional
    public void checkPaidPaymentStatus(Payment payment,
                                       PaymentDues paymentDue,
                                       GetTransactionDataResponseDto transactionData) {
        // Fill in paid details only when the due or payment is not fully recorded yet.
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
            log.info("Fawaterk due marked paid: dueId={} paymentId={}", paymentDue.getId(), payment.getId());
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
            log.info("Fawaterk payment marked paid: dueId={} paymentId={}", paymentDue.getId(), payment.getId());
        }
    }
}
