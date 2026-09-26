package com.main.aqarpaymentbackend.vendor.fawaterk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateTransactionResponseDataDto {

    @JsonProperty("intent_key")
    private String intentKey;
    @JsonProperty("url")
    private String url;
    @JsonProperty("expires_in")
    private Long expiresIn;
}
