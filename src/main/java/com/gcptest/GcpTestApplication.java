package com.gcptest;

import com.gcptest.service.BigQueryService;
import com.gcptest.firebasestorage.service.FirebaseStorageAccessTestService;
import com.gcptest.firebasestorage.dto.StorageAccessReport;
import com.gcptest.firebasestorage.dto.StorageAccessTestResult;
import com.gcptest.microsoftgraph.service.AuthenticationService;
import com.gcptest.microsoftgraph.service.GraphService;
import com.gcptest.microsoftgraph.service.ReportService;
import com.gcptest.microsoftgraph.dto.GraphAnalysisReport;
import com.gcptest.microsoftgraph.dto.UserRole;
import com.gcptest.microsoftgraph.dto.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@SpringBootApplication
@RequiredArgsConstructor
public class GcpTestApplication implements CommandLineRunner {

    private final BigQueryService bigQueryService;

    public static void main(String[] args) {
        boolean consoleMode = false;
        boolean storageAccessMode = false;
        boolean microsoftGraphMode = false;
        String tipo = "";
        
        for (String arg : args) {
            if (arg.equalsIgnoreCase("--console")) {
                consoleMode = true;
            }
            if (arg.equalsIgnoreCase("--firebase-storage-test")) {
                storageAccessMode = true;
            }
            if (arg.equalsIgnoreCase("--microsoft-graph-test")) {
                microsoftGraphMode = true;
            }
            if (arg.equalsIgnoreCase("oechsle")) {
                tipo = "oechsle";
            }
            if (arg.equalsIgnoreCase("promart")) {
                tipo = "promart";
            }
        }
        
        if (!tipo.isEmpty()) {
            System.setProperty("gcp.tipo", tipo);
        }

        if (microsoftGraphMode) {
            System.out.println("=== MICROSOFT GRAPH DIRECTORY ROLES ANALYSIS ===");
            try {
                org.springframework.context.annotation.AnnotationConfigApplicationContext ctx =
                        new org.springframework.context.annotation.AnnotationConfigApplicationContext();
                
                // Load properties from application.yaml
                org.springframework.core.io.Resource resource = new org.springframework.core.io.ClassPathResource("application.yaml");
                org.springframework.beans.factory.config.YamlPropertiesFactoryBean yamlFactory = new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
                yamlFactory.setResources(resource);
                java.util.Properties props = yamlFactory.getObject();
                if (props != null) {
                    org.springframework.core.env.ConfigurableEnvironment env = ctx.getEnvironment();
                    org.springframework.core.env.PropertiesPropertySource yamlSource = new org.springframework.core.env.PropertiesPropertySource("yamlProperties", props);
                    env.getPropertySources().addFirst(yamlSource);
                }
                
                // Register Microsoft Graph beans
                ctx.register(AuthenticationService.class, GraphService.class, ReportService.class);
                ctx.refresh();
                
                // Get service beans
                AuthenticationService authService = ctx.getBean(AuthenticationService.class);
                GraphService graphService = ctx.getBean(GraphService.class);
                ReportService reportService = ctx.getBean(ReportService.class);
                
                // Validate configuration
                if (!authService.validateConfiguration()) {
                    System.err.println("❌ Microsoft Graph configuration is incomplete.");
                    System.err.println("Please update application.yaml with your Azure AD app registration details:");
                    System.err.println("  - microsoft.graph.tenant-id: Your Azure AD tenant ID");
                    System.err.println("  - microsoft.graph.client-id: Your app registration client ID");
                    System.err.println("  - microsoft.graph.client-secret: Your app registration client secret");
                    ctx.close();
                    return;
                }
                
                System.out.println("🔐 Authenticating with Microsoft Graph...");
                
                // Test authentication
                String token = authService.getAccessToken();
                if (token != null) {
                    System.out.println("✅ Authentication successful");
                    
                    // Perform analysis
                    System.out.println("📊 Analyzing directory roles from users.txt file...");
                    List<UserStatus> userStatusList = graphService.buildUserRoleAnalysisFromFile();
                    
                    // Generate matrix-style CSV report (like PowerShell script)
                    LocalDateTime analysisTime = LocalDateTime.now();
                    String csvPath = reportService.generateMatrixCsvReport(userStatusList, analysisTime);
                    
                    // Print PowerShell-style summary
                    reportService.printUserStatusAnalysisSummary(userStatusList, csvPath, analysisTime);
                    
                } else {
                    System.err.println("❌ Authentication failed");
                }
                
                ctx.close();
            } catch (Exception e) {
                System.err.println("❌ Error during Microsoft Graph analysis: " + e.getMessage());
                e.printStackTrace();
            }
            System.out.println("🏁 === Microsoft Graph Analysis Completed ===");
            return;
        }

        if (storageAccessMode) {
            System.out.println("� === Firebase Storage Access Testing Mode ===");
            try {
                org.springframework.context.annotation.AnnotationConfigApplicationContext ctx =
                        new org.springframework.context.annotation.AnnotationConfigApplicationContext();
                // Cargar properties de application.yaml
                org.springframework.core.io.Resource resource = new org.springframework.core.io.ClassPathResource("application.yaml");
                org.springframework.beans.factory.config.YamlPropertiesFactoryBean yamlFactory = new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
                yamlFactory.setResources(resource);
                java.util.Properties props = yamlFactory.getObject();
                if (props != null) {
                    org.springframework.core.env.ConfigurableEnvironment env = ctx.getEnvironment();
                    org.springframework.core.env.PropertiesPropertySource yamlSource = new org.springframework.core.env.PropertiesPropertySource("yamlProperties", props);
                    env.getPropertySources().addFirst(yamlSource);
                }
                // Registrar beans para storage access testing
                ctx.register(com.gcptest.firebasestorage.service.FirebaseStorageAccessTestService.class);
                ctx.refresh();
                
                FirebaseStorageAccessTestService storageService = ctx.getBean(FirebaseStorageAccessTestService.class);
                System.out.println("� Iniciando análisis de permisos de Firebase Storage...");
                var report = storageService.runAllStorageAccessTests();
                
                System.out.println("\n📊 === REPORTE DE PERMISOS DE STORAGE ===");
                System.out.println("🎯 Bucket objetivo: " + report.getTargetBucket());
                System.out.println("📅 Generado: " + report.getGeneratedAt());
                System.out.println("🔢 Total tests: " + report.getSummary().getTotalTests());
                System.out.println("⚠️  Accesos no autorizados: " + report.getSummary().getUnauthorizedAccessFound());
                System.out.println("🚨 Issues críticos: " + report.getSummary().getCriticalPermissionIssues());
                System.out.println("⚡ Issues alto riesgo: " + report.getSummary().getHighRiskPermissionIssues());
                System.out.println("🛡️  Storage comprometido: " + (report.getSummary().isStorageCompromised() ? "SÍ" : "NO"));
                System.out.println("🔑 Permisos encontrados: " + report.getSummary().getPermissionsFound());
                
                System.out.println("\n📋 === RESULTADOS DETALLADOS ===");
                for (var result : report.getTestResults()) {
                    System.out.println(String.format("Test %s: %s - %s (%s)", 
                        result.getTestId(), 
                        result.getTestName(), 
                        result.getStatus(), 
                        result.getRiskLevel()));
                    System.out.println("  " + result.getDetails());
                    
                    if (result.getRecommendations() != null && !result.getRecommendations().isEmpty()) {
                        System.out.println("  📋 Recomendaciones:");
                        for (String rec : result.getRecommendations()) {
                            System.out.println("    - " + rec);
                        }
                    }
                }
                
                System.out.println("\n🛠️  === RECOMENDACIONES GENERALES ===");
                for (String recommendation : report.getGeneralRecommendations()) {
                    System.out.println("  " + recommendation);
                }
                
                // Generar archivo de reporte
                generateFileReport(report);
                
                ctx.close();
            } catch (Exception e) {
                System.err.println("❌ Error durante análisis de permisos: " + e.getMessage());
                e.printStackTrace();
            }
            System.out.println("🏁 === Análisis de Permisos de Storage Completado ===");
            return;
        }
        if (consoleMode) {
            System.out.println("=== GCP Test Application (Console Mode) ===");
            try {
                org.springframework.context.annotation.AnnotationConfigApplicationContext ctx =
                        new org.springframework.context.annotation.AnnotationConfigApplicationContext();
                // Cargar properties de application.yaml ANTES de registrar los beans
                org.springframework.core.io.Resource resource = new org.springframework.core.io.ClassPathResource("application.yaml");
                org.springframework.beans.factory.config.YamlPropertiesFactoryBean yamlFactory = new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
                yamlFactory.setResources(resource);
                java.util.Properties props = yamlFactory.getObject();
                if (props != null) {
                    org.springframework.core.env.ConfigurableEnvironment env = ctx.getEnvironment();
                    org.springframework.core.env.PropertiesPropertySource yamlSource = new org.springframework.core.env.PropertiesPropertySource("yamlProperties", props);
                    env.getPropertySources().addFirst(yamlSource);
                }
                // Registrar beans después de cargar las propiedades
                ctx.register(com.gcptest.config.GcpConfig.class, com.gcptest.service.BigQueryService.class);
                ctx.refresh();
                BigQueryService bigQueryService = ctx.getBean(BigQueryService.class);
                bigQueryService.testConnection();
                if ("oechsle".equals(tipo)) {
                    bigQueryService.extractOechsleRecords();
                } else if ("promart".equals(tipo)) {
                    bigQueryService.extractPromartRecords();
                } else {
                    System.out.println("[ERROR] Debes indicar 'oechsle' o 'promart' como argumento.");
                }
                ctx.close();
            } catch (Exception e) {
                System.err.println("Error during execution: " + e.getMessage());
                e.printStackTrace();
            }
            System.out.println("=== Application Finished ===");
        } else {
            SpringApplication.run(GcpTestApplication.class, args);
        }
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== GCP Test Application Started ===");
        
        try {
            // Test BigQuery connection and data extraction
            bigQueryService.testConnection();
            bigQueryService.extractLatest10Records();
            
        } catch (Exception e) {
            System.err.println("Error during execution: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("=== Application Finished ===");
    }
    
    private static void generateFileReport(StorageAccessReport report) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "firebase_storage_test_" + timestamp + ".txt";
            Path outputPath = Paths.get("output", fileName);
            
            StringBuilder fileContent = new StringBuilder();
            fileContent.append("=== FIREBASE STORAGE ACCESS TEST REPORT ===\n");
            fileContent.append("Fecha: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            fileContent.append("Bucket: ").append(report.getTargetBucket()).append("\n");
            fileContent.append("Storage Comprometido: ").append(report.getSummary().isStorageCompromised() ? "SÍ" : "NO").append("\n\n");
            
            // Procesar cada punto específico en orden solicitado
            for (StorageAccessTestResult result : report.getTestResults()) {
                switch (result.getTestId()) {
                    case "FSA-001":
                        fileContent.append("1. AUTENTICACIÓN AUTOMÁTICA CON CREDENCIALES EXPUESTAS\n");
                        fileContent.append("   Estado: ").append(result.getStatus().name()).append("\n");
                        fileContent.append("   Resultado: ").append(result.getDetails()).append("\n");
                        if (result.getDiscoveredData() != null) {
                            String tokenPreview = (String) result.getDiscoveredData().get("token_preview");
                            if (tokenPreview != null) {
                                fileContent.append("   Token obtenido: ").append(tokenPreview).append("\n");
                            }
                            String endpoint = (String) result.getDiscoveredData().get("endpoint");
                            if (endpoint != null) {
                                fileContent.append("   Endpoint: ").append(endpoint).append("\n");
                            }
                        }
                        fileContent.append("\n");
                        break;
                        
                    case "FSA-005":
                        fileContent.append("2. LISTADO DE ARCHIVOS DEL BUCKET\n");
                        fileContent.append("   Estado: ").append(result.getStatus().name()).append("\n");
                        fileContent.append("   Resultado: ").append(result.getDetails()).append("\n");
                        if (result.getDiscoveredData() != null) {
                            String endpoint = (String) result.getDiscoveredData().get("endpoint");
                            if (endpoint != null) {
                                fileContent.append("   Endpoint: ").append(endpoint).append("\n");
                            }
                            String fileCount = (String) result.getDiscoveredData().get("file_count");
                            if (fileCount != null) {
                                fileContent.append("   Número de archivos: ").append(fileCount).append("\n");
                            }
                            String firstFiles = (String) result.getDiscoveredData().get("first_files");
                            if (firstFiles != null) {
                                fileContent.append("   Primeros 3 archivos:\n");
                                fileContent.append("   ").append(firstFiles).append("\n");
                            }
                        }
                        fileContent.append("\n");
                        break;
                        
                    case "FSA-003":
                        fileContent.append("3. DESCARGA DE ARCHIVOS EXISTENTES (CRÍTICO)\n");
                        fileContent.append("   Estado: ").append(result.getStatus().name()).append("\n");
                        fileContent.append("   Resultado: ").append(result.getDetails()).append("\n");
                        if (result.getDiscoveredData() != null) {
                            String endpoint = (String) result.getDiscoveredData().get("endpoint");
                            if (endpoint != null) {
                                fileContent.append("   Endpoint: ").append(endpoint).append("\n");
                            }
                            String downloadedFile = (String) result.getDiscoveredData().get("downloaded_file");
                            if (downloadedFile != null) {
                                fileContent.append("   Archivo descargado: ").append(downloadedFile).append("\n");
                            }
                            String downloadUrl = (String) result.getDiscoveredData().get("download_url");
                            if (downloadUrl != null) {
                                fileContent.append("   URL de descarga: ").append(downloadUrl).append("\n");
                            }
                        }
                        fileContent.append("\n");
                        break;
                        
                    case "FSA-004":
                        fileContent.append("4. ESCRITURA DE ARCHIVOS EN EL BUCKET\n");
                        fileContent.append("   Estado: ").append(result.getStatus().name()).append("\n");
                        fileContent.append("   Resultado: ").append(result.getDetails()).append("\n");
                        if (result.getDiscoveredData() != null) {
                            String endpoint = (String) result.getDiscoveredData().get("endpoint");
                            if (endpoint != null) {
                                fileContent.append("   Endpoint: ").append(endpoint).append("\n");
                            }
                            String uploadedUrl = (String) result.getDiscoveredData().get("uploaded_url");
                            if (uploadedUrl != null) {
                                fileContent.append("   URL del archivo subido: ").append(uploadedUrl).append("\n");
                            }
                            String downloadUrl = (String) result.getDiscoveredData().get("download_url");
                            if (downloadUrl != null) {
                                fileContent.append("   URL de descarga: ").append(downloadUrl).append("\n");
                            }
                        }
                        fileContent.append("\n");
                        break;
                        
                    case "FSA-006":
                        fileContent.append("5. ACCESO A METADATA SENSIBLE\n");
                        fileContent.append("   Estado: ").append(result.getStatus().name()).append("\n");
                        fileContent.append("   Resultado: ").append(result.getDetails()).append("\n");
                        if (result.getDiscoveredData() != null) {
                            String endpoint = (String) result.getDiscoveredData().get("endpoint");
                            if (endpoint != null) {
                                fileContent.append("   Endpoint: ").append(endpoint).append("\n");
                            }
                            String metadataAvailable = (String) result.getDiscoveredData().get("metadata_available");
                            if (metadataAvailable != null) {
                                fileContent.append("   Metadata accesible: ").append(metadataAvailable).append("\n");
                            }
                        }
                        fileContent.append("\n");
                        break;
                }
            }
            
            fileContent.append("=== FIN DEL REPORTE ===\n");
            
            Files.write(outputPath, fileContent.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("📄 Reporte generado: " + outputPath.toAbsolutePath());
            
        } catch (Exception e) {
            System.err.println("❌ Error generando archivo de reporte: " + e.getMessage());
        }
    }
}
