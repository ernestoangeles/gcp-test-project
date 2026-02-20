package com.gcptest.firebasestorage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirebaseAuthData {
    private String idToken;
    private String refreshToken;
    private String expiresIn;
    private String localId;
    private String email;
    private boolean registered;
    private String error;
    private String errorMessage;
    private String kind;
    private String displayName;
}