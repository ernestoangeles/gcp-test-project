package com.gcptest.microsoftgraph.service;

import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Service
@Slf4j
@ConditionalOnProperty(name = "microsoft.graph.enabled", havingValue = "true", matchIfMissing = false)
public class AuthenticationService {
    
    @Value("${microsoft.graph.tenant-id}")
    private String tenantId;
    
    @Value("${microsoft.graph.client-id}")
    private String clientId;
    
    @Value("${microsoft.graph.client-secret}")
    private String clientSecret;
    
    private static final String AUTHORITY = "https://login.microsoftonline.com/%s";
    private static final String SCOPE = "https://graph.microsoft.com/.default";
    
    /**
     * Obtains an access token for Microsoft Graph API using client credentials flow
     * @return Access token string
     * @throws Exception if authentication fails
     */
    public String getAccessToken() throws Exception {
        log.info("Authenticating with Azure AD for tenant: {}", tenantId);
        
        ConfidentialClientApplication app = ConfidentialClientApplication.builder(
                clientId,
                ClientCredentialFactory.createFromSecret(clientSecret))
                .authority(String.format(AUTHORITY, tenantId))
                .build();
        
        ClientCredentialParameters clientCredentialParam = ClientCredentialParameters.builder(
                Collections.singleton(SCOPE))
                .build();
        
        CompletableFuture<IAuthenticationResult> future = app.acquireToken(clientCredentialParam);
        IAuthenticationResult result = future.get();
        
        if (result != null && result.accessToken() != null) {
            log.info("Successfully obtained access token");
            return result.accessToken();
        } else {
            throw new RuntimeException("Failed to obtain access token");
        }
    }
    
    /**
     * Validates that all required configuration properties are set
     * @return true if configuration is valid
     */
    public boolean validateConfiguration() {
        boolean isValid = tenantId != null && !tenantId.isEmpty() &&
                         clientId != null && !clientId.isEmpty() &&
                         clientSecret != null && !clientSecret.isEmpty();
        
        if (!isValid) {
            log.error("Microsoft Graph configuration is incomplete. Please check application.yaml");
            log.error("Required properties: microsoft.graph.tenant-id, microsoft.graph.client-id, microsoft.graph.client-secret");
        }
        
        return isValid;
    }
}