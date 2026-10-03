package com.main.aqarpaymentbackend.vendor.stripe.controller;

import com.main.aqarpaymentbackend.util.ReturnObject;
import com.main.aqarpaymentbackend.vendor.stripe.dto.StripeWebhookRequestDto;
import com.main.aqarpaymentbackend.vendor.stripe.service.StripeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stripe")
@RequiredArgsConstructor
public class StripeController {

    private final StripeService stripeService;

    @PostMapping("/dues/{dueId}/checkout")
    public ResponseEntity<ReturnObject> createCheckout(
            @CookieValue("Authorization") String token,
            @PathVariable("dueId") Integer dueId) {
        return stripeService.createCheckout(token, dueId);
    }

    @GetMapping("/checkout/session/{id}")
    public ResponseEntity<ReturnObject> getSessionById(@PathVariable("id") String id) {
        return stripeService.getSessionById(id);
    }

    @PostMapping("/webhook")
    public ResponseEntity<ReturnObject> stripeWebhook(
            @RequestBody String rawBody,
            @RequestHeader("Stripe-Signature") String signature) {

        return stripeService.stripeWebhook(rawBody, signature);
    }

    @GetMapping("/success")
    public ResponseEntity<ReturnObject> success(
            @RequestParam("session_id") String sessionId) {
        return stripeService.getSuccessResult(sessionId);
    }

    @GetMapping("/cancle")
    public ResponseEntity<ReturnObject> cancel() {
        return new ResponseEntity<>(
                new ReturnObject(
                        "You left Stripe Checkout. Check your payment status or try again.",
                        false,
                        null
                ),
                HttpStatus.OK
        );
    }
}
