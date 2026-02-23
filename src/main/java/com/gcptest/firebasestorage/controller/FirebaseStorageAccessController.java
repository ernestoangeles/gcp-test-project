package com.gcptest.firebasestorage.controller;

import com.gcptest.firebasestorage.dto.StorageAccessTestResult;
import com.gcptest.firebasestorage.dto.StorageAccessReport;
import com.gcptest.firebasestorage.service.FirebaseStorageAccessTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/firebase-storage-access")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "firebase.storage-access-test.enabled", havingValue = "true", matchIfMissing = false)
public class FirebaseStorageAccessController {

    private final FirebaseStorageAccessTestService storageAccessTestService;

    /**
     * Ejecuta todas las pruebas de acceso a Firebase Storage
     */
    @PostMapping("/test/all")
    public ResponseEntity<StorageAccessReport> runAllTests() {
        log.info("🔍 Iniciando análisis completo de permisos de Firebase Storage");
        try {
            StorageAccessReport report = storageAccessTestService.runAllStorageAccessTests();
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Error ejecutando análisis de permisos de storage", e);
            StorageAccessReport errorReport = StorageAccessReport.builder()
                    .reportId("ERROR")
                    .generatedAt(LocalDateTime.now())
                    .build();
            return ResponseEntity.internalServerError().body(errorReport);
        }
    }

    /**
     * Ejecuta una prueba específica de permisos
     */
    @PostMapping("/test/{testId}")
    public ResponseEntity<StorageAccessTestResult> runSpecificTest(@PathVariable String testId) {
        log.info("🔍 Ejecutando test específico: {}", testId);
        
        try {
            StorageAccessTestResult result;
            
            switch (testId.toUpperCase()) {
                case "FSA-001":
                case "AUTH":
                case "AUTHENTICATION":
                    result = storageAccessTestService.testFirebaseAuthentication();
                    break;
                    
                default:
                    result = StorageAccessTestResult.builder()
                            .testId(testId)
                            .testName("Test No Encontrado")
                            .status(StorageAccessTestResult.TestStatus.ERROR)
                            .errorMessage("Test ID no válido: " + testId)
                            .executionTime(LocalDateTime.now())
                            .build();
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error ejecutando test específico: {}", testId, e);
            StorageAccessTestResult errorResult = StorageAccessTestResult.builder()
                    .testId(testId)
                    .status(StorageAccessTestResult.TestStatus.ERROR)
                    .errorMessage(e.getMessage())
                    .executionTime(LocalDateTime.now())
                    .build();
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    /**
     * Obtiene información sobre las pruebas disponibles
     */
    @GetMapping("/tests/info")
    public ResponseEntity<Map<String, Object>> getTestsInfo() {
        Map<String, Object> testsInfo = Map.of(
            "availableTests", Map.of(
                "FSA-001", "Autenticación Firebase",
                "FSA-002", "Account Lookup",
                "FSA-003", "Permisos de Lectura",
                "FSA-004", "Permisos de Escritura",
                "FSA-005", "Permisos de Listado",
                "FSA-006", "Acceso a Metadata"
            ),
            "endpoints", Map.of(
                "runAll", "POST /api/firebase-storage-access/test/all",
                "runSpecific", "POST /api/firebase-storage-access/test/{testId}",
                "testInfo", "GET /api/firebase-storage-access/tests/info"
            ),
            "description", "API para testing de permisos de acceso a Firebase Storage",
            "targetBucket", "pe-gcp-customercare-02.appspot.com"
        );
        
        return ResponseEntity.ok(testsInfo);
    }

    /**
     * Endpoint de salud para verificar el servicio
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "FirebaseStorageAccessTestService",
            "timestamp", LocalDateTime.now().toString()
        ));
    }
}