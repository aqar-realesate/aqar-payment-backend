package com.main.aqarpaymentbackend.vendor.stripe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.aqarpaymentbackend.model.Payment;
import com.main.aqarpaymentbackend.model.PaymentDues;
import com.main.aqarpaymentbackend.model.enums.PaymentGateway;
import com.main.aqarpaymentbackend.model.enums.PaymentStatus;
import com.main.aqarpaymentbackend.repository.PaymentDuesRepository;
import com.main.aqarpaymentbackend.repository.PaymentRepository;
import com.main.aqarpaymentbackend.util.JwtUtil;
import com.main.aqarpaymentbackend.util.ReturnObject;
import com.main.aqarpaymentbackend.vendor.stripe.client.StripeFeignClient;
import com.main.aqarpaymentbackend.vendor.stripe.dto.*;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class StripeService {

    private final StripeFeignClient stripeFeignClient;
    private final PaymentRepository paymentRepository;
    private final PaymentDuesRepository paymentDuesRepository;
    private final JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${stripe.success-url}")
    private String successUrl;
    @Value("${stripe.cancle-url}")
    private String cancelUrl;
    @Value("${stripe.webhook.signing-key}")
    private String stripeSigningKey;


    @Transactional
    public ResponseEntity<ReturnObject> createCheckout(String token, Integer dueId) {

        Integer customerId = jwtUtil.extractUserId(token);
        log.info("Stripe checkout requested: dueId={} customerId={}", dueId, customerId);

        // Lock and load the due so two requests cannot create sessions simultaneously.
        PaymentDues paymentDue = paymentDuesRepository.findById(dueId).orElse(null);
        if (paymentDue == null) {
            log.warn("Stripe checkout rejected: due not found, dueId={}", dueId);
            return new ResponseEntity<>(
                    new ReturnObject("Invalid payment due id", false, null),
                    HttpStatus.NOT_FOUND
            );
        }

        // Check that this due belongs to the customer.
        if (!customerId.equals(paymentDue.getCustomerId())) {
            log.warn("Stripe checkout rejected: ownership mismatch, dueId={} customerId={}",
                    dueId, customerId);
            return new ResponseEntity<>(
                    new ReturnObject("This payment due doesn't belong to this customer", false, null),
                    HttpStatus.CONFLICT
            );
        }

        // Stop if the due has already been paid.
        if (paymentDue.getStatus() == PaymentStatus.PAID
                || Objects.equals(paymentDue.getPaidFlag(), 1)) {
            log.info("Stripe checkout skipped: due already paid, dueId={}", dueId);
            return new ResponseEntity<>(
                    new ReturnObject("This payment due already paid", false, null),
                    HttpStatus.CONFLICT
            );
        }

        // Reuse an existing active Stripe checkout for this due.
        if (paymentDue.getPaymentId() != null) {
            Payment existing = paymentRepository.findById(paymentDue.getPaymentId())
                    .orElse(null);

            if (existing != null
                    && existing.getPaymentGateway() == PaymentGateway.STRIPE
                    && existing.getStatus() == PaymentStatus.PENDING
                    && existing.getCheckoutUrl() != null
                    && existing.getCheckoutExpiresAt() != null
                    && existing.getCheckoutExpiresAt().isAfter(Instant.now())) {

                CreateSessionResponseDto response = CreateSessionResponseDto.builder()
                        .stripePaymentId(existing.getProviderReference())
                        .checkoutUrl(existing.getCheckoutUrl())
                        .checkoutUrlExpiresAt(Timestamp.from(existing.getCheckoutExpiresAt()))
                        .paymentStatus(existing.getStatus())
                        .build();

                log.info("Stripe checkout reused: dueId={} paymentId={} sessionId={}",
                        dueId, existing.getId(), existing.getProviderReference());
                return new ResponseEntity<>(
                        new ReturnObject("Existing checkout session", true, response),
                        HttpStatus.OK
                );
            }
        }

        // Create the local payment attempt before asking Stripe for a Session.
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaidFlag(0);
        payment.setPaymentGateway(PaymentGateway.STRIPE);
        payment.setCustomerId(paymentDue.getCustomerId());
        payment.setPaymentDueId(paymentDue.getId());
        payment.setAmount(paymentDue.getAmount());
        payment.setCurrency(paymentDue.getCurrency());
        payment.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        paymentRepository.save(payment);
        log.debug("Stripe payment attempt created: dueId={} paymentId={}", dueId, payment.getId());

        // Build the Checkout Session request using the due and local payment ID.
        Map<String, String> form = new LinkedHashMap<>();
        form.put("mode", "payment");
        form.put("line_items[0][price_data][currency]", paymentDue.getCurrency());
        form.put("success_url", successUrl + "?session_id={CHECKOUT_SESSION_ID}");
        form.put("cancel_url", cancelUrl);
        form.put("client_reference_id", payment.getId().toString());
        form.put("line_items[0][price_data][unit_amount]", paymentDue.getAmount().movePointRight(2).toBigIntegerExact().toString());
        form.put("line_items[0][price_data][product_data][name]", "Payment due " + dueId);
        form.put("line_items[0][quantity]", "1");

        // Ask Stripe to create the Checkout Session.
        StripeSessionResponseDto stripeSessionResponse = stripeFeignClient.createSession(form);
        log.info("Stripe Session received: dueId={} paymentId={} sessionId={}",
                dueId, payment.getId(), stripeSessionResponse.getId());

        // Save Stripe's Session ID and Checkout URL on the payment attempt.
        payment.setProviderReference(stripeSessionResponse.getId());
        payment.setCheckoutUrl(stripeSessionResponse.getUrl());
        payment.setCheckoutExpiresAt(stripeSessionResponse.getExpiresAt().toInstant());
        paymentRepository.save(payment);

        // Link the due to the new local payment attempt.
        paymentDue.setPaymentId(payment.getId());
        paymentDue.setIntentKey(stripeSessionResponse.getId());
        paymentDue.setPaidFlag(0);
        paymentDue.setStatus(PaymentStatus.PENDING);
        paymentDuesRepository.save(paymentDue);
        log.info("Stripe checkout linked to due: dueId={} paymentId={} sessionId={}",
                dueId, payment.getId(), stripeSessionResponse.getId());

        // Return the Checkout URL to the customer.
        CreateSessionResponseDto response = CreateSessionResponseDto.builder()
                .stripePaymentId(stripeSessionResponse.getId())
                .checkoutUrl(stripeSessionResponse.getUrl())
                .checkoutUrlExpiresAt(stripeSessionResponse.getExpiresAt())
                .stripePaymentStatus(stripeSessionResponse.getPaymentStatus())
                .stripeStatus(stripeSessionResponse.getStatus())
                .cancelUrl(stripeSessionResponse.getCancelUrl())
                .successUrl(stripeSessionResponse.getSuccessUrl())
                .paymentStatus(payment.getStatus())
                .build();

        return new ResponseEntity<>(
                new ReturnObject("Checkout session created successfully", true, response),
                HttpStatus.OK
        );
    }

    public ResponseEntity<ReturnObject> getSessionById(String id) {

        try {
            StripeSessionResponseDto stripeSessionResponse = stripeFeignClient.getSessionById(id);

            CreateSessionResponseDto response = CreateSessionResponseDto.builder()
                    .stripePaymentId(stripeSessionResponse.getId())
                    .checkoutUrl(stripeSessionResponse.getUrl())
                    .checkoutUrlExpiresAt(stripeSessionResponse.getExpiresAt())
                    .stripePaymentStatus(stripeSessionResponse.getPaymentStatus())
                    .stripeStatus(stripeSessionResponse.getStatus())
                    .cancelUrl(stripeSessionResponse.getCancelUrl())
                    .successUrl(stripeSessionResponse.getSuccessUrl())
                    .build();

            return new ResponseEntity<>(
                    new ReturnObject("Session fetched successfully", true, response),
                    HttpStatus.OK
            );
        } catch (FeignException e) {
            return new ResponseEntity<>(
                    new ReturnObject(e.getMessage(), false, null),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    @Transactional
    public ResponseEntity<ReturnObject> stripeWebhook(String rawBody, String signature) {

        // Reject a request whose raw body does not match Stripe's signature.
        if (!isValidStripeSignature(rawBody, signature)) {
            log.warn("Stripe webhook rejected: invalid signature");
            return new ResponseEntity<>(
                    new ReturnObject("Invalid Stripe signature", false, null),
                    HttpStatus.UNAUTHORIZED
            );
        }

        try {
            // Parse the body only after verifying its signature.
            StripeWebhookRequestDto request = objectMapper.readValue(rawBody, StripeWebhookRequestDto.class);
            log.info("Stripe webhook received: eventId={} type={}", request.getId(), request.getType());

            // Read the event type before interpreting data.object.
            String type = request.getType();
            if (type == null) {
                log.warn("Stripe webhook rejected: missing event type, eventId={}", request.getId());
                return new ResponseEntity<>(
                        new ReturnObject("Missing Stripe event type", false, null),
                        HttpStatus.BAD_REQUEST
                );
            }

            // A failed card attempt is a PaymentIntent event, so leave this due unchanged.
            if ("payment_intent.payment_failed".equals(type)) {
                log.info("Stripe card attempt failed: eventId={}", request.getId());
                return new ResponseEntity<>(
                        new ReturnObject("Event received", true, null),
                        HttpStatus.OK
                );
            }

            // Acknowledge event types that this Checkout integration does not handle.
            Set<String> sessionEvents = Set.of(
                    "checkout.session.completed",
                    "checkout.session.async_payment_succeeded",
                    "checkout.session.async_payment_failed",
                    "checkout.session.expired"
            );

            if (!sessionEvents.contains(type)) {
                log.info("Stripe webhook ignored: eventId={} type={}", request.getId(), type);
                return new ResponseEntity<>(
                        new ReturnObject("Event ignored", true, null),
                        HttpStatus.OK
                );
            }

            // Check that the event contains a Checkout Session.
            if (request.getData() == null || request.getData().getObject() == null) {
                log.warn("Stripe webhook rejected: missing Checkout Session, eventId={} type={}",
                        request.getId(), type);
                return new ResponseEntity<>(
                        new ReturnObject("Missing Checkout Session", false, null),
                        HttpStatus.BAD_REQUEST
                );
            }

            StripeWebhookObjectDto session = request.getData().getObject();

            // Check that the object is a Session from a one-time payment flow.
            if (!"checkout.session".equals(session.getObject())
                    || !"payment".equals(session.getMode())
                    || session.getId() == null) {
                log.warn("Stripe webhook rejected: invalid Checkout Session, eventId={} type={} sessionId={}",
                        request.getId(), type, session.getId());
                return new ResponseEntity<>(
                        new ReturnObject("Invalid Checkout Session", false, null),
                        HttpStatus.BAD_REQUEST
                );
            }

            Payment payment = paymentRepository.findByProviderReference(session.getId());
            if (payment == null || payment.getPaymentDueId() == null) {
                log.warn("Stripe webhook rejected: payment not found, eventId={} sessionId={}",
                        request.getId(), session.getId());
                return new ResponseEntity<>(
                        new ReturnObject("Invalid provider reference id", false, null),
                        HttpStatus.NOT_FOUND
                );
            }

            PaymentDues paymentDue = paymentDuesRepository.findById(payment.getPaymentDueId()).orElse(null);
            if (paymentDue == null) {
                log.warn("Stripe webhook rejected: due not found, eventId={} paymentId={} dueId={}",
                        request.getId(), payment.getId(), payment.getPaymentDueId());
                return new ResponseEntity<>(
                        new ReturnObject("Invalid intent key", false, null),
                        HttpStatus.NOT_FOUND
                );
            }

            // Compare Stripe's Session with the local payment and due.
            if (payment.getPaymentGateway() != PaymentGateway.STRIPE
                    || !payment.getId().toString().equals(session.getClientReferenceId())
                    || !paymentDue.getCurrency().equalsIgnoreCase(session.getCurrency())
                    || !Objects.equals(
                    session.getAmountTotal(),
                    paymentDue.getAmount().movePointRight(2).longValueExact())) {

                log.warn("Stripe webhook rejected: Stripe data mismatch, eventId={} paymentId={} dueId={} sessionId={}",
                        request.getId(), payment.getId(), paymentDue.getId(), session.getId());
                return new ResponseEntity<>(
                        new ReturnObject("The data of payment from stripe isn't matching our payment", false, null),
                        HttpStatus.CONFLICT
                );
            }

            // Compare the saved payment attempt with its due.
            if (payment.getAmount() == null
                    || payment.getAmount().compareTo(paymentDue.getAmount()) != 0
                    || payment.getCurrency() == null
                    || !payment.getCurrency().equalsIgnoreCase(paymentDue.getCurrency())
                    || !payment.getPaymentDueId().equals(paymentDue.getId())
                    || !payment.getCustomerId().equals(paymentDue.getCustomerId())) {

                log.warn("Stripe webhook rejected: local payment mismatch, eventId={} paymentId={} dueId={}",
                        request.getId(), payment.getId(), paymentDue.getId());
                return new ResponseEntity<>(
                        new ReturnObject("The payment attempt isn't matching the payment due", false, null),
                        HttpStatus.CONFLICT
                );
            }

            boolean successfulEvent = ("checkout.session.completed".equals(type)
                    || "checkout.session.async_payment_succeeded".equals(type))
                    && "paid".equals(session.getPaymentStatus());

            // Process a successful payment.
            if (successfulEvent) {
                // Mark both the payment attempt and its due as paid.
                markSuccessPayment(payment, paymentDue);
                log.info("Stripe webhook payment recorded: eventId={} paymentId={} dueId={} sessionId={}",
                        request.getId(), payment.getId(), paymentDue.getId(), session.getId());
                return new ResponseEntity<>(
                        new ReturnObject("Payment recorded", true, webhookResponse(payment, paymentDue)),
                        HttpStatus.OK
                );
            }

            // Leave a completed but unpaid Session pending.
            if ("checkout.session.completed".equals(type)) {
                log.info("Stripe webhook payment pending: eventId={} paymentId={} dueId={} sessionId={}",
                        request.getId(), payment.getId(), paymentDue.getId(), session.getId());
                return new ResponseEntity<>(
                        new ReturnObject("Payment pending", true, null),
                        HttpStatus.OK
                );
            }

            // Close the local attempt when delayed payment fails or Checkout expires.
            if ("checkout.session.async_payment_failed".equals(type) || "checkout.session.expired".equals(type)) {
                if (payment.getStatus() == PaymentStatus.PENDING) {
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setPaidFlag(0);
                    payment.setCheckoutUrl(null);
                    payment.setCheckoutExpiresAt(null);
                    paymentRepository.save(payment);
                    log.info("Stripe webhook payment attempt closed: eventId={} type={} paymentId={} dueId={}",
                            request.getId(), type, payment.getId(), paymentDue.getId());
                }
            }

            // Acknowledge the verified event so Stripe does not retry it.
            log.debug("Stripe webhook acknowledged: eventId={} type={}", request.getId(), type);
            return new ResponseEntity<>(
                    new ReturnObject("Event received", true, null),
                    HttpStatus.OK
            );

        } catch (JsonProcessingException e) {
            // Reject JSON that cannot be read as your webhook request DTO.
            log.warn("Stripe webhook rejected: invalid JSON payload");
            return new ResponseEntity<>(
                    new ReturnObject("Can't read the json request body", false, null),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    @Transactional
    public ResponseEntity<ReturnObject> getSuccessResult(String sessionId) {

        try {

            StripeSessionResponseDto stripeSessionResponse = stripeFeignClient.getSessionById(sessionId);
            if (!"paid".equals(stripeSessionResponse.getPaymentStatus())) {

                return new ResponseEntity<>(
                        new ReturnObject("The payment didn't complete", false, stripeSessionResponse),
                        HttpStatus.BAD_REQUEST
                );
            }

            Payment payment = paymentRepository.findByProviderReference(sessionId);
            if (payment == null || payment.getPaymentDueId() == null) {
                return new ResponseEntity<>(
                        new ReturnObject("Invalid provider reference id", false, null),
                        HttpStatus.NOT_FOUND
                );
            }

            PaymentDues paymentDue = paymentDuesRepository.findById(payment.getPaymentDueId()).orElse(null);
            if (paymentDue == null) {
                return new ResponseEntity<>(
                        new ReturnObject("Invalid payment due id", false, null),
                        HttpStatus.NOT_FOUND
                );
            }

            boolean recorded = payment.getStatus() == PaymentStatus.PAID
                    && Objects.equals(payment.getPaidFlag(), 1)
                    && paymentDue.getStatus() == PaymentStatus.PAID
                    && Objects.equals(paymentDue.getPaidFlag(), 1);


            RedirectUrlResponseDto response = RedirectUrlResponseDto.builder()
                    .paymentId(payment.getId())
                    .paymentDueId(paymentDue.getId())
                    .checkoutSessionId(sessionId)
                    .stripePaymentStatus(stripeSessionResponse.getPaymentStatus())
                    .stripeStatus(stripeSessionResponse.getStatus())
                    .paymentStatus(payment.getStatus())
                    .paymentDueStatus(paymentDue.getStatus())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .paidAt(payment.getPaidAt())
                    .build();


            // if hitting the success url before webhook affecting on database
            if (!recorded) {
                return new ResponseEntity<>(
                        new ReturnObject("Payment received by Stripe, updating payment status.", true, response),
                        HttpStatus.OK
                );
            }

            return new ResponseEntity<>(
                    new ReturnObject("Payment recorded successfully", true, response),
                    HttpStatus.OK
            );

        } catch (FeignException e) {
            return new ResponseEntity<>(
                    new ReturnObject(e.getMessage(), false, null),
                    HttpStatus.BAD_REQUEST
            );
        }
    }


    private void markSuccessPayment(Payment payment, PaymentDues paymentDue) {
        payment.setPaidFlag(1);
        payment.setPaidAt(Timestamp.valueOf(LocalDateTime.now()));
        payment.setStatus(PaymentStatus.PAID);
        payment.setCheckoutUrl(null);
        payment.setCheckoutExpiresAt(null);
        paymentRepository.save(payment);

        paymentDue.setPaidFlag(1);
        paymentDue.setPaidAt(Timestamp.valueOf(LocalDateTime.now()));
        paymentDue.setStatus(PaymentStatus.PAID);
        paymentDue.setPaymentId(payment.getId());
        paymentDuesRepository.save(paymentDue);

    }

    private StripePaymentResultDto webhookResponse(Payment payment, PaymentDues paymentDue) {

        return StripePaymentResultDto.builder()
                .paymentId(payment.getId())
                .paymentDueId(paymentDue.getId())
                .checkoutSessionId(payment.getProviderReference())
                .paymentStatus(payment.getStatus())
                .paymentDueStatus(paymentDue.getStatus())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paidAt(payment.getPaidAt())
                .build();
    }

    private boolean isValidStripeSignature(String rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }

        String timestamp = null;
        List<String> signatures = new ArrayList<>();

        for (String part : signatureHeader.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) continue;

            if ("t".equals(pair[0])) timestamp = pair[1];
            if ("v1".equals(pair[0])) signatures.add(pair[1]);
        }

        if (timestamp == null || signatures.isEmpty()) {
            return false;
        }

        try {
            long sentAt = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();

            // Reject old or future requests to limit replay attacks.
            if (sentAt < now - 300 || sentAt > now + 300) {
                return false;
            }

            String signedPayload = timestamp + "." + rawBody;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    stripeSigningKey.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            byte[] expected = mac.doFinal(
                    signedPayload.getBytes(StandardCharsets.UTF_8)
            );

            for (String signature : signatures) {
                try {
                    byte[] received = HexFormat.of().parseHex(signature);
                    if (MessageDigest.isEqual(expected, received)) {
                        return true;
                    }
                } catch (IllegalArgumentException ignored) {
                    // Malformed hex signature; try any other v1 signature.
                }
            }
            return false;
        } catch (NumberFormatException | GeneralSecurityException e) {
            return false;
        }
    }

}
