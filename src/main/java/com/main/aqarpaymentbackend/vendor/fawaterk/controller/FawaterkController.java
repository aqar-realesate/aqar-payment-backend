package com.main.aqarpaymentbackend.vendor.fawaterk.controller;

import com.main.aqarpaymentbackend.repository.PaymentDuesRepository;
import com.main.aqarpaymentbackend.util.JwtUtil;
import com.main.aqarpaymentbackend.util.ReturnObject;
import com.main.aqarpaymentbackend.vendor.fawaterk.dto.*;
import com.main.aqarpaymentbackend.vendor.fawaterk.service.FawaterkService;
import com.main.aqarpaymentbackend.vendor.fawaterk.service.FawaterkWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@RestController
@RequestMapping("/fawaterk")
@RequiredArgsConstructor
@Slf4j
public class FawaterkController {

    private final FawaterkService fawaterkService;
    private final FawaterkWebhookService webhookService;


    @PostMapping("/auth/token")
    public ResponseEntity<ReturnObject> fawaterkOAuth() {

        FawaterkOAuthResponseDto response = fawaterkService.fawaterkOAuth();

        return new ResponseEntity<>(new ReturnObject(
                "Authenticated Successfully",
                true,
                response
        ), HttpStatus.OK);
    }


     /// Create checkout → save PENDING
     /// Webhook → verify signature → find payment → query Fawaterk → validate result → save PAID


    @PostMapping("/dues/{dueId}/checkout")
    public ResponseEntity<CreateTransactionResponseDto> createTransaction(
            @CookieValue("Authorization") String token,
            @PathVariable("dueId") Integer dueId
    ) {

        try {
            CreateTransactionResponseDto response = fawaterkService.fawaterkCreateTransaction(token, dueId);
            return ResponseEntity.ok().body(response);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(new CreateTransactionResponseDto(
                    "error",
                    e.getReason(),
                    null
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new CreateTransactionResponseDto(
                    "Bad Request",
                    e.getMessage(),
                    null
            ));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<ReturnObject> receiveWebhook(
            @RequestBody FawaterkPaymentWebhookDto webhook) {

        try {
            webhookService.handleWebhook(webhook);
            return new ResponseEntity<>(
                    new ReturnObject(
                            "Webhook received",
                            true,
                            null
                    ), HttpStatus.OK
            );
        } catch (ResponseStatusException e) {
            return new ResponseEntity<>(
                    new ReturnObject(
                            e.getMessage(),
                            false,
                            null

                    ), e.getStatusCode()
            );
        }
    }

    @PostMapping("/getTransactionData")
    public ResponseEntity<GetTransactionDataResponseDto> getTransactionData(
            @RequestBody GetTransactionDataRequestDto request) {

        FawaterkOAuthResponseDto oAuth = fawaterkService.fawaterkOAuth();
        String token = "Bearer "+oAuth.getAccessToken();
        GetTransactionDataResponseDto response = fawaterkService.getTransactionData(token,request);

        return ResponseEntity.ok().body(response);
    }
}
