package com.main.aqarpaymentbackend.vendor.paymob.controller;

import com.main.aqarpaymentbackend.util.ReturnObject;
import com.main.aqarpaymentbackend.vendor.paymob.dto.PaymobAuthTokenResponseDto;
import com.main.aqarpaymentbackend.vendor.paymob.service.PaymobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/paymob")
@RequiredArgsConstructor
public class PaymobController {

    private final PaymobService paymobService;

    @PostMapping("/auth/token")
    public ResponseEntity<ReturnObject> authToken() {

        PaymobAuthTokenResponseDto response = paymobService.authToken();

        return new ResponseEntity<>(
                new ReturnObject(
                        "Paymob auth success",
                        true,
                        response
                ), HttpStatus.OK
        );
    }

    @PostMapping("dues/{dueId}/createIntention")
    public ResponseEntity<ReturnObject> createIntention(
            @CookieValue("Authorization") String token,
            @PathVariable("dueId") Integer dueId) {
        return paymobService.createIntention(token, dueId);
    }
}
