package com.gcptest.orders.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderProcessingResult {
    private String orderId;
    private String vtexPaymentId;
    private String paymentSystem;
    private String paymentSystemName;
    
    // Datos de VTEX Order
    private String hostname;
    private String creationDate;
    private String cancellationDate;
    private String cancellationRequestDate;
    private String orderStatus;
    private String orderStatusDescription;
    private Boolean orderIsCompleted;
    
    // Datos de Payment Status
    private String paymentDate;
    private String paymentStatus;
    private String paymentMethod;
    private String cardIssuer;
    private String cardBrand;
    private String purchaseType;
    private String trxType;
    private String trxStatus;
    private String trxResponse;
    private String message;
    
    // Control de errores
    private boolean success;
    private String errorMessage;
}
