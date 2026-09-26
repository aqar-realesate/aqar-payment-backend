package com.main.aqarpaymentbackend.vendor.fawaterk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetTransactionDataResponseDataDto {

    @JsonProperty("intent_key")
    private String intentKey;
    @JsonProperty("transaction_id")
    private String transactionId;
    @JsonProperty("customer_email")
    private String customerEmail;
    @JsonProperty("commission")
    private String commission;
    @JsonProperty("transaction_created_at")
    private String transactionCreatedAt;
    @JsonProperty("paid")
    private Integer paid;
    @JsonProperty("paid_at")
    private Instant paidAt;
    @JsonProperty("status_text")
    private String statusText;
    @JsonProperty("total")
    private BigDecimal total;
    @JsonProperty("currency")
    private String currency;
    @JsonProperty("payment_method")
    private String paymentMethod;
    @JsonProperty("pay_load")
    private JsonNode payLoad;
    @JsonProperty("due_date")
    private String dueDate;
    @JsonProperty("transaction_link")
    private String transactionLink;
}
