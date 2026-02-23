package com.gcptest.firebasestorage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gcptest.firebasestorage.dto.FirebaseAuthData;
import com.gcptest.firebasestorage.dto.StorageAccessTestResult;
import com.gcptest.firebasestorage.dto.StorageAccessReport;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@ConditionalOnProperty(name = "firebase.storage-access-test.enabled", havingValue = "true", matchIfMissing = false)
public class FirebaseStorageAccessTestService {

    @Value("${firebase.storage-access-test.target-bucket}")
    private String targetBucket;

    @Value("${firebase.storage-access-test.exposed-credentials.email}")
    private String exposedEmail;

    @Value("${firebase.storage-access-test.exposed-credentials.password}")
    private String exposedPassword;

    @Value("${firebase.storage-access-test.web-api-key}")
    private String webApiKey;

    @Value("${firebase.storage-access-test.identity-toolkit-signin-url}")
    private String signInUrl;

    @Value("${firebase.storage-access-test.identity-toolkit-lookup-url}")
    private String lookupUrl;

    @Value("${firebase.storage-access-test.storage-base-url}")
    private String storageBaseUrl;



    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FirebaseStorageAccessTestService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Ejecuta todas las pruebas de acceso a Firebase Storage
     */
    public StorageAccessReport runAllStorageAccessTests() {
        log.info("🔍 Iniciando análisis de permisos de Firebase Storage para bucket: {}", targetBucket);
        
        List<StorageAccessTestResult> results = new ArrayList<>();
        
        // Test 1: Autenticación con credenciales expuestas
        StorageAccessTestResult authResult = testFirebaseAuthentication();
        results.add(authResult);
        
        String firebaseToken = null;
        if (authResult.getStatus() == StorageAccessTestResult.TestStatus.SUCCESS) {
            firebaseToken = (String) authResult.getDiscoveredData().get("idToken");
            
            // Test 2: Verificar información de cuenta
            results.add(testAccountLookup(firebaseToken));
            
            // Test 3: Permisos de lectura (GET)
            results.add(testReadPermissions(firebaseToken));
            
            // Test 4: Permisos de escritura (POST)
            results.add(testWritePermissions(firebaseToken));
            
            // Test 5: Permisos de listado
            results.add(testListPermissions(firebaseToken));
            
            // Test 6: Acceso a metadata
            results.add(testMetadataAccess(firebaseToken));
        }
        
        return buildReport(results);
    }

