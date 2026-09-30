package com.main.aqarpaymentbackend.vendor.fawaterk.controller;

import com.main.aqarpaymentbackend.util.ReturnObject;
import com.main.aqarpaymentbackend.vendor.fawaterk.dto.FailedWebhookRequestDto;
import com.main.aqarpaymentbackend.vendor.fawaterk.dto.PaidWebhookRequestDto;
import com.main.aqarpaymentbackend.vendor.fawaterk.service.FawaterkWebhooksService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/fawaterak")
@RequiredArgsConstructor
@Slf4j
public class FawaterkWebhooksController {

    private final FawaterkWebhooksService fawaterkWebhooksService;

    @PostMapping("/paid_json")
    public ResponseEntity<ReturnObject> paidWebhook(@RequestBody PaidWebhookRequestDto request) {
        return fawaterkWebhooksService.paidWebhook(request);
    }

    @PostMapping("/failed_json")
    public ResponseEntity<ReturnObject> failedWebhook(@RequestBody FailedWebhookRequestDto request) {
        return fawaterkWebhooksService.failedWebhook(request);
    }

}