package com.main.aqarpaymentbackend.vendor.paymob.service;

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
import com.main.aqarpaymentbackend.vendor.paymob.client.PaymobFeignClient;
import com.main.aqarpaymentbackend.vendor.paymob.dto.*;
import com.main.aqarpaymentbackend.vendor.paymob.util.PaymobHmacVerifier;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymobService {

    private final PaymentDuesRepository paymentDuesRepository;
    private final PaymentRepository paymentRepository;
    private final PaymobFeignClient paymobFeignClient;
    private final CustomerFeignClient customerFeignClient;
    private final PaymobHmacVerifier paymobHmacVerifier;
    private final JwtUtil jwtUtil;

    @Value("${paymob.card-integration-id}")
    private Integer cardIntegrationId;
    @Value("${paymob.notification-url}")
    private String notificationUrl;
    @Value("${paymob.redirection-url}")
    private String redirectionUrl;
    @Value("${paymob.base-url}")
    private String baseUrl;
    @Value("${paymob.api-key}")
    private String apiKey;
    @Value("${paymob.public-key}")
    private String publicKey;
    @Value("${paymob.secret-key}")
    private String secretKey;

    private final ObjectMapper objectMapper = new ObjectMapper();



    public PaymobAuthTokenResponseDto authToken() {

        PaymobAuthTokenRequestDto request = PaymobAuthTokenRequestDto.builder()
                .apikey(apiKey)
                .build();

        return paymobFeignClient.authToken(request);
    }

    @Transactional
    public ResponseEntity<ReturnObject> createIntention(String token, Integer dueId) {

        Integer customerId = jwtUtil.extractUserId(token);
        log.info("Paymob intention requested: dueId={} customerId={}", dueId, customerId);

        String paymobToken = "Token " + secretKey;

        PaymentDues paymentDue = paymentDuesRepository.findById(dueId).orElse(null);
        if (paymentDue == null) {
            log.warn("Paymob intention rejected: due not found, dueId={}", dueId);
            return new ResponseEntity<>(
                    new ReturnObject("Invalid payment deu id", false, null),
                    HttpStatus.NOT_FOUND
            );
        }

        if (!Objects.equals(customerId, paymentDue.getCustomerId())) {
            log.warn("Paymob intention rejected: ownership mismatch, dueId={} customerId={}", dueId, customerId);
            return new ResponseEntity<>(
                    new ReturnObject("This payment due doesn't belong to this customer", false, null),
                    HttpStatus.CONFLICT
            );
        }

        if (paymentDue.getStatus().equals(PaymentStatus.PAID) || paymentDue.getPaidFlag().equals(1)) {
            log.info("Paymob intention rejected: due already paid, dueId={}", dueId);
            return new ResponseEntity<>(
                    new ReturnObject("The payment due already paid", false, null),
                    HttpStatus.CONFLICT
            );

        }

        // Our database stores EGP, Paymob expects piasters (cents).
        BigDecimal amountCents;
        try {
            amountCents = BigDecimal.valueOf(
                    paymentDue.getAmount().movePointRight(2).longValueExact()
            );
        } catch (ArithmeticException e) {
            log.warn("Paymob intention rejected: amount cannot be converted to cents, dueId={}", dueId);
            return new ResponseEntity<>(
                    new ReturnObject("Amount cannot be converted to cents", false, null),
                    HttpStatus.BAD_REQUEST
            );
        }


        ReturnObject unitDetails = customerFeignClient.getUnitDetails(token, paymentDue.getUnitId()).getBody();
        UnitInfoDto unitInfo = objectMapper.convertValue(unitDetails.getData(), UnitInfoDto.class);

        ReturnObject customerDetails = customerFeignClient.getCustomerDetails(token).getBody();
        CustomerInfoDto customerInfo = objectMapper.convertValue(customerDetails.getData(), CustomerInfoDto.class);
        PaymobBillingDataDto billingData = PaymobBillingDataDto.builder()
                .firstName(customerInfo.getFirstName())
                .lastName(customerInfo.getLastName())
                .email(customerInfo.getEmail())
                .phoneNumber(customerInfo.getPhoneNumber())
                .build();

        Payment payment = Payment.builder()
                .paymentDueId(paymentDue.getId())
                .customerId(customerId)
                .amount(paymentDue.getAmount())
                .currency(paymentDue.getCurrency())
                .status(PaymentStatus.PENDING)
                .paymentGateway(PaymentGateway.PAYMOB)
                .paymentMethodId(cardIntegrationId)
                .paidFlag(0)
                .build();
        payment = paymentRepository.save(payment);

        // Give Paymob a unique reference for this local payment attempt.
        String specialReference = "AQAR-PAYMENT-" + payment.getId();
        payment.setSpecialReference(specialReference);
        paymentRepository.save(payment);
        log.info("Paymob payment attempt created: dueId={} paymentId={}", dueId, payment.getId());



        PaymobCreateIntentionRequestDto paymobRequest = PaymobCreateIntentionRequestDto.builder()
                .amount(amountCents)
                .currency(paymentDue.getCurrency())
                .paymentMethods(List.of(cardIntegrationId))
                .items(List.of(
                        ItemsRequestDto.builder()
                                .name(unitInfo.getName())
                                .amount(amountCents)
                                .build()
                ))
                .billingData(billingData)
                .specialReference(specialReference)
                .expiration(3600)                        // 1h
                .notificationUrl(notificationUrl)        // webhook url
                .redirectionUrl(redirectionUrl)          // redirection url
                .build();


        PaymobCreateIntentionResponseDto paymobResponse;
        try {
             paymobResponse = paymobFeignClient.createIntention(paymobToken, paymobRequest);
        } catch (FeignException e) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.warn("Paymob intention creation failed: dueId={} paymentId={} providerStatus={}",
                    dueId, payment.getId(), e.status());

            return new ResponseEntity<>(
                    new ReturnObject("Paymob could not create the intention", false, null),
                    HttpStatus.BAD_GATEWAY
            );
        }
        log.info("Paymob intention created: dueId={} paymentId={} intentionId={} orderId={}",
                dueId, payment.getId(), paymobResponse.getId(), paymobResponse.getIntentionOrderId());

        // This URL opens checkout.
        String checkoutUrl = "https://eg.checkout.paymob.com/?publicKey=" + publicKey + "&clientSecret=" + paymobResponse.getClientSecret();

        payment.setProviderReference(paymobResponse.getId());
        payment.setProviderOrderId(paymobResponse.getIntentionOrderId());
        payment.setCheckoutUrl(checkoutUrl);
        payment.setCheckoutExpiresAt(Instant.now().plusSeconds(3600));
        paymentRepository.save(payment);

        paymentDue.setPaymentId(payment.getId());
        paymentDue.setIntentKey(payment.getProviderReference());
        paymentDue.setPaidFlag(0);
        paymentDuesRepository.save(paymentDue);
        log.info("Paymob intention linked to due: dueId={} paymentId={} intentionId={}",
                dueId, payment.getId(), payment.getProviderReference());

        CreateIntentionResponseDto response = CreateIntentionResponseDto.builder()
                .paymentId(payment.getId())
                .checkoutUrl(payment.getCheckoutUrl())
                .checkoutExpiresAt(payment.getCheckoutExpiresAt())
                .paymentStatus(payment.getStatus())
                .paymentGateway(payment.getPaymentGateway())
                .build();

        return new ResponseEntity<>(
                new ReturnObject("Intention created successfully", true, response),
                HttpStatus.OK
        );
    }

    @Transactional
    public ResponseEntity<ReturnObject> paymobWebhook(String receivedHmac, PaymobWebhookRequestDto request) {

        // Verify the HMAC before using callback values to update local records.
        boolean isHmacValid = paymobHmacVerifier.isValid(request, receivedHmac);
        if (!isHmacValid) {
            log.warn("Paymob webhook rejected: invalid HMAC");
            return new ResponseEntity<>(
                    new ReturnObject("Invalid HMAC signature", false, null),
                    HttpStatus.CONFLICT
            );
        }
        log.info("Paymob webhook verified: orderId={} transactionId={}",
                request.getObj().getOrder().getId(), request.getObj().getTransactionId());

        Payment payment = paymentRepository.findByProviderOrderId(request.getObj().getOrder().getId());
        if (payment == null) {
            log.warn("Paymob webhook rejected: order not found, orderId={}", request.getObj().getOrder().getId());
            return new ResponseEntity<>(
                    new ReturnObject("Mismatching order id with saved payment order id", false, null),
                    HttpStatus.CONFLICT
            );
        }
        PaymentDues paymentDue = paymentDuesRepository.findById(payment.getPaymentDueId()).orElse(null);
        if (paymentDue == null) {
            log.warn("Paymob webhook rejected: due not found, paymentId={} dueId={}",
                    payment.getId(), payment.getPaymentDueId());
            return new ResponseEntity<>(
                    new ReturnObject("Invalid payment due id", false, null),
                    HttpStatus.CONFLICT
            );
        }

        if (!Objects.equals(payment.getCustomerId(), paymentDue.getCustomerId())
                || payment.getAmount() == null
                || paymentDue.getAmount() == null
                || payment.getAmount().compareTo(paymentDue.getAmount()) != 0
                || !Objects.equals(payment.getCurrency(), paymentDue.getCurrency())) {
            log.warn("Paymob webhook rejected: payment and due mismatch, paymentId={} dueId={}",
                    payment.getId(), paymentDue.getId());
            return new ResponseEntity<>(
                    new ReturnObject("Payment and Payment due do not match", false, null),
                    HttpStatus.CONFLICT
            );
        }

        if (
                !payment.getPaymentGateway().equals(PaymentGateway.PAYMOB)
                // I use the ".movePointRight(2)" because the saved amount is not the same as amount sent to paymob into "createIntention()"
                || payment.getAmount().movePointRight(2).compareTo(request.getObj().getPaymentAmount()) != 0
                || !payment.getCurrency().equals(request.getObj().getPaymentCurrency())
                || !payment.getPaymentMethodId().equals(request.getObj().getIntegrationId())
        ) {
            log.warn("Paymob webhook rejected: provider data mismatch, paymentId={} dueId={} transactionId={}",
                    payment.getId(), paymentDue.getId(), request.getObj().getTransactionId());
            return new ResponseEntity<>(
                    new ReturnObject("Mismatching data between saved payment and webhook data", false, null), HttpStatus.CONFLICT
            );
        }

        // Handle the payment status
        if (Boolean.TRUE.equals(request.getObj().getPending())) {
            // Keep pending. Never downgrade PAID.
            log.info("Paymob webhook left payment pending: paymentId={} dueId={} transactionId={}",
                    payment.getId(), paymentDue.getId(), request.getObj().getTransactionId());
        } else if (Boolean.TRUE.equals(request.getObj().getSuccess()) &&
                Boolean.FALSE.equals(request.getObj().getPending())) {

            // If payment already PAID before
            if (PaymentStatus.PAID.equals(payment.getStatus())) {
                if (Objects.equals(
                        payment.getProviderTransactionId(),
                        request.getObj().getTransactionId())) {
                    // Paymob sent the same success callback again.
                    log.info("Paymob webhook repeated paid transaction: paymentId={} dueId={} transactionId={}",
                            payment.getId(), paymentDue.getId(), request.getObj().getTransactionId());
                    return new ResponseEntity<>(
                            new ReturnObject("Payment already processed", true, null),
                            HttpStatus.OK
                    );
                }

                // Already paid, but this is a different Paymob transaction.
                log.error("Possible second charge for payment {}", payment.getId());
                return new ResponseEntity<>(
                        new ReturnObject("Different transaction for an already paid payment", false, null),
                        HttpStatus.CONFLICT
                );
            }

            // If payment due PAID before
            if (PaymentStatus.PAID.equals(paymentDue.getStatus())
                    || Integer.valueOf(1).equals(paymentDue.getPaidFlag())) {

                // This is a successful charge, so record it on this Payment.
                payment.setStatus(PaymentStatus.PAID);
                payment.setPaidFlag(1);
                payment.setProviderTransactionId(request.getObj().getTransactionId());
                payment.setTransactionCreatedAt(request.getObj().getCreatedAt());
                if (payment.getPaidAt() == null) {
                    payment.setPaidAt(Timestamp.valueOf(LocalDateTime.now()));
                }
                paymentRepository.save(payment);

                // Leave PaymentDues unchanged: another attempt already paid it.
                log.error("Possible second charge: due {}, payment {}, Paymob transaction {}",
                        paymentDue.getId(), payment.getId(),
                        request.getObj().getTransactionId());

                return new ResponseEntity<>(
                        new ReturnObject("Payment recorded; due was already paid", true, null),
                        HttpStatus.OK
                );
            }

            // Mark payment and payment due PAID.
            if (!Integer.valueOf(1).equals(payment.getPaidFlag()) && !payment.getStatus().equals(PaymentStatus.PAID)) {
                payment.setStatus(PaymentStatus.PAID);
                payment.setPaidFlag(1);
                payment.setProviderTransactionId(request.getObj().getTransactionId());
                payment.setTransactionCreatedAt(request.getObj().getCreatedAt());
                if (payment.getPaidAt() == null) {
                    payment.setPaidAt(Timestamp.valueOf(LocalDateTime.now()));
                }
                paymentRepository.save(payment);
                log.info("Paymob payment marked paid: paymentId={} dueId={} transactionId={}",
                        payment.getId(), paymentDue.getId(), request.getObj().getTransactionId());
            }

            if (!paymentDue.getStatus().equals(PaymentStatus.PAID) && !Integer.valueOf(1).equals(paymentDue.getPaidFlag())) {
                paymentDue.setStatus(PaymentStatus.PAID);
                paymentDue.setPaidFlag(1);
                if (paymentDue.getPaidAt() == null) {
                    paymentDue.setPaidAt(Timestamp.valueOf(LocalDateTime.now()));
                }
                paymentDuesRepository.save(paymentDue);
                log.info("Paymob due marked paid: dueId={} paymentId={}", paymentDue.getId(), payment.getId());
            }
        } else if (Boolean.FALSE.equals(request.getObj().getSuccess()) && Boolean.FALSE.equals(request.getObj().getPending())) {
            // Mark only this payment attempt FAILED.
            if (!Integer.valueOf(1).equals(payment.getPaidFlag()) && !payment.getStatus().equals(PaymentStatus.PAID)) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setPaidFlag(0);
                payment.setProviderTransactionId(request.getObj().getTransactionId());
                paymentRepository.save(payment);
                log.info("Paymob payment attempt marked failed: paymentId={} dueId={} transactionId={}",
                        payment.getId(), paymentDue.getId(), request.getObj().getTransactionId());
            }
        } else {
            // Missing/unknown result: reject without changing the database.
            log.warn("Paymob webhook rejected: unknown result, paymentId={} dueId={} transactionId={}",
                    payment.getId(), paymentDue.getId(), request.getObj().getTransactionId());
            return new ResponseEntity<>(
                    new ReturnObject("Unknown payment status from paymob", false, null),
                    HttpStatus.BAD_REQUEST
            );
        }

        return new ResponseEntity<>(
                new ReturnObject("Paymob callback processed", true, null),
                HttpStatus.OK
        );
    }

    public ResponseEntity<ReturnObject> paymobReturn(Map<String, String> params) {

        log.info("Paymob return received");
        String receivedHmac = params.get("hmac");

        String amountCents = params.get("amount_cents");
        String currency = params.get("currency");
        String integrationId = params.get("integration_id");
        // Paymob's redirect example uses "order"; its HMAC guide says "order_id".
        String orderId = params.get("order_id");
        if (orderId == null) {
            orderId = params.get("order");
        }

        if (!paymobHmacVerifier.isValid(params, receivedHmac)) {
            log.warn("Paymob return rejected: invalid HMAC");
            return new ResponseEntity<>(
                    new ReturnObject("Invalid Paymob HMAC", false, null),
                    HttpStatus.BAD_REQUEST
            );
        }

        Long parsedOrderId;
        BigDecimal parsedAmountCents;
        Integer parsedIntegrationId;

        try {
            parsedOrderId = Long.parseLong(orderId);
            parsedAmountCents = new BigDecimal(amountCents);
            parsedAmountCents.toBigIntegerExact(); // Paymob amount must be whole cents
            parsedIntegrationId = Integer.parseInt(integrationId);
        } catch (NumberFormatException | ArithmeticException e) {
            log.warn("Paymob return rejected: invalid callback values");
            return new ResponseEntity<>(
                    new ReturnObject("Invalid Paymob callback values", false, null),
                    HttpStatus.BAD_REQUEST
            );
        }


        Payment payment = paymentRepository.findByProviderOrderId(parsedOrderId);
        if (payment == null) {
            log.warn("Paymob return rejected: order not found, orderId={}", parsedOrderId);
            return new ResponseEntity<>(
                    new ReturnObject("Invalid order id", false, null),
                    HttpStatus.NOT_FOUND
            );
        }

        if (payment.getPaymentGateway() != PaymentGateway.PAYMOB
                || payment.getAmount() == null
                || payment.getAmount().movePointRight(2).compareTo(parsedAmountCents) != 0
                || !Objects.equals(payment.getCurrency(), currency)
                || !Objects.equals(payment.getPaymentMethodId(), parsedIntegrationId)) {
            log.warn("Paymob return rejected: payment data mismatch, orderId={} paymentId={}",
                    parsedOrderId, payment.getId());
            return new ResponseEntity<>(
                    new ReturnObject("Paymob callback does not match the payment", false, null),
                    HttpStatus.CONFLICT
            );
        }

        PaymentDues paymentDue = paymentDuesRepository.findById(payment.getPaymentDueId()).orElse(null);
        if (paymentDue == null) {
            log.warn("Paymob return rejected: due not found, paymentId={} dueId={}",
                    payment.getId(), payment.getPaymentDueId());
            return new ResponseEntity<>(
                    new ReturnObject("Invalid payment id", false, null),
                    HttpStatus.NOT_FOUND
            );
        }


        // Show the local result; the webhook is responsible for changing payment status.
        PaymentResultDto response = PaymentResultDto.builder()
                .paymentId(payment.getId())
                .paymentDueId(paymentDue.getId())
                .paymentStatus(payment.getStatus())
                .paymentDueStatus(paymentDue.getStatus())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paidAt(payment.getPaidAt())
                .build();

        log.info("Paymob return result ready: paymentId={} dueId={} paymentStatus={}",
                payment.getId(), paymentDue.getId(), payment.getStatus());
        return new ResponseEntity<>(
                new ReturnObject("Payment result fetched successfully", true, response),
                HttpStatus.OK
        );
    }
}
