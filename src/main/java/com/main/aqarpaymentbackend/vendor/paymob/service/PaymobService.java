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
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymobService {

    private final PaymentDuesRepository paymentDuesRepository;
    private final PaymentRepository paymentRepository;
    private final PaymobFeignClient paymobFeignClient;
    private final CustomerFeignClient customerFeignClient;
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

    public ResponseEntity<ReturnObject> createIntention(String token, Integer dueId) {

        Integer customerId = jwtUtil.extractUserId(token);

        String paymobToken = "Token " + secretKey;

        PaymentDues paymentDue = paymentDuesRepository.findById(dueId).orElse(null);
        if (paymentDue == null) {
            return new ResponseEntity<>(
                    new ReturnObject(
                            "Invalid payment deu id",
                            false,
                            null
                    ), HttpStatus.NOT_FOUND
            );
        }

        if (!Objects.equals(customerId, paymentDue.getCustomerId())) {
            return new ResponseEntity<>(
                    new ReturnObject(
                            "This payment due doesn't belong to this customer",
                            false,
                            null
                    ), HttpStatus.CONFLICT
            );
        }

        if (paymentDue.getStatus().equals(PaymentStatus.PAID) || paymentDue.getPaidFlag().equals(1)) {
            return new ResponseEntity<>(
                    new ReturnObject(
                            "The payment due already paid",
                            false,
                            null
                    ), HttpStatus.CONFLICT
            );

        }

        // Our database stores EGP, Paymob expects piasters (cents).
        BigDecimal amountCents;
        try {
            amountCents = BigDecimal.valueOf(
                    paymentDue.getAmount().movePointRight(2).longValueExact()
            );
        } catch (ArithmeticException e) {
            return new ResponseEntity<>(
                    new ReturnObject("Amount cannot be converted to cents",
                            false,
                            null),
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

        String specialReference = "AQAR-PAYMENT-" + payment.getId();
        payment.setSpecialReference(specialReference);
        paymentRepository.save(payment);



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

            return new ResponseEntity<>(
                    new ReturnObject("Paymob could not create the intention",
                            false,
                            null),
                    HttpStatus.BAD_GATEWAY
            );
        }

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

        CreateIntentionResponseDto response = CreateIntentionResponseDto.builder()
                .paymentId(payment.getId())
                .checkoutUrl(payment.getCheckoutUrl())
                .checkoutExpiresAt(payment.getCheckoutExpiresAt())
                .paymentStatus(payment.getStatus())
                .paymentGateway(payment.getPaymentGateway())
                .build();

        return new ResponseEntity<>(
                new ReturnObject(
                        "Intention created successfully",
                        true,
                        response
                ), HttpStatus.OK
        );
    }
}
