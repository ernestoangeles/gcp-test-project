package com.gcptest.microsoftgraph.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DirectoryRoleReport {
    private String roleName;
    private String roleDescription;
    private int totalAssignments;
    private int directAssignments;
    private int groupAssignments;
    private List<String> assignedUsers;
    private Map<String, Integer> departmentBreakdown;
}