package com.main.aqarpaymentbackend.vendor.fawaterk.util;

import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Component
public class CalculateHashKeys {

    public static String calculateWebhookHash(
            String transactionKey,
            Integer transactionId,
            String paymentMethod,
            String hashApiKey
    ) throws Exception {
        String message = "TransactionId=" + transactionId + "&TransactionKey=" + transactionKey + "&PaymentMethod=" + paymentMethod;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(hashApiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
