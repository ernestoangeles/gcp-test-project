package com.gcptest.orders.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VtexOrderResponse {
    private String status;
    private String statusDescription;
    private Boolean isCompleted;
    private String creationDate;
    private String hostname;
    private CancellationData cancellationData;
    private List<CancellationRequest> cancellationRequests;
    private PaymentData paymentData;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentData {
        private List<Transaction> transactions;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Transaction {
        private List<Payment> payments;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payment {
        private String id;
        private String paymentSystem;
        private String paymentSystemName;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CancellationData {
        @JsonProperty("CancellationDate")
        private String cancellationDate;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CancellationRequest {
        private String cancellationRequestDate;
    }
}
