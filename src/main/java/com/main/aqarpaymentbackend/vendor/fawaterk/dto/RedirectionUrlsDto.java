package com.main.aqarpaymentbackend.vendor.fawaterk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RedirectionUrlsDto {

    @JsonProperty("successUrl")
    private String successUrl;
    @JsonProperty("failUrl")
    private String failUrl;
    @JsonProperty("pendingUrl")
    private String pendingUrl;
    @JsonProperty("webhookUrl")
    private String webhookUrl;
}
