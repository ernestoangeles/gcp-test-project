package com.gcptest.microsoftgraph.service;

import com.gcptest.microsoftgraph.dto.DirectoryRoleReport;
import com.gcptest.microsoftgraph.dto.GraphAnalysisReport;
import com.gcptest.microsoftgraph.dto.UserRole;
import com.gcptest.microsoftgraph.dto.UserStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Service
@Slf4j
@ConditionalOnProperty(name = "microsoft.graph.enabled", havingValue = "true", matchIfMissing = false)
public class ReportService {
    
    private static final String REPORTS_DIR = "reports/microsoft-graph/";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    /**
     * Generates a comprehensive analysis report from user roles data
     * @param userRoles List of UserRole objects
     * @return GraphAnalysisReport with statistics and analysis
     */
    public GraphAnalysisReport generateAnalysisReport(List<UserRole> userRoles) {
        log.info("Generating comprehensive analysis report for {} users", userRoles.size());
        
        LocalDateTime analysisTime = LocalDateTime.now();
        
        // Calculate user statistics
        int totalUsers = userRoles.size();
        int activeUsers = (int) userRoles.stream().filter(UserRole::isAccountEnabled).count();
        int inactiveUsers = totalUsers - activeUsers;
        
        // Calculate department statistics
        Map<String, Integer> departmentStats = userRoles.stream()
                .filter(user -> user.getDepartment() != null && !user.getDepartment().isEmpty())
                .collect(Collectors.groupingBy(
                        UserRole::getDepartment,
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));
        
        // Collect all unique roles
        Set<String> uniqueRoles = userRoles.stream()
                .flatMap(user -> user.getAssignedRoles().stream())
                .map(UserRole.DirectoryRole::getRoleName)
                .collect(Collectors.toSet());
        
        // Calculate role distribution
        Map<String, Integer> roleDistribution = userRoles.stream()
                .flatMap(user -> user.getAssignedRoles().stream())
                .collect(Collectors.groupingBy(
                        UserRole.DirectoryRole::getRoleName,
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));
        
        // Generate role reports
        List<DirectoryRoleReport> roleReports = generateRoleReports(userRoles, uniqueRoles);
        
        // Generate CSV report
        String csvPath = generateCsvReport(userRoles, analysisTime);
        
        return new GraphAnalysisReport(
                analysisTime,
                totalUsers,
                activeUsers,
                inactiveUsers,
                uniqueRoles.size(),
                roleReports,
                userRoles,
                departmentStats,
                roleDistribution,
                csvPath
        );
    }
    
