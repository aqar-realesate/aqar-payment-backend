//package com.main.aqarpaymentbackend.vendor.paymob.service;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.main.aqarpaymentbackend.client.CustomerFeignClient;
//import com.main.aqarpaymentbackend.model.Payment;
//import com.main.aqarpaymentbackend.model.PaymentDues;
//import com.main.aqarpaymentbackend.model.enums.PaymentStatus;
//import com.main.aqarpaymentbackend.repository.PaymentDuesRepository;
//import com.main.aqarpaymentbackend.repository.PaymentRepository;
//import com.main.aqarpaymentbackend.util.JwtUtil;
//import com.main.aqarpaymentbackend.util.ReturnObject;
//import com.main.aqarpaymentbackend.vendor.paymob.dto.PaymobBillingDataDto;
//import com.main.aqarpaymentbackend.vendor.paymob.dto.PaymobCreateIntentionRequestDto;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.HttpStatus;
//import org.springframework.stereotype.Service;
//import org.springframework.web.server.ResponseStatusException;
//
//import java.math.BigDecimal;
//import java.util.Optional;
//import java.util.UUID;
//
//import static java.lang.Boolean.TRUE;
//import static org.bouncycastle.util.BigIntegers.longValueExact;
//
//@Service
//@Slf4j
//@RequiredArgsConstructor
//public class PaymobService {
//
//    private final PaymentDuesRepository paymentDuesRepository;
//    private final PaymentRepository paymentRepository;
//    private final JwtUtil jwtUtil;
//    private final CustomerFeignClient customerFeignClient;
//
//    @Value("${paymob.card-integration-id}")
//    private Integer cardIntegrationId;
//    @Value("${paymob.notification-url}")
//    private String notificationUrl;
//    @Value("${paymob.redirection-url}")
//    private String redirectionUrl;
//
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//
////    PaymobCreateIntentionRequestDto prepareIntentionRequest(String token, Integer paymentDueId) {
////
////        PaymobBillingDataDto customerInfo = prepareBillingData(token, paymentDueId);
////        PaymentDues paymentDue = paymentDuesRepository.findById(paymentDueId)
////                .orElseThrow(() -> new ResponseStatusException(
////                        HttpStatus.NOT_FOUND, "Payment due not found"
////                ));
////
////        PaymobCreateIntentionRequestDto intentionRequest = PaymobCreateIntentionRequestDto.builder()
////                .amount()
////                .currency(paymentDue.getCurrency())
////                .paymentMethods()
////                .billingData(customerInfo)
////                .specialReference(UUID.randomUUID().toString())
////                .notificationUrl(notificationUrl)
////                .redirectionUrl(redirectionUrl)
////                .build();
////
////    }
//
//
//
//    private PaymobBillingDataDto prepareBillingData(String token, Integer paymentDueId) {
//
//        Integer customerId = jwtUtil.extractUserId(token);
//
//        PaymentDues paymentDue = paymentDuesRepository.findById(paymentDueId)
//                .orElseThrow(() -> new ResponseStatusException(
//                        HttpStatus.NOT_FOUND, "Payment due not found"
//                ));
//
//        if (!customerId.equals(paymentDue.getCustomerId())) {
//            throw new ResponseStatusException(
//                    HttpStatus.FORBIDDEN, "This payment due doesn't belong to this customer"
//            );
//        }
//
//        if (paymentRepository.existsByPaymentDueIdAndStatus(paymentDueId, PaymentStatus.PAID)) {
//            throw new ResponseStatusException(
//                    HttpStatus.CONFLICT, "This payment already paid before"
//            );
//        }
//
//        ReturnObject customerDetails = customerFeignClient.getCustomerDetails(token).getBody();
//        if (
//                customerDetails == null ||
//                !TRUE.equals(customerDetails.getStatus()) ||
//                customerDetails.getData() == null
//        ) {
//            throw new ResponseStatusException(
//                    HttpStatus.BAD_GATEWAY, "Could not fetch customer details"
//            );
//        }
//        CustomerInfoDto customerInfoDto = objectMapper.convertValue(customerDetails.getData(), CustomerInfoDto.class);
//
//        PaymobBillingDataDto billingData = PaymobBillingDataDto.builder()
//                .firstName(customerInfoDto.getFirstName())
//                .lastName(customerInfoDto.getLastName())
//                .email(customerInfoDto.getEmail())
//                .phoneNumber(customerInfoDto.getPhone())
//                .build();
//
//        return billingData;
//    }
//}
