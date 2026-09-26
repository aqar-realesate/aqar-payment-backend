package com.main.aqarpaymentbackend.vendor.fawaterk.service;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class FawaterkService {

    private final FawaterkFeignClient fawaterkFeignClient;
    private final CustomerFeignClient customerFeignClient;
    private final PaymentDuesRepository paymentDuesRepository;
    private final PaymentRepository paymentRepository;
    private final JwtUtil jwtUtil;


    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${fawaterk.client-secret}")
    private String clientSecret;
    @Value("${fawaterk.client-id}")
    private String clientId;


    public FawaterkOAuthResponseDto fawaterkOAuth() {

        FawaterkOAuthRequestDto requestDto = FawaterkOAuthRequestDto.builder()
                .grantType("client_credentials")
                .clientSecret(clientSecret)
                .clientId(clientId)
                .build();
        log.info("Calling the external api");
        FawaterkOAuthResponseDto responseDto = fawaterkFeignClient.fawaterkOAuth(requestDto);
        log.info("the response from external api was: {}", requestDto);
        return responseDto;
    }

    public CreateTransactionResponseDto fawaterkCreateTransaction(String token, Integer paymentDueId) {

        Integer customerId = jwtUtil.extractUserId(token);

        PaymentDues due = paymentDuesRepository.findById(paymentDueId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment due not found"
                ));

        if (!due.getCustomerId().equals(customerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "This payment due does not belong to you"
            );
        }
        // Get customer info
        ReturnObject customerResult = customerFeignClient.getCustomerDetails(token).getBody();
        CustomerInfoDto adminCustomer = objectMapper.convertValue(customerResult.getData(), CustomerInfoDto.class);

        CustomerDetailsDto fawaterkCustomer = new CustomerDetailsDto();
        fawaterkCustomer.setFirstName(adminCustomer.getFirstName());
        fawaterkCustomer.setLastName(adminCustomer.getLastName());
        fawaterkCustomer.setEmail(adminCustomer.getEmail());
        fawaterkCustomer.setPhone(adminCustomer.getPhone());

        // Get cart info
        ReturnObject unitResult = customerFeignClient.getUnitDetails(token, due.getUnitId()).getBody();
        UnitDetailsDto unitDetails = objectMapper.convertValue(unitResult.getData(), UnitDetailsDto.class);

        CartItemDto cartItem = CartItemDto.builder()
                .name(unitDetails.getUnitTitle())
                .price(due.getAmount().toPlainString())
                .quantity(1)
                .build();


        // Build the checkout request
        CreateTransactionRequestDto request = CreateTransactionRequestDto.builder()
                .cartTotal(due.getAmount())
                .currency(due.getCurrency())
                .customer(fawaterkCustomer)
                .cartItems(List.of(cartItem))
                .build();

        // Stop if the due has already been paid
        if (paymentRepository.existsByPaymentDueIdAndStatus(
                paymentDueId, PaymentStatus.PAID)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This payment due has already been paid"
            );
        }

        // Look for the newest pending Fawaterk checkout
        Optional<Payment> pendingPayment =
                paymentRepository
                        .findFirstByPaymentDueIdAndStatusAndPaymentGatewayOrderByIdDesc(
                                paymentDueId,
                                PaymentStatus.PENDING,
                                PaymentGateway.FAWATERK
                        );

        Instant now = Instant.now();

        if (pendingPayment.isPresent()) {
            Payment existing = pendingPayment.get();

            // Reuse only a checkout whose expiry is still in the future
            if (existing.getCheckoutExpiresAt() != null
                    && existing.getCheckoutExpiresAt().isAfter(now)) {

                // Return remaining seconds
                long remainingSeconds = Duration.between(
                        now, existing.getCheckoutExpiresAt()
                ).getSeconds();

                return CreateTransactionResponseDto.builder()
                        .status("success")
                        .data(CreateTransactionResponseDataDto.builder()
                                .intentKey(existing.getProviderReference())
                                .url(existing.getCheckoutUrl())
                                .expiresIn(remainingSeconds)
                                .build())
                        .build();
            }
        }

        // Obtain the oauth token for the checkout request
        FawaterkOAuthResponseDto oauth = fawaterkOAuth();

        CreateTransactionResponseDto response =
                fawaterkFeignClient.fawaterkCreateTransaction(
                        "Bearer " + oauth.getAccessToken(),
                        request
                );

        if (response == null || !"success".equals(response.getStatus())
                || response.getData() == null) {
            throw new IllegalStateException("Fawaterk did not create a checkout");
        }

        Payment payment = Payment.builder()
                .paymentDueId(paymentDueId)
                .customerId(customerId)
                .unitRequestId(due.getRequestId())
                .amount(due.getAmount())
                .currency(due.getCurrency())
                .status(PaymentStatus.PENDING)
                .paymentGateway(PaymentGateway.FAWATERK)
                .providerReference(response.getData().getIntentKey())
                .checkoutUrl(response.getData().getUrl())
                .checkoutExpiresAt(Instant.now().plusSeconds(response.getData().getExpiresIn()))
                .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                .build();

        paymentRepository.save(payment);

        return response;
    }

    public GetTransactionDataResponseDto getTransactionData(String token, GetTransactionDataRequestDto request) {


        GetTransactionDataResponseDto response = fawaterkFeignClient.getTransactionData(token, request);
        return response;
    }
}
