package com.main.aqarpaymentbackend.vendor.paymob.util;

import com.main.aqarpaymentbackend.vendor.paymob.dto.PaymobWebhookRequestDto;
import com.main.aqarpaymentbackend.vendor.paymob.dto.WebhookObjBodyDto;
import com.main.aqarpaymentbackend.vendor.paymob.dto.WebhookObjSourceDataBodyDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;


@Component
public class PaymobHmacVerifier {

    private final String hmacSecret;

    public PaymobHmacVerifier(
            @Value("${paymob.hmac-secret}")
            String hmacSecret
    ) {
        this.hmacSecret = hmacSecret;
    }

    // For webhook json request
    public boolean isValid(PaymobWebhookRequestDto request, String receivedHmac) {
        if (request == null
                || !"TRANSACTION".equals(request.getCallbackType())
                || request.getObj() == null
                || receivedHmac == null
                || receivedHmac.length() != 128) {
            return false;
        }

        WebhookObjBodyDto obj = request.getObj();
        if (obj.getOrder() == null || obj.getSourceData() == null
                || obj.getPaymentAmount() == null) {
            return false;
        }

        WebhookObjSourceDataBodyDto source = obj.getSourceData();

        try {
            // Paymob requires these values in this exact order, with no separator.
            String data = String.join("",
                    obj.getPaymentAmount().toBigIntegerExact().toString(),
                    value(obj.getCreatedAt()),
                    value(obj.getPaymentCurrency()),
                    value(obj.getPaymentErrorCheck()),
                    value(obj.getHasParentTransaction()),
                    value(obj.getTransactionId()),
                    value(obj.getIntegrationId()),
                    value(obj.getIs3dSecure()),
                    value(obj.getIsAuth()),
                    value(obj.getIsCapture()),
                    value(obj.getIsRefunded()),
                    value(obj.getIsStandalonePayment()),
                    value(obj.getIsVoided()),
                    value(obj.getOrder().getId()),
                    value(obj.getOwner()),
                    value(obj.getPending()),
                    value(source.getPan()),
                    value(source.getSubType()),
                    value(source.getType()),
                    value(obj.getSuccess())
            );

            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(
                    hmacSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA512"
            ));

            byte[] calculated = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            byte[] received = HexFormat.of().parseHex(receivedHmac);

            return MessageDigest.isEqual(calculated, received);
        } catch (IllegalArgumentException | ArithmeticException e) {
            // Missing field, non-integer amount, or malformed hex HMAC.
            return false;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not calculate Paymob HMAC", e);
        }
    }

    // For redirect param request
    public boolean isValid(Map<String, String> params, String receivedHmac) {
        if (params == null || receivedHmac == null || receivedHmac.length() != 128) {
            return false;
        }

        String[] keys = {
                "amount_cents",
                "created_at",
                "currency",
                "error_occured",
                "has_parent_transaction",
                "id",
                "integration_id",
                "is_3d_secure",
                "is_auth",
                "is_capture",
                "is_refunded",
                "is_standalone_payment",
                "is_voided",
                "order_id",
                "owner",
                "pending",
                "source_data.pan",
                "source_data.sub_type",
                "source_data.type",
                "success"
        };

        StringBuilder data = new StringBuilder();
        for (String key : keys) {
            String field = params.get(key);
            if ("order_id".equals(key) && field == null) { field = params.get("order"); }
            if (field == null) { return false; }
            data.append(field);
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec( hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));

            byte[] calculated = mac.doFinal( data.toString().getBytes(StandardCharsets.UTF_8));
            byte[] received = HexFormat.of().parseHex(receivedHmac);
            return MessageDigest.isEqual(calculated, received);
        } catch (IllegalArgumentException e) {
            return false;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not calculate Paymob HMAC", e);
        }
    }

    private static String value(Object field) {
        if (field == null) {
            throw new IllegalArgumentException("Missing signed Paymob field");
        }
        return field.toString();
    }
}