package com.gcptest.microsoftgraph.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphAnalysisReport {
    private LocalDateTime analysisTimestamp;
    private int totalUsers;
    private int activeUsers;
    private int inactiveUsers;
    private int totalRoles;
    private List<DirectoryRoleReport> roleReports;
    private List<UserRole> userRoles;
    private Map<String, Integer> departmentStats;
    private Map<String, Integer> roleDistribution;
    private String csvReportPath;
}