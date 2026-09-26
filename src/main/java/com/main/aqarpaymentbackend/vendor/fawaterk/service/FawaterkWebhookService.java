package com.main.aqarpaymentbackend.vendor.fawaterk.service;

import com.main.aqarpaymentbackend.model.enums.PaymentStatus;
import com.main.aqarpaymentbackend.vendor.fawaterk.dto.FawaterkOAuthResponseDto;
import com.main.aqarpaymentbackend.vendor.fawaterk.dto.FawaterkPaymentWebhookDto;
import com.main.aqarpaymentbackend.vendor.fawaterk.dto.GetTransactionDataRequestDto;
import com.main.aqarpaymentbackend.vendor.fawaterk.dto.GetTransactionDataResponseDto;
import lombok.extern.slf4j.Slf4j;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.HexFormat;
import com.main.aqarpaymentbackend.model.Payment;
import com.main.aqarpaymentbackend.model.enums.PaymentGateway;
import com.main.aqarpaymentbackend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class FawaterkWebhookService {

    private final PaymentRepository paymentRepository;
    private final FawaterkService fawaterkService;

    // Read the HASH API key from our configuration
    @Value("${fawaterk.hash-api-key}")
    private String hashApiKey;

    public boolean isValidSignature(FawaterkPaymentWebhookDto webhook) {

        // Reject missing fields needed to calculate the signature
        if (webhook == null
                || webhook.getTransactionId() == null
                || webhook.getTransactionKey() == null
                || webhook.getPaymentMethod() == null
                || webhook.getTransactionHashKey() == null) {
            return false;
        }

        try {
            // Build the message from this webhook
            String message = buildSignatureMessage(webhook);

            // Calculate our expected signature
            byte[] expectedSignature = calculateSignature(message);

            // Convert Fawaterk's hexadecimal signature into bytes
            byte[] receivedSignature = HexFormat.of().parseHex(
                    webhook.getTransactionHashKey()
            );

            // Compare our signature with Fawaterk's signature
            return MessageDigest.isEqual(
                    expectedSignature, receivedSignature
            );

        } catch (IllegalArgumentException e) {
            // Invalid hexadecimal text means an invalid signature
            return false;

        } catch (GeneralSecurityException e) {
            // A failure in the signature calculator is a server error
            throw new IllegalStateException(
                    "Could not calculate webhook signature", e
            );
        }
    }

    public void handleWebhook(FawaterkPaymentWebhookDto webhook) {

        // Verify the notification signature
        if (!isValidSignature(webhook)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid webhook signature"
            );
        }

        // Find the local payment associated with this checkout
        Payment payment = paymentRepository
                .findByProviderReferenceAndPaymentGateway(
                        webhook.getTransactionKey(),
                        PaymentGateway.FAWATERK
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found"
                ));

        // Build the lookup request using our saved checkout reference
        GetTransactionDataRequestDto request =
                GetTransactionDataRequestDto.builder()
                        .intentKey(payment.getProviderReference())
                        .build();

        // Get an OAuth token to authenticate with Fawaterk
        FawaterkOAuthResponseDto oauth =
                fawaterkService.fawaterkOAuth();

        // Ask Fawaterk for the current transaction details
        GetTransactionDataResponseDto response =
                fawaterkService.getTransactionData(
                        "Bearer " + oauth.getAccessToken(),
                        request
                );

        // Reject an unsuccessful or empty lookup
        if (response == null
                || !"success".equals(response.getStatus())
                || response.getData() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not verify the payment with Fawaterk"
            );
        }

        // Read the transaction details returned directly by Fawaterk
        var transaction = response.getData();

        // Confirm Fawaterk returned the checkout we requested
        if (!payment.getProviderReference().equals(transaction.getIntentKey())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Fawaterk returned a different checkout reference"
            );
        }

        // Confirm the amount matches our saved payment
        if (transaction.getTotal() == null
                || payment.getAmount().compareTo(transaction.getTotal()) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment amount does not match"
            );
        }

        // Confirm the currency matches our saved payment
        if (!payment.getCurrency().equals(transaction.getCurrency())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment currency does not match"
            );
        }

        // Accept only the documented paid flag values
        if (transaction.getPaid() == null
                || (transaction.getPaid() != 0 && transaction.getPaid() != 1)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Fawaterk returned an invalid paid flag"
            );
        }

        // A repeated notification must not change an already-paid payment
        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }

        // Unpaid does not mean failed; leave the payment unchanged
        if (transaction.getPaid() == 0) {
            return;
        }

        // Read the provider transaction ID from the verified lookup response
        Integer providerTransactionId;
        try {
            providerTransactionId =
                    Integer.valueOf(transaction.getTransactionId());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Fawaterk returned an invalid transaction ID"
            );
        }

        // A completed payment must have a valid ID and payment time
        if (providerTransactionId <= 0 || transaction.getPaidAt() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Fawaterk returned incomplete paid transaction details"
            );
        }

        // Record the payment result confirmed directly by Fawaterk
        payment.setStatus(PaymentStatus.PAID);
        payment.setProviderTransactionId(providerTransactionId);
        payment.setPaidAt(Timestamp.from(transaction.getPaidAt()));

        // Update the existing row rather than creating another payment
        paymentRepository.save(payment);
    }


    // Build the exact message used to calculate the webhook signature
    private String buildSignatureMessage(FawaterkPaymentWebhookDto webhook) {
        return "TransactionId=" + webhook.getTransactionId()
                + "&TransactionKey=" + webhook.getTransactionKey()
                + "&PaymentMethod=" + webhook.getPaymentMethod();
    }

    private byte[] calculateSignature(String message)
            throws GeneralSecurityException {

        // Select the HMAC-SHA256 algorithm
        Mac mac = Mac.getInstance("HmacSHA256");

        // Turn our HASH API key into a key Java can use
        SecretKeySpec key = new SecretKeySpec(
                hashApiKey.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );

        // Give the algorithm our key
        mac.init(key);

        // Calculate and return the signature bytes
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }

}
