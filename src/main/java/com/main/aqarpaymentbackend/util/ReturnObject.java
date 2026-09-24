package com.main.aqarpaymentbackend.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReturnObject {
    private String message;
    private Boolean status;
    private Object data;
}