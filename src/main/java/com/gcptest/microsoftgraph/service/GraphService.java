package com.gcptest.microsoftgraph.service;

import com.gcptest.microsoftgraph.dto.UserRole;
import com.gcptest.microsoftgraph.dto.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@ConditionalOnProperty(name = "microsoft.graph.enabled", havingValue = "true", matchIfMissing = false)
public class GraphService {
    
    @Autowired
    private AuthenticationService authenticationService;
    
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final String GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0";
    private static final String USER_FILE = "users.txt";
    
    /**
     * Makes an authenticated GET request to Microsoft Graph API
     * @param endpoint The API endpoint to call
     * @return JsonNode with response data
     * @throws Exception if the request fails
     */
    private JsonNode makeGraphRequest(String endpoint) throws Exception {
        String accessToken = authenticationService.getAccessToken();
        
        Request request = new Request.Builder()
                .url(GRAPH_BASE_URL + endpoint)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Content-Type", "application/json")
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                return objectMapper.readTree(responseBody);
            } else {
                log.error("Graph API request failed: {} - {}", response.code(), response.message());
                if (response.code() == 404) {
                    return null; // User not found
                }
                throw new RuntimeException("Graph API request failed: " + response.code());
            }
        }
    }
    
    /**
     * Loads user list from users.txt file, creates default if not exists
     * @return List of user IDs/UPNs to analyze
     */
    public List<String> loadUserList() {
        Path userFilePath = Paths.get(USER_FILE);
        List<String> userList = new ArrayList<>();
        
        try {
            if (Files.exists(userFilePath)) {
                userList = Files.readAllLines(userFilePath)
                        .stream()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .collect(Collectors.toList());
                log.info("Usuarios cargados desde {}: {}", USER_FILE, userList.size());
            } else {
                // Create default users.txt file
                userList.add("lesly.villanueva@oechsle.pe");
                String defaultContent = "# Archivo de usuarios para análisis de Microsoft Graph\n" +
                                      "# Un usuario por línea (UserPrincipalName o ID)\n" +
                                      "# Ejemplo:\n" +
                                      "# lesly.villanueva@oechsle.pe\n" +
                                      "# juan.perez@oechsle.pe\n\n" +
                                      "lesly.villanueva@oechsle.pe";
                Files.write(userFilePath, defaultContent.getBytes());
                log.info("Archivo {} no encontrado. Se creó con usuarios de ejemplo.", USER_FILE);
            }
        } catch (IOException e) {
            log.error("Error leyendo archivo {}: {}", USER_FILE, e.getMessage());
            userList.add("lesly.villanueva@oechsle.pe"); // fallback
        }
        
        return userList;
    }
    
    /**
     * Gets user information with error handling for non-existent users
     * @param userId User ID or UPN
     * @return UserStatus indicating if user exists, not found, or error
     */
    public UserStatus getUserWithStatus(String userId) {
        try {
            log.debug("Consultando usuario: {}", userId);
            
            // First, try to get user basic info
            String endpoint = "/users/" + userId + "?$select=id,userPrincipalName,displayName,department,jobTitle,accountEnabled,mail";
            JsonNode userResponse = makeGraphRequest(endpoint);
            
            if (userResponse == null) {
                log.warn("Usuario no encontrado: {}", userId);
                return UserStatus.notFound();
            }
            
            // User exists, now get their role assignments
            List<JsonNode> roles = getUserRoleAssignments(userId);
            
            List<UserRole.DirectoryRole> assignedRoles = new ArrayList<>();
            for (JsonNode role : roles) {
                if (role.has("id") && role.has("displayName")) {
                    UserRole.DirectoryRole directoryRole = new UserRole.DirectoryRole(
                            role.get("id").asText(),
                            role.get("displayName").asText(),
                            role.has("description") ? role.get("description").asText() : "",
                            "Direct", // For now, assume direct assignment
                            null // No group assignment info yet
                    );
                    assignedRoles.add(directoryRole);
                }
            }
            
            UserRole userRole = new UserRole(
                    userResponse.get("id").asText(),
                    userResponse.has("userPrincipalName") ? userResponse.get("userPrincipalName").asText() : "",
                    userResponse.has("displayName") ? userResponse.get("displayName").asText() : "",
                    userResponse.has("department") ? userResponse.get("department").asText() : null,
                    userResponse.has("jobTitle") ? userResponse.get("jobTitle").asText() : null,
                    userResponse.has("accountEnabled") && userResponse.get("accountEnabled").asBoolean(),
                    assignedRoles
            );
            
            return UserStatus.exists(userRole);
            
        } catch (Exception e) {
            if (e.getMessage() != null && (
                    e.getMessage().contains("does not exist") || 
                    e.getMessage().contains("ResourceNotFound") ||
                    e.getMessage().contains("Request_ResourceNotFound"))) {
                log.warn("Usuario no encontrado: {}", userId);
                return UserStatus.notFound();
            } else {
                log.error("Error al consultar usuario {}: {}", userId, e.getMessage());
                return UserStatus.error(e.getMessage());
            }
        }
    }
    
    /**
     * Builds comprehensive UserRole analysis from file-based user list
     * @return List of UserStatus objects with analysis results
     * @throws Exception if API calls fail
     */
    public List<UserStatus> buildUserRoleAnalysisFromFile() throws Exception {
        log.info("Building comprehensive user-role analysis from file");
        
        List<String> userList = loadUserList();
        List<UserStatus> userStatusList = new ArrayList<>();
        
        log.info("Analizando {} usuarios...", userList.size());
        
        for (String userId : userList) {
            UserStatus status = getUserWithStatus(userId);
            userStatusList.add(status);
            
            // Log progress
            if (status.getStatus() == UserStatus.Status.EXISTS) {
                log.info("✅ {}: {} roles encontrados", userId, status.getUserRole().getAssignedRoles().size());
            } else if (status.getStatus() == UserStatus.Status.NOT_FOUND) {
                log.warn("❌ {}: Usuario no encontrado", userId);
            } else {
                log.error("⚠️ {}: Error - {}", userId, status.getErrorMessage());
            }
        }
        
        // Print statistics
        long existingUsers = userStatusList.stream().filter(s -> s.getStatus() == UserStatus.Status.EXISTS).count();
        long notFoundUsers = userStatusList.stream().filter(s -> s.getStatus() == UserStatus.Status.NOT_FOUND).count();
        long errorUsers = userStatusList.stream().filter(s -> s.getStatus() == UserStatus.Status.ERROR).count();
        
        log.info("=== ESTADÍSTICAS ===");
        log.info("👥 Total usuarios procesados: {}", userList.size());
        log.info("✅ Usuarios existentes: {}", existingUsers);
        log.info("❌ Usuarios no encontrados: {}", notFoundUsers);
        log.info("⚠️ Usuarios con error: {}", errorUsers);
        
        return userStatusList;
    }
    
    /**
     * Retrieves all users from Azure AD
     * @return List of JsonNode objects representing users
     * @throws Exception if API call fails
     */
    public List<JsonNode> getAllUsers() throws Exception {
        log.info("Fetching all users from Azure AD");
        
        List<JsonNode> allUsers = new ArrayList<>();
        String endpoint = "/users?$select=id,userPrincipalName,displayName,department,jobTitle,accountEnabled,mail&$top=999";
        
        JsonNode response = makeGraphRequest(endpoint);
        
        if (response.has("value")) {
            JsonNode users = response.get("value");
            for (JsonNode user : users) {
                allUsers.add(user);
            }
            log.info("Retrieved {} users from Azure AD", allUsers.size());
        }
        
        return allUsers;
    }
    
    /**
     * Retrieves all directory roles from Azure AD
     * @return List of JsonNode objects representing directory roles
     * @throws Exception if API call fails
     */
    public List<JsonNode> getAllDirectoryRoles() throws Exception {
        log.info("Fetching all directory roles from Azure AD");
        
        List<JsonNode> allRoles = new ArrayList<>();
        String endpoint = "/directoryRoles?$select=id,displayName,description";
        
        JsonNode response = makeGraphRequest(endpoint);
        
        if (response.has("value")) {
            JsonNode roles = response.get("value");
            for (JsonNode role : roles) {
                allRoles.add(role);
            }
            log.info("Retrieved {} directory roles from Azure AD", allRoles.size());
        }
        
        return allRoles;
    }
    
    /**
     * Gets role assignments for a specific user
     * @param userId The user's ID
     * @return List of JsonNode objects representing assigned roles
     * @throws Exception if API call fails
     */
    public List<JsonNode> getUserRoleAssignments(String userId) throws Exception {
        List<JsonNode> userRoles = new ArrayList<>();
        
        try {
            String endpoint = "/users/" + userId + "/memberOf?$filter=odata.type eq 'microsoft.graph.directoryRole'";
            JsonNode response = makeGraphRequest(endpoint);
            
            if (response.has("value")) {
                JsonNode roles = response.get("value");
                for (JsonNode role : roles) {
                    userRoles.add(role);
                }
            }
        } catch (Exception e) {
            log.warn("Could not retrieve role assignments for user {}: {}", userId, e.getMessage());
        }
        
        return userRoles;
    }
    
    /**
     * Builds comprehensive UserRole objects with all role information
     * @return List of UserRole objects
     * @throws Exception if API calls fail
     */
    public List<UserRole> buildUserRoleAnalysis() throws Exception {
        log.info("Building comprehensive user-role analysis");
        
        List<JsonNode> users = getAllUsers();
        List<UserRole> userRoles = new ArrayList<>();
        
        for (JsonNode user : users) {
            if (user.has("id")) {
                String userId = user.get("id").asText();
                List<JsonNode> roles = getUserRoleAssignments(userId);
                
                List<UserRole.DirectoryRole> assignedRoles = new ArrayList<>();
                for (JsonNode role : roles) {
                    if (role.has("id") && role.has("displayName")) {
                        UserRole.DirectoryRole directoryRole = new UserRole.DirectoryRole(
                                role.get("id").asText(),
                                role.get("displayName").asText(),
                                role.has("description") ? role.get("description").asText() : "",
                                "Direct", // For now, assume direct assignment
                                null // No group assignment info yet
                        );
                        assignedRoles.add(directoryRole);
                    }
                }
                
                UserRole userRole = new UserRole(
                        userId,
                        user.has("userPrincipalName") ? user.get("userPrincipalName").asText() : "",
                        user.has("displayName") ? user.get("displayName").asText() : "",
                        user.has("department") ? user.get("department").asText() : null,
                        user.has("jobTitle") ? user.get("jobTitle").asText() : null,
                        user.has("accountEnabled") && user.get("accountEnabled").asBoolean(),
                        assignedRoles
                );
                
                userRoles.add(userRole);
            }
        }
        
        log.info("Analysis complete. Processed {} users with role assignments", userRoles.size());
        return userRoles;
    }
}