    /**
     * Generates detailed reports for each directory role
     * @param userRoles List of UserRole objects
     * @param uniqueRoles Set of unique role names
     * @return List of DirectoryRoleReport objects
     */
    private List<DirectoryRoleReport> generateRoleReports(List<UserRole> userRoles, Set<String> uniqueRoles) {
        List<DirectoryRoleReport> roleReports = new ArrayList<>();
        
        for (String roleName : uniqueRoles) {
            List<String> assignedUsers = userRoles.stream()
                    .filter(user -> user.getAssignedRoles().stream()
                            .anyMatch(role -> roleName.equals(role.getRoleName())))
                    .map(UserRole::getDisplayName)
                    .collect(Collectors.toList());
            
            // Department breakdown for this role
            Map<String, Integer> departmentBreakdown = userRoles.stream()
                    .filter(user -> user.getAssignedRoles().stream()
                            .anyMatch(role -> roleName.equals(role.getRoleName())))
                    .filter(user -> user.getDepartment() != null && !user.getDepartment().isEmpty())
                    .collect(Collectors.groupingBy(
                            UserRole::getDepartment,
                            Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                    ));
            
            // Get role description (assuming all instances have same description)
            String roleDescription = userRoles.stream()
                    .flatMap(user -> user.getAssignedRoles().stream())
                    .filter(role -> roleName.equals(role.getRoleName()))
                    .map(UserRole.DirectoryRole::getRoleDescription)
                    .findFirst()
                    .orElse("");
            
            DirectoryRoleReport roleReport = new DirectoryRoleReport(
                    roleName,
                    roleDescription,
                    assignedUsers.size(),
                    assignedUsers.size(), // All assumed to be direct for now
                    0, // Group assignments not implemented yet
                    assignedUsers,
                    departmentBreakdown
            );
            
            roleReports.add(roleReport);
        }
        
        return roleReports.stream()
                .sorted(Comparator.comparing(DirectoryRoleReport::getTotalAssignments).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * Generates CSV report with user-role matrix
     * @param userRoles List of UserRole objects
     * @param analysisTime Timestamp for the report
     * @return Path to the generated CSV file
     */
    public String generateCsvReport(List<UserRole> userRoles, LocalDateTime analysisTime) {
        String timestamp = analysisTime.format(TIMESTAMP_FORMAT);
        String fileName = String.format("%smicrosoft_graph_roles_%s.csv", REPORTS_DIR, timestamp);
        
        try {
            // Create reports directory if it doesn't exist
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(REPORTS_DIR));
            
            try (FileWriter writer = new FileWriter(fileName, StandardCharsets.UTF_8);
                 CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
                
                // Write header
                csvPrinter.printRecord("User ID", "Display Name", "User Principal Name", 
                                     "Department", "Job Title", "Account Enabled", "Assigned Roles", "Role Count");
                
                // Write user data
                for (UserRole user : userRoles) {
                    String rolesString = user.getAssignedRoles().stream()
                            .map(UserRole.DirectoryRole::getRoleName)
                            .collect(Collectors.joining("; "));
                    
                    csvPrinter.printRecord(
                            user.getUserId(),
                            user.getDisplayName(),
                            user.getUserPrincipalName(),
                            user.getDepartment() != null ? user.getDepartment() : "",
                            user.getJobTitle() != null ? user.getJobTitle() : "",
                            user.isAccountEnabled(),
                            rolesString,
                            user.getAssignedRoles().size()
                    );
                }
                
                log.info("CSV report generated successfully: {}", fileName);
            }
        } catch (IOException e) {
            log.error("Failed to generate CSV report: {}", e.getMessage());
            return null;
        }
        
        return fileName;
    }
    
    /**
     * Prints a summary of the analysis to the console
     * @param report GraphAnalysisReport to summarize
     */
    public void printAnalysisSummary(GraphAnalysisReport report) {
        System.out.println("\n=== MICROSOFT GRAPH DIRECTORY ROLES ANALYSIS SUMMARY ===");
        System.out.println("Analysis Timestamp: " + report.getAnalysisTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("\n--- USER STATISTICS ---");
        System.out.printf("Total Users: %d%n", report.getTotalUsers());
        System.out.printf("Active Users: %d%n", report.getActiveUsers());
        System.out.printf("Inactive Users: %d%n", report.getInactiveUsers());
        
        System.out.println("\n--- ROLE STATISTICS ---");
        System.out.printf("Total Directory Roles: %d%n", report.getTotalRoles());
        
        System.out.println("\n--- TOP 10 ROLES BY ASSIGNMENT COUNT ---");
        report.getRoleDistribution().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> System.out.printf("- %s: %d users%n", entry.getKey(), entry.getValue()));
        
        System.out.println("\n--- DEPARTMENT BREAKDOWN ---");
        report.getDepartmentStats().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> System.out.printf("- %s: %d users%n", entry.getKey(), entry.getValue()));
        
        if (report.getCsvReportPath() != null) {
            System.out.println("\n--- REPORTS ---");
            System.out.printf("CSV Report Generated: %s%n", report.getCsvReportPath());
        }
        
        System.out.println("\n=== ANALYSIS COMPLETE ===\n");
    }
    
    /**
     * Generates matrix-style CSV report from UserStatus list (like PowerShell script)
     * @param userStatusList List of UserStatus objects with analysis results
     * @param analysisTime Timestamp for the report
     * @return Path to the generated CSV file
     */
    public String generateMatrixCsvReport(List<UserStatus> userStatusList, LocalDateTime analysisTime) {
        String timestamp = analysisTime.format(TIMESTAMP_FORMAT);
        String fileName = String.format("%sdirectory_roles_report_%s.csv", REPORTS_DIR, timestamp);
        
        try {
            // Create reports directory if it doesn't exist
            Files.createDirectories(Paths.get(REPORTS_DIR));
            
            // Collect all unique roles from existing users
            Set<String> allRoles = userStatusList.stream()
                    .filter(us -> us.getStatus() == UserStatus.Status.EXISTS)
                    .flatMap(us -> us.getUserRole().getAssignedRoles().stream())
                    .map(UserRole.DirectoryRole::getRoleName)
                    .map(roleName -> roleName.replace(" ", "_").replace(".", "_"))
                    .collect(java.util.TreeSet::new, java.util.TreeSet::add, java.util.TreeSet::addAll);
            
            if (allRoles.isEmpty()) {
                allRoles.add("Sin_Roles_Encontrados");
            }
            
            try (FileWriter writer = new FileWriter(fileName, StandardCharsets.UTF_8);
                 CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
                
                // Create header
                List<String> headers = new ArrayList<>();
                headers.add("Usuario");
                headers.add("Estatus");
                headers.add("Total_Roles");
                headers.addAll(allRoles);
                csvPrinter.printRecord(headers);
                
                // Write user data
                for (UserStatus userStatus : userStatusList) {
                    List<Object> record = new ArrayList<>();
                    
                    if (userStatus.getStatus() == UserStatus.Status.EXISTS) {
                        UserRole userRole = userStatus.getUserRole();
                        record.add(userRole.getUserPrincipalName());
                        record.add(userStatus.getStatus().getDisplayName());
                        record.add(userRole.getAssignedRoles().size());
                        
                        // Add role columns
                        Set<String> userRoleNames = userRole.getAssignedRoles().stream()
                                .map(UserRole.DirectoryRole::getRoleName)
                                .map(roleName -> roleName.replace(" ", "_").replace(".", "_"))
                                .collect(Collectors.toSet());
                        
                        for (String role : allRoles) {
                            record.add(userRoleNames.contains(role) ? "✓" : "");
                        }
                    } else {
                        // Handle non-existing or error users
                        record.add("Unknown User"); // We don't have the original UPN for failed lookups
                        record.add(userStatus.getStatus().getDisplayName());
                        record.add(0);
                        
                        // Add status for all role columns
                        String statusValue = userStatus.getStatus() == UserStatus.Status.NOT_FOUND ? "No existe" : "Error";
                        for (String role : allRoles) {
                            record.add(statusValue);
                        }
                    }
                    
                    csvPrinter.printRecord(record);
                }
                
                log.info("Matrix CSV report generated successfully: {}", fileName);
            }
            
            // Generate usuarios_no_encontrados file if needed
            generateNotFoundUsersFile(userStatusList, timestamp);
            
        } catch (IOException e) {
            log.error("Failed to generate matrix CSV report: {}", e.getMessage());
            return null;
        }
        
        return fileName;
    }
    
