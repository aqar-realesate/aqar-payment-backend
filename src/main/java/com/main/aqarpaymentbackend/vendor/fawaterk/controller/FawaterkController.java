package com.main.aqarpaymentbackend.vendor.fawaterk.controller;

import com.main.aqarpaymentbackend.util.ReturnObject;
import com.main.aqarpaymentbackend.vendor.fawaterk.dto.*;
import com.main.aqarpaymentbackend.vendor.fawaterk.service.FawaterkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/fawaterk")
@RequiredArgsConstructor
@Slf4j
public class FawaterkController {

    private final FawaterkService fawaterkService;

    @PostMapping("/oauth/token")
    public ResponseEntity<ReturnObject> fawaterkOAuth() {

        OAuthResponseDto response = fawaterkService.fawaterkOAuth();

        return new ResponseEntity<>(
                new ReturnObject(
                        "OAuth success",
                        true,
                        response
                ), HttpStatus.OK
        );
    }

    @PostMapping("/dues/{dueId}/createTransaction")
    public ResponseEntity<ReturnObject> createTransaction(
            @CookieValue("Authorization") String token,
            @PathVariable("dueId") Integer dueId) {

        try {
            CreateTransactionResponseDto response = fawaterkService.createTransaction(token, dueId);

            return new ResponseEntity<>(
                    new ReturnObject(
                            "Transaction creation success",
                            true,
                            response
                    ), HttpStatus.OK
            );
        } catch (ResponseStatusException e) {
            log.error(e.getMessage());
            return new ResponseEntity<>(
                    new ReturnObject(
                            e.getReason(),
                            false,
                            null
                    ), e.getStatusCode()
            );
        }
    }

    @GetMapping("/getPaymentMethods")
    public ResponseEntity<ReturnObject> getPaymentMethods() {

        return fawaterkService.getPaymentMethods();
    }

    @GetMapping("/getTransactionData")
    public ResponseEntity<ReturnObject> getTransactionData(@RequestBody GetTransactionDataRequestDto request) {

        if (request == null) {
            return new ResponseEntity<>(
                    new ReturnObject(
                            "Invalid intent key",
                            false,
                            null
                    ), HttpStatus.NOT_FOUND
            );
        }

        return fawaterkService.getTransactionData(request.getIntentKey());
    }


    @GetMapping("/success")
    public ResponseEntity<ReturnObject> successTransaction(@RequestParam("intent_key") String intentKey) {

        return fawaterkService.successTransaction(intentKey);
    }

    @GetMapping("/fail")
    public ResponseEntity<ReturnObject> failTransaction(@RequestParam("intent_key") String intentKey,
                                                        @RequestParam("errorMessage") String errorMessage) {

        return fawaterkService.failTransaction(intentKey, errorMessage);
    }

    @GetMapping("/pending")
    public ResponseEntity<ReturnObject> pendingTransaction(@RequestParam("intent_key") String intentKey,
                                                        @RequestParam(value = "errorMessage", required = false) String errorMessage) {

        return fawaterkService.pendingTransaction(intentKey, errorMessage);
    }
}
