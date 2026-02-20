package com.gcptest.firebasestorage.dto;

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
public class StorageAccessReport {
    
    private String reportId;
    private LocalDateTime generatedAt;
    private String targetBucket;
    private String executedBy;
    private ReportSummary summary;
    private List<StorageAccessTestResult> testResults;
    private List<String> generalRecommendations;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportSummary {
        private int totalTests;
        private int unauthorizedAccessFound;
        private int criticalPermissionIssues;
        private int highRiskPermissionIssues;
        private int mediumRiskPermissionIssues;
        private int lowRiskPermissionIssues;
        private StorageAccessTestResult.RiskLevel overallRiskLevel;
        private boolean storageCompromised;
        private List<String> permissionsFound;
    }
}