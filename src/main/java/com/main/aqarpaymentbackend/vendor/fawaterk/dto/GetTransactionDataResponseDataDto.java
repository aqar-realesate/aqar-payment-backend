package com.main.aqarpaymentbackend.vendor.fawaterk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetTransactionDataResponseDataDto {

    @JsonProperty("intent_key")
    private String intentKey;
    @JsonProperty("transaction_id")
    private Integer transactionId;
    @JsonProperty("customer_email")
    private String customerEmail;
    @JsonProperty("transaction_created_at")
    private String transactionCreatedAt;
    @JsonProperty("paid_at")
    private String paidAt;
    @JsonProperty("paid")
    private Integer paidFlag;
    @JsonProperty("status_text")
    private String statusText;
    @JsonProperty("total")
    private BigDecimal total;
    @JsonProperty("currency")
    private String currency;
    @JsonProperty("transaction_link")
    private String transactionLink;
}
