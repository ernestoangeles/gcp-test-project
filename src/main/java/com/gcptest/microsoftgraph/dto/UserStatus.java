package com.gcptest.microsoftgraph.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatus {
    public enum Status {
        EXISTS("Existe"),
        NOT_FOUND("No existe"),
        ERROR("Error");
        
        private final String displayName;
        
        Status(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private Status status;
    private String errorMessage;
    private UserRole userRole;
    
    public static UserStatus exists(UserRole userRole) {
        return new UserStatus(Status.EXISTS, null, userRole);
    }
    
    public static UserStatus notFound() {
        return new UserStatus(Status.NOT_FOUND, null, null);
    }
    
    public static UserStatus error(String errorMessage) {
        return new UserStatus(Status.ERROR, errorMessage, null);
    }
}