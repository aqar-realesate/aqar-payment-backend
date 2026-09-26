package com.main.aqarpaymentbackend.vendor.fawaterk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerInfoDto {

    private String firstName;
    private String lastName;
    private String phone;
    private String email;
}
