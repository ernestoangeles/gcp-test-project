package com.gcptest.firebasestorage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageAccessTestResult {
    
    private String testId;
    private String testName;
    private String description;
    private AccessTestType accessTestType;
    private TestStatus status;
    private RiskLevel riskLevel;
    private LocalDateTime executionTime;
    private String errorMessage;
    private Map<String, Object> discoveredData;
    private List<String> recommendations;
    private String details;
    
    public enum AccessTestType {
        AUTHENTICATION_TEST,
        READ_PERMISSION_TEST,
        WRITE_PERMISSION_TEST,
        LIST_PERMISSION_TEST,
        DELETE_PERMISSION_TEST,
        METADATA_ACCESS_TEST
    }
    
    public enum TestStatus {
        SUCCESS,
        FAILED,
        ERROR,
        SKIPPED
    }
    
    public enum RiskLevel {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW,
        INFO
    }
}