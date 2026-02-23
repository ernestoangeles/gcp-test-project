package com.gcptest.orders.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentStatusResponse {
    private String paymentDate;
    private String status;
    private String paymentMethod;
    private String cardIssuer;
    private String cardBrand;
    private String purchaseType;
    private String trxType;
    private String trxStatus;
    private String trxResponse;
    private String message;
}