    /**
     * Generates file with users that were not found
     * @param userStatusList List of UserStatus objects
     * @param timestamp Timestamp for the filename
     */
    private void generateNotFoundUsersFile(List<UserStatus> userStatusList, String timestamp) {
        List<String> notFoundUsers = userStatusList.stream()
                .filter(us -> us.getStatus() == UserStatus.Status.NOT_FOUND)
                .map(us -> "Usuario no encontrado")  // We need the original UPN here
                .collect(Collectors.toList());
        
        if (!notFoundUsers.isEmpty()) {
            String notFoundFileName = String.format("%susuarios_no_encontrados_%s.txt", REPORTS_DIR, timestamp);
            try {
                Files.write(Paths.get(notFoundFileName), notFoundUsers, StandardCharsets.UTF_8);
                log.info("📄 Lista de usuarios no encontrados: {}", notFoundFileName);
            } catch (IOException e) {
                log.error("Error creating not found users file: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Prints analysis summary with PowerShell-style statistics
     * @param userStatusList List of UserStatus objects
     * @param csvPath Path to generated CSV file
     * @param analysisTime Analysis timestamp
     */
    public void printUserStatusAnalysisSummary(List<UserStatus> userStatusList, String csvPath, LocalDateTime analysisTime) {
        // Calculate statistics
        long existingUsers = userStatusList.stream().filter(s -> s.getStatus() == UserStatus.Status.EXISTS).count();
        long notFoundUsers = userStatusList.stream().filter(s -> s.getStatus() == UserStatus.Status.NOT_FOUND).count();
        long errorUsers = userStatusList.stream().filter(s -> s.getStatus() == UserStatus.Status.ERROR).count();
        
        // Collect unique roles
        Set<String> uniqueRoles = userStatusList.stream()
                .filter(us -> us.getStatus() == UserStatus.Status.EXISTS)
                .flatMap(us -> us.getUserRole().getAssignedRoles().stream())
                .map(UserRole.DirectoryRole::getRoleName)
                .collect(Collectors.toSet());
        
        System.out.println("\n=== ESTADÍSTICAS ===");
        System.out.printf("👥 Total usuarios procesados: %d%n", userStatusList.size());
        System.out.printf("✅ Usuarios existentes: %d%n", existingUsers);
        System.out.printf("❌ Usuarios no encontrados: %d%n", notFoundUsers);
        System.out.printf("⚠️ Usuarios con error: %d%n", errorUsers);
        System.out.printf("🔐 Roles únicos encontrados: %d%n", uniqueRoles.size());
        
        if (uniqueRoles.isEmpty()) {
            System.out.println("No se encontraron Directory Roles en ningún usuario.");
        }
        
        System.out.println("\n=== RESUMEN FINAL ===");
        System.out.printf("✅ Archivo CSV principal: %s%n", csvPath);
        System.out.printf("📊 Registros en el reporte: %d%n", userStatusList.size());
        System.out.printf("📁 Ubicación: %s%n", System.getProperty("user.dir"));
        
        System.out.println("\n=== PREVIEW (primeros 10 registros) ===");
        userStatusList.stream().limit(10).forEach(userStatus -> {
            if (userStatus.getStatus() == UserStatus.Status.EXISTS) {
                UserRole user = userStatus.getUserRole();
                System.out.printf("✅ %s: %d roles%n", 
                    user.getUserPrincipalName(), 
                    user.getAssignedRoles().size());
            } else {
                System.out.printf("%s: %s%n", 
                    userStatus.getStatus() == UserStatus.Status.NOT_FOUND ? "❌" : "⚠️",
                    userStatus.getStatus().getDisplayName());
            }
        });
        
        System.out.println("\n🎯 Proceso completado exitosamente.");
        System.out.println("💡 Tip: Los usuarios 'No existe' aparecen claramente marcados en el CSV");
    }
}