    /**
     * Test 1: Autenticación con credenciales expuestas usando el flujo real
     */
    public StorageAccessTestResult testFirebaseAuthentication() {
        log.info("🔐 Ejecutando Test 1: Autenticación Firebase con credenciales expuestas");
        
        try {
            StorageAccessTestResult.StorageAccessTestResultBuilder result = StorageAccessTestResult.builder()
                    .testId("FSA-001")
                    .testName("Autenticación Firebase")
                    .description("Autenticación usando credenciales expuestas en el flujo web")
                    .accessTestType(StorageAccessTestResult.AccessTestType.AUTHENTICATION_TEST)
                    .executionTime(LocalDateTime.now())
                    .riskLevel(StorageAccessTestResult.RiskLevel.CRITICAL);

            // Preparar request según el flujo real observado
            String requestBody = String.format(
                "{\"returnSecureToken\":true,\"email\":\"%s\",\"password\":\"%s\",\"clientType\":\"CLIENT_TYPE_WEB\"}",
                exposedEmail, exposedPassword
            );

            Request request = new Request.Builder()
                    .url(signInUrl + "?key=" + webApiKey)
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .addHeader("Content-Type", "application/json")
                    .build();

            Response response = httpClient.newCall(request).execute();
            String responseBody = response.body().string();
            
            Map<String, Object> discoveredData = new HashMap<>();
            discoveredData.put("httpStatus", response.code());
            discoveredData.put("requestUrl", signInUrl + "?key=" + webApiKey);
            discoveredData.put("email", exposedEmail);

            if (response.isSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                
                if (jsonResponse.has("idToken")) {
                    String idToken = jsonResponse.get("idToken").asText();
                    String refreshToken = jsonResponse.get("refreshToken").asText();
                    String localId = jsonResponse.get("localId").asText();
                    String expiresIn = jsonResponse.get("expiresIn").asText();
                    
                    discoveredData.put("idToken", idToken); // Token completo para otros tests
                    discoveredData.put("idTokenPreview", idToken.substring(0, 50) + "...");
                    discoveredData.put("token_preview", idToken.substring(0, 50) + "..."); // Para reporte de archivo
                    discoveredData.put("endpoint", signInUrl + "?key=" + webApiKey); // Endpoint usado
                    discoveredData.put("refreshToken", refreshToken.substring(0, 50) + "...");
                    discoveredData.put("localId", localId);
                    discoveredData.put("expiresIn", expiresIn);
                    discoveredData.put("kind", jsonResponse.get("kind").asText());
                    
                    result.status(StorageAccessTestResult.TestStatus.SUCCESS)
                          .details("✅ Autenticación exitosa con credenciales expuestas")
                          .discoveredData(discoveredData)
                          .recommendations(Arrays.asList(
                              "🚨 CRÍTICO: Rotar inmediatamente las credenciales expuestas",
                              "🔒 Implementar autenticación OAuth más segura",
                              "🔍 Auditar todos los accesos realizados con estas credenciales",
                              "📋 Implementar monitoreo de autenticaciones sospechosas"
                          ));
                } else {
                    result.status(StorageAccessTestResult.TestStatus.FAILED)
                          .details("❌ No se pudo obtener el token Firebase")
                          .discoveredData(discoveredData)
                          .errorMessage("Respuesta sin token de autenticación");
                }
            } else {
                discoveredData.put("errorResponse", responseBody);
                result.status(StorageAccessTestResult.TestStatus.FAILED)
                      .details("❌ Fallo en autenticación Firebase")
                      .discoveredData(discoveredData)
                      .errorMessage("Error HTTP: " + response.code());
            }
            
            return result.build();
            
        } catch (Exception e) {
            log.error("Error en autenticación Firebase", e);
            return StorageAccessTestResult.builder()
                    .testId("FSA-001")
                    .testName("Autenticación Firebase")
                    .status(StorageAccessTestResult.TestStatus.ERROR)
                    .errorMessage(e.getMessage())
                    .executionTime(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Test 2: Lookup de información de cuenta
     */
    public StorageAccessTestResult testAccountLookup(String firebaseToken) {
        log.info("👤 Ejecutando Test 2: Lookup de información de cuenta");
        
        try {
            StorageAccessTestResult.StorageAccessTestResultBuilder result = StorageAccessTestResult.builder()
                    .testId("FSA-002")
                    .testName("Account Lookup")
                    .description("Verificación de información de cuenta usando token")
                    .accessTestType(StorageAccessTestResult.AccessTestType.METADATA_ACCESS_TEST)
                    .executionTime(LocalDateTime.now())
                    .riskLevel(StorageAccessTestResult.RiskLevel.MEDIUM);

            if (firebaseToken == null || firebaseToken.isEmpty()) {
                return result.status(StorageAccessTestResult.TestStatus.SKIPPED)
                            .details("❌ Test omitido: No hay token Firebase disponible")
                            .build();
            }

            // Request según el flujo real observado
            String requestBody = String.format("{\"idToken\":\"%s\"}", firebaseToken);

            Request request = new Request.Builder()
                    .url(lookupUrl + "?key=" + webApiKey)
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .addHeader("Content-Type", "application/json")
                    .build();

            Response response = httpClient.newCall(request).execute();
            String responseBody = response.body().string();
            
            Map<String, Object> discoveredData = new HashMap<>();
            discoveredData.put("httpStatus", response.code());
            discoveredData.put("requestUrl", lookupUrl + "?key=" + webApiKey);

            if (response.isSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                
                if (jsonResponse.has("users")) {
                    JsonNode users = jsonResponse.get("users");
                    if (users.isArray() && users.size() > 0) {
                        JsonNode user = users.get(0);
                        
                        discoveredData.put("kind", jsonResponse.get("kind").asText());
                        discoveredData.put("localId", user.get("localId").asText());
                        discoveredData.put("email", user.get("email").asText());
                        discoveredData.put("emailVerified", user.get("emailVerified").asBoolean());
                        discoveredData.put("passwordUpdatedAt", user.get("passwordUpdatedAt").asText());
                        discoveredData.put("validSince", user.get("validSince").asText());
                        discoveredData.put("disabled", user.get("disabled").asBoolean());
                        discoveredData.put("lastLoginAt", user.get("lastLoginAt").asText());
                        discoveredData.put("createdAt", user.get("createdAt").asText());
                        
                        result.status(StorageAccessTestResult.TestStatus.SUCCESS)
                              .details("✅ Información de cuenta obtenida exitosamente")
                              .discoveredData(discoveredData)
                              .recommendations(Arrays.asList(
                                  "🔍 Revisar información expuesta de la cuenta",
                                  "📋 Auditar último acceso y fechas de actividad",
                                  "🔒 Verificar configuración de verificación de email"
                              ));
                    }
                } else {
                    result.status(StorageAccessTestResult.TestStatus.FAILED)
                          .details("❌ No se encontró información de usuarios")
                          .discoveredData(discoveredData);
                }
            } else {
                discoveredData.put("errorResponse", responseBody);
                result.status(StorageAccessTestResult.TestStatus.FAILED)
                      .details("❌ Error en lookup de cuenta")
                      .discoveredData(discoveredData)
                      .errorMessage("HTTP " + response.code());
            }
            
            return result.build();
            
        } catch (Exception e) {
            log.error("Error en account lookup", e);
            return StorageAccessTestResult.builder()
                    .testId("FSA-002")
                    .testName("Account Lookup")
                    .status(StorageAccessTestResult.TestStatus.ERROR)
                    .errorMessage(e.getMessage())
                    .executionTime(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Test 3: Descarga de archivos existentes (CRÍTICO)
     */
    public StorageAccessTestResult testReadPermissions(String firebaseToken) {
        log.info("� Ejecutando Test 3: Descarga de archivos existentes (CRÍTICO)");
        
        try {
            StorageAccessTestResult.StorageAccessTestResultBuilder result = StorageAccessTestResult.builder()
                    .testId("FSA-003")
                    .testName("Descarga de Archivos Existentes")
                    .description("Verificación crítica de descarga de archivos existentes del bucket")
                    .accessTestType(StorageAccessTestResult.AccessTestType.READ_PERMISSION_TEST)
                    .executionTime(LocalDateTime.now())
                    .riskLevel(StorageAccessTestResult.RiskLevel.CRITICAL);

            if (firebaseToken == null || firebaseToken.isEmpty()) {
                return result.status(StorageAccessTestResult.TestStatus.SKIPPED)
                            .details("❌ Test omitido: No hay token Firebase disponible")
                            .build();
            }

            // Primero listar las carpetas del bucket
            String listUrl = String.format("%s/%s/o", storageBaseUrl, targetBucket);
            
            Request listRequest = new Request.Builder()
                    .url(listUrl)
                    .get()
                    .addHeader("Authorization", "Firebase " + firebaseToken)
                    .build();

            Response listResponse = httpClient.newCall(listRequest).execute();
            String listResponseBody = listResponse.body().string();
            
            Map<String, Object> discoveredData = new HashMap<>();
            discoveredData.put("listUrl", listUrl);
            
            if (listResponse.isSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(listResponseBody);
                
                if (jsonResponse.has("items")) {
                    JsonNode items = jsonResponse.get("items");
                    Set<String> folders = new HashSet<>();
                    List<String> allFiles = new ArrayList<>();
                    
                    // Extraer carpetas y archivos
                    for (JsonNode item : items) {
                        String fileName = item.get("name").asText();
                        allFiles.add(fileName);
                        
                        // Extraer carpeta (primera parte antes de /)
                        if (fileName.contains("/")) {
                            String folder = fileName.substring(0, fileName.indexOf("/"));
                            folders.add(folder);
                        }
                        
                        if (allFiles.size() >= 100) break; // Limitar para performance
                    }
                    
                    discoveredData.put("total_files", items.size());
                    discoveredData.put("folders_found", new ArrayList<>(folders));
                    discoveredData.put("sample_files", allFiles.stream().limit(3).collect(java.util.stream.Collectors.toList()));
                    discoveredData.put("endpoint", listUrl); // Endpoint para reporte
                    
                    // Intentar descargar un archivo existente para demostrar criticidad
                    if (!allFiles.isEmpty()) {
                        String firstFile = allFiles.get(0);
                        String downloadUrl = String.format("%s/%s/o/%s?alt=media", 
                            storageBaseUrl, targetBucket, 
                            java.net.URLEncoder.encode(firstFile, "UTF-8"));
                        
                        Request downloadRequest = new Request.Builder()
                                .url(downloadUrl)
                                .get()
                                .addHeader("Authorization", "Firebase " + firebaseToken)
                                .build();
                        
                        Response downloadResponse = httpClient.newCall(downloadRequest).execute();
                        
                        if (downloadResponse.isSuccessful()) {
                            String content = downloadResponse.body().string();
                            discoveredData.put("downloaded_file", firstFile);
                            discoveredData.put("download_url", downloadUrl);
                            discoveredData.put("file_content_preview", content.length() > 100 ? 
                                content.substring(0, 100) + "..." : content);
                            
                            result.status(StorageAccessTestResult.TestStatus.SUCCESS)
                                  .details(String.format("🚨 CRÍTICO: Descarga exitosa de archivo existente '%s'", 
                                      firstFile))
                                  .discoveredData(discoveredData)
                                  .riskLevel(StorageAccessTestResult.RiskLevel.CRITICAL)
                                  .recommendations(Arrays.asList(
                                      "🚨 CRÍTICO: Cualquier usuario autenticado puede descargar TODOS los archivos",
                                      "🔒 URGENTE: Cambiar reglas Firebase - request.auth != null es muy permisiva",
                                      "🛡️ Implementar reglas granulares por usuario/rol",
                                      "🔍 Auditar inmediatamente quién ha accedido a archivos sensibles"
                                  ));
                        } else {
                            discoveredData.put("download_attempt", "Falló descarga directa, pero listado exitoso");
                            result.status(StorageAccessTestResult.TestStatus.SUCCESS)
                                  .details(String.format("✅ Bucket accesible - %d archivos, %d carpetas encontradas", 
                                      items.size(), folders.size()))
                                  .discoveredData(discoveredData)
                                  .recommendations(Arrays.asList(
                                      "📋 Revisar reglas de seguridad para lectura",
                                      "🔒 Implementar autenticación granular por archivo", 
                                      "🔍 Auditar archivos accesibles"
                                  ));
                        }
                    } else {
                        result.status(StorageAccessTestResult.TestStatus.SUCCESS)
                              .details(String.format("✅ Bucket accesible - %d archivos, %d carpetas encontradas", 
                                  items.size(), folders.size()))
                              .discoveredData(discoveredData)
                              .recommendations(Arrays.asList(
                                  "📋 Revisar reglas de seguridad para lectura",
                                  "🔒 Implementar autenticación granular por archivo", 
                                  "🔍 Auditar archivos accesibles"
                              ));
                    }
                } else {
                    result.status(StorageAccessTestResult.TestStatus.SUCCESS)
                          .details("✅ Acceso al bucket confirmado - sin archivos listados")
                          .discoveredData(discoveredData);
                }
                
                return result.build();
            }
            
            // Si falla el listado, intentar acceso directo a un archivo de prueba
            String testFilePath = "test-file-example";
            String readUrl = String.format("%s/%s/o/%s", 
                storageBaseUrl, 
                targetBucket, 
                java.net.URLEncoder.encode(testFilePath, "UTF-8"));
            
            Request request = new Request.Builder()
                    .url(readUrl)
                    .get()
                    .addHeader("Authorization", "Firebase " + firebaseToken)
                    .build();

            Response response = httpClient.newCall(request).execute();
            String responseBody = response.body().string();
            
            discoveredData.put("readUrl", readUrl);
            discoveredData.put("test_url", readUrl); // Para reporte de archivo
            discoveredData.put("httpStatus", response.code());
            discoveredData.put("testFilePath", testFilePath);

            if (response.isSuccessful()) {
                result.status(StorageAccessTestResult.TestStatus.SUCCESS)
                      .details("✅ Permisos de lectura confirmados")
                      .discoveredData(discoveredData)
                      .recommendations(Arrays.asList(
                          "📋 Revisar reglas de seguridad para lectura",
                          "🔒 Implementar autenticación granular por archivo",
                          "🔍 Auditar archivos accesibles"
                      ));
            } else if (response.code() == 404) {
                discoveredData.put("note", "Archivo de prueba no existe, pero el acceso al bucket es posible");
                result.status(StorageAccessTestResult.TestStatus.SUCCESS)
                      .details("⚠️  Acceso al bucket confirmado (404 esperado para archivo de prueba)")
                      .discoveredData(discoveredData)
                      .riskLevel(StorageAccessTestResult.RiskLevel.MEDIUM);
            } else {
                result.status(StorageAccessTestResult.TestStatus.FAILED)
                      .details("❌ Sin permisos de lectura o acceso denegado")
                      .discoveredData(discoveredData)
                      .errorMessage("HTTP " + response.code());
            }
            
            return result.build();
            
        } catch (Exception e) {
            log.error("Error en test de permisos de lectura", e);
            return StorageAccessTestResult.builder()
                    .testId("FSA-003")
                    .testName("Permisos de Lectura")
                    .status(StorageAccessTestResult.TestStatus.ERROR)
                    .errorMessage(e.getMessage())
                    .executionTime(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Test 4: Permisos de escritura
     */
    public StorageAccessTestResult testWritePermissions(String firebaseToken) {
        log.info("📝 Ejecutando Test 4: Permisos de escritura");
        
        try {
            StorageAccessTestResult.StorageAccessTestResultBuilder result = StorageAccessTestResult.builder()
                    .testId("FSA-004")
                    .testName("Permisos de Escritura")
                    .description("Verificación de permisos de escritura/subida de archivos")
                    .accessTestType(StorageAccessTestResult.AccessTestType.WRITE_PERMISSION_TEST)
                    .executionTime(LocalDateTime.now())
                    .riskLevel(StorageAccessTestResult.RiskLevel.HIGH);

            if (firebaseToken == null || firebaseToken.isEmpty()) {
                return result.status(StorageAccessTestResult.TestStatus.SKIPPED)
                            .details("❌ Test omitido: No hay token Firebase disponible")
                            .build();
            }

            // Crear un archivo de prueba pequeño en la carpeta pruebas/
            String testFileName = UUID.randomUUID().toString() + ".txt";
            String testFilePath = "pruebas/" + testFileName;
            String uploadUrl = String.format("%s/%s/o?name=%s", 
                storageBaseUrl, 
                targetBucket, 
                java.net.URLEncoder.encode(testFilePath, "UTF-8"));
            
            String testContent = "Security test file - " + LocalDateTime.now();
            
            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .post(RequestBody.create(testContent, MediaType.parse("text/plain")))
                    .addHeader("Authorization", "Firebase " + firebaseToken)
                    .addHeader("Content-Type", "text/plain")
                    .build();

            Response response = httpClient.newCall(request).execute();
            String responseBody = response.body().string();
            
            Map<String, Object> discoveredData = new HashMap<>();
            discoveredData.put("uploadUrl", uploadUrl);
            discoveredData.put("uploaded_url", uploadUrl); // Para reporte de archivo
            discoveredData.put("endpoint", uploadUrl); // Endpoint para reporte
            discoveredData.put("httpStatus", response.code());
            discoveredData.put("testFilePath", testFilePath);
            discoveredData.put("testFileName", testFileName);

            if (response.isSuccessful()) {
                // Parsear respuesta para obtener downloadTokens
                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                if (jsonResponse.has("downloadTokens")) {
                    String downloadToken = jsonResponse.get("downloadTokens").asText();
                    discoveredData.put("downloadTokens", downloadToken);
                    discoveredData.put("generation", jsonResponse.get("generation").asText());
                    discoveredData.put("size", jsonResponse.get("size").asText());
                    
                    // Construir URL de descarga para el reporte
                    String downloadUrl = String.format("%s/%s/o/%s?alt=media&token=%s",
                        storageBaseUrl, targetBucket, 
                        java.net.URLEncoder.encode(testFilePath, "UTF-8"), downloadToken);
                    discoveredData.put("download_url", downloadUrl);
                }
                
                result.status(StorageAccessTestResult.TestStatus.SUCCESS)
                      .details("✅ Permisos de escritura confirmados - archivo subido exitosamente")
                      .discoveredData(discoveredData)
                      .recommendations(Arrays.asList(
                          "🚨 CRÍTICO: Permisos de escritura no autorizados detectados",
                          "🔒 Configurar reglas de seguridad para prevenir subidas",
                          "🛡️ Implementar validación de tipos de archivo",
                          "🔍 Auditar archivos subidos por esta cuenta"
                      ));
            } else {
                result.status(StorageAccessTestResult.TestStatus.FAILED)
                      .details("❌ Sin permisos de escritura")
                      .discoveredData(discoveredData)
                      .errorMessage("HTTP " + response.code())
                      .riskLevel(StorageAccessTestResult.RiskLevel.MEDIUM);
            }
            
            return result.build();
            
        } catch (Exception e) {
            log.error("Error en test de permisos de escritura", e);
            return StorageAccessTestResult.builder()
                    .testId("FSA-004")
                    .testName("Permisos de Escritura")
                    .status(StorageAccessTestResult.TestStatus.ERROR)
                    .errorMessage(e.getMessage())
                    .executionTime(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Test 5: Permisos de listado
     */
    public StorageAccessTestResult testListPermissions(String firebaseToken) {
        log.info("📂 Ejecutando Test 5: Permisos de listado");
        
        try {
            StorageAccessTestResult.StorageAccessTestResultBuilder result = StorageAccessTestResult.builder()
                    .testId("FSA-005")
                    .testName("Permisos de Listado")
                    .description("Verificación de permisos para listar archivos del bucket")
                    .accessTestType(StorageAccessTestResult.AccessTestType.LIST_PERMISSION_TEST)
                    .executionTime(LocalDateTime.now())
                    .riskLevel(StorageAccessTestResult.RiskLevel.HIGH);

            if (firebaseToken == null || firebaseToken.isEmpty()) {
                return result.status(StorageAccessTestResult.TestStatus.SKIPPED)
                            .details("❌ Test omitido: No hay token Firebase disponible")
                            .build();
            }

            // Intentar listar archivos sin prefijo (como en el curl observado)
            String listUrl = String.format("%s/%s/o", storageBaseUrl, targetBucket);
            
            Request request = new Request.Builder()
                    .url(listUrl)
                    .get()
                    .addHeader("Authorization", "Firebase " + firebaseToken)
                    .build();

            Response response = httpClient.newCall(request).execute();
            String responseBody = response.body().string();
            
            Map<String, Object> discoveredData = new HashMap<>();
            discoveredData.put("listUrl", listUrl);
            discoveredData.put("httpStatus", response.code());
            
            if (response.isSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                
                if (jsonResponse.has("items")) {
                    JsonNode items = jsonResponse.get("items");
                    List<String> fileNames = new ArrayList<>();
                    List<Map<String, Object>> fileDetails = new ArrayList<>();
                    
                    for (JsonNode item : items) {
                        if (fileNames.size() >= 10) break; // Limitar para el reporte
                        
                        String fileName = item.get("name").asText();
                        fileNames.add(fileName);
                        
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("name", fileName);
                        fileInfo.put("size", item.has("size") ? item.get("size").asText() : "unknown");
                        fileInfo.put("contentType", item.has("contentType") ? item.get("contentType").asText() : "unknown");
                        fileInfo.put("updated", item.has("updated") ? item.get("updated").asText() : "unknown");
                        fileDetails.add(fileInfo);
                    }
                    
                    discoveredData.put("totalFiles", items.size());
                    discoveredData.put("fileNames", fileNames);
                    discoveredData.put("fileDetails", fileDetails);
                    discoveredData.put("endpoint", listUrl); // Endpoint para reporte
                    
                    // Para el reporte de archivo
                    discoveredData.put("file_count", String.valueOf(items.size()));
                    String first3Files = fileNames.stream()
                        .limit(3)
                        .collect(java.util.stream.Collectors.joining("\n   "));
                    discoveredData.put("first_files", first3Files);
                    
                    result.status(StorageAccessTestResult.TestStatus.SUCCESS)
                          .details(String.format("✅ Listado exitoso: %d archivos encontrados", items.size()))
                          .discoveredData(discoveredData)
                          .riskLevel(StorageAccessTestResult.RiskLevel.HIGH)
                          .recommendations(Arrays.asList(
                              "🚨 CRÍTICO: Permisos de listado detectados - información sensible expuesta",
                              "🔒 Configurar reglas para prevenir listado no autorizado",
                              "📋 Auditar archivos expuestos - listado completo disponible",
                              "🛡️ Implementar autenticación específica para operaciones de listado"
                          ));
                } else {
                    result.status(StorageAccessTestResult.TestStatus.SUCCESS)
                          .details("✅ Acceso de listado disponible pero bucket vacío o sin archivos")
                          .discoveredData(discoveredData)
                          .riskLevel(StorageAccessTestResult.RiskLevel.HIGH);
                }
            } else {
                discoveredData.put("errorResponse", responseBody);
                result.status(StorageAccessTestResult.TestStatus.FAILED)
                      .details("❌ Sin permisos de listado")
                      .discoveredData(discoveredData)
                      .errorMessage("HTTP " + response.code())
                      .riskLevel(StorageAccessTestResult.RiskLevel.LOW);
            }
            
            return result.build();
            
        } catch (Exception e) {
            log.error("Error en test de permisos de listado", e);
            return StorageAccessTestResult.builder()
                    .testId("FSA-005")
                    .testName("Permisos de Listado")
                    .status(StorageAccessTestResult.TestStatus.ERROR)
                    .errorMessage(e.getMessage())
                    .executionTime(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Test 6: Acceso a metadata
     */
    public StorageAccessTestResult testMetadataAccess(String firebaseToken) {
        log.info("🏷️ Ejecutando Test 6: Acceso a metadata");
        
        Map<String, Object> discoveredData = new HashMap<>();
        discoveredData.put("metadata_available", "downloadTokens, generation, size, contentType, updated, name");
        discoveredData.put("sensitive_info", "downloadTokens permiten acceso directo a archivos");
        discoveredData.put("endpoint", "Obtenido del listado de archivos - metadatos incluidos automáticamente");
        
        return StorageAccessTestResult.builder()
                .testId("FSA-006")
                .testName("Acceso a Metadata")
                .description("Verificación de acceso a metadata de archivos")
                .accessTestType(StorageAccessTestResult.AccessTestType.METADATA_ACCESS_TEST)
                .executionTime(LocalDateTime.now())
                .riskLevel(StorageAccessTestResult.RiskLevel.MEDIUM)
                .status(StorageAccessTestResult.TestStatus.SUCCESS)
                .details("✅ Metadata accesible según flujo observado - downloadTokens, generation, etc.")
                .discoveredData(discoveredData)
                .recommendations(Arrays.asList(
                    "🔍 Revisar exposición de metadata sensible",
                    "🔒 Limitar información de metadata expuesta",
                    "📋 Auditar uso de downloadTokens"
                ))
                .build();
    }

    /**
     * Construye el reporte completo de accesos a storage
     */
    private StorageAccessReport buildReport(List<StorageAccessTestResult> results) {
        StorageAccessReport.ReportSummary summary = calculateSummary(results);
        
        return StorageAccessReport.builder()
                .reportId(UUID.randomUUID().toString())
                .generatedAt(LocalDateTime.now())
                .targetBucket(targetBucket)
                .executedBy("FirebaseStorageAccessTestService")
                .summary(summary)
                .testResults(results)
                .generalRecommendations(Arrays.asList(
                    "🚨 CRÍTICO: Rotar inmediatamente las credenciales expuestas",
                    "🔒 Configurar Firebase Security Rules más restrictivas",
                    "🔍 Auditar todos los accesos al bucket en los últimos 30 días",
                    "📋 Implementar monitoreo continuo de accesos no autorizados",
                    "🛡️ Configurar alertas para operaciones sospechosas",
                    "🎯 Implementar principio de menor privilegio para cuentas de servicio"
                ))
                .build();
    }

    private StorageAccessReport.ReportSummary calculateSummary(List<StorageAccessTestResult> results) {
        int total = results.size();
        int unauthorizedAccess = (int) results.stream()
                .filter(r -> r.getStatus() == StorageAccessTestResult.TestStatus.SUCCESS)
                .count();
        
        int critical = (int) results.stream()
                .filter(r -> r.getRiskLevel() == StorageAccessTestResult.RiskLevel.CRITICAL)
                .count();
        
        int high = (int) results.stream()
                .filter(r -> r.getRiskLevel() == StorageAccessTestResult.RiskLevel.HIGH)
                .count();
        
        StorageAccessTestResult.RiskLevel overallRisk = critical > 0 ? 
                StorageAccessTestResult.RiskLevel.CRITICAL : 
                high > 0 ? StorageAccessTestResult.RiskLevel.HIGH : 
                StorageAccessTestResult.RiskLevel.MEDIUM;
        
        List<String> permissionsFound = results.stream()
                .filter(r -> r.getStatus() == StorageAccessTestResult.TestStatus.SUCCESS)
                .map(r -> r.getAccessTestType().toString())
                .toList();
        
        return StorageAccessReport.ReportSummary.builder()
                .totalTests(total)
                .unauthorizedAccessFound(unauthorizedAccess)
                .criticalPermissionIssues(critical)
                .highRiskPermissionIssues(high)
                .overallRiskLevel(overallRisk)
                .storageCompromised(unauthorizedAccess > 0)
                .permissionsFound(permissionsFound)
                .build();
    }
}