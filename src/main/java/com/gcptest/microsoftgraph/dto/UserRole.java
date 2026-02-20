package com.gcptest.microsoftgraph.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {
    private String userId;
    private String userPrincipalName;
    private String displayName;
    private String department;
    private String jobTitle;
    private boolean accountEnabled;
    private List<DirectoryRole> assignedRoles;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DirectoryRole {
        private String roleId;
        private String roleName;
        private String roleDescription;
        private String assignmentType; // "Direct" or "Group"
        private String assignedThrough; // Group name if assigned through group
    }
}