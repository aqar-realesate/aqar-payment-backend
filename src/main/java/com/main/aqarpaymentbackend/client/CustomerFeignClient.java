package com.main.aqarpaymentbackend.client;

import com.main.aqarpaymentbackend.util.ReturnObject;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "customer-client",
        url = "${customer.base-url}"
)
public interface CustomerFeignClient {

    @GetMapping("/unit/{unitId}")
    ResponseEntity<ReturnObject> getUnitDetails(@CookieValue("Authorization") String token,
                                                @PathVariable("unitId") Integer unitId);

    @GetMapping("/auth/profile")
    ResponseEntity<ReturnObject> getCustomerDetails(@CookieValue("Authorization") String token);
}
