package com.gcptest.orders.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderProcessingReport {
    private LocalDateTime processingDate;
    private int totalOrders;
    private int successfulOrders;
    private int failedOrders;
    private List<OrderProcessingResult> results;
    private long processingTimeMs;
}
