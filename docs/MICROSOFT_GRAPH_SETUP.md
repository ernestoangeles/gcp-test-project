# Microsoft Graph Directory Roles Analysis

## Overview

This module provides comprehensive analysis of Azure Active Directory directory roles and user assignments, **exactly replicating the functionality of the PowerShell script**. It generates detailed reports in CSV format showing user-role matrices and statistics with the same output format and error handling.

## Features

- 🔐 **Secure Authentication** using Azure AD app registration
- 📄 **File-Based User Input** - reads users from `users.txt` file
- 👤 **User Existence Validation** - checks if each user exists before analyzing
- 📊 **Matrix-Style CSV Export** - exactly like PowerShell script output  
- 📈 **Detailed Statistics** - users found/not found/errors breakdown
- 🚨 **Error Handling** - distinguishes between not found and other errors
- 📝 **Multiple Output Files** - main CSV + usuarios_no_encontrados.txt
- 🛡️ **Comprehensive Logging** for debugging and audit purposes

## Key Improvements Over Original

✅ **Exact PowerShell Script Replication**:
- Same CSV structure with role columns (✓ for assigned, empty for not assigned)
- Same statistics output with emojis and Spanish text  
- Same file naming convention: `directory_roles_report_YYYYMMDD_HHMMSS.csv`
- Same handling of non-existent users marked as "No existe"

✅ **Enhanced User Management**:
- Reads users from `users.txt` file (created automatically with example)
- Validates each user exists before querying roles
- Handles errors gracefully with clear status reporting

✅ **PowerShell-Style Output**:
```
=== ESTADÍSTICAS ===
👥 Total usuarios procesados: 5
✅ Usuarios existentes: 3  
❌ Usuarios no encontrados: 1
⚠️ Usuarios con error: 1
🔐 Roles únicos encontrados: 8

=== RESUMEN FINAL ===
✅ Archivo CSV principal: reports/microsoft-graph/directory_roles_report_20251105_161847.csv
📊 Registros en el reporte: 5
📁 Ubicación: D:\DATA\sandbox\gcp-test-project

🎯 Proceso completado exitosamente.
💡 Tip: Los usuarios 'No existe' aparecen claramente marcados en el CSV
```

## Prerequisites

### Azure AD App Registration

1. **Register an Application** in Azure AD:
   - Go to Azure Portal → Azure Active Directory → App registrations
   - Click "New registration"
   - Name: "Microsoft Graph Directory Roles Analyzer"
   - Supported account types: "Accounts in this organizational directory only"

2. **Configure API Permissions**:
   - Go to "API permissions" → "Add a permission"
   - Select "Microsoft Graph" → "Application permissions"
   - Add these permissions:
     - `User.Read.All`
     - `Directory.Read.All`
     - `RoleManagement.Read.Directory`
   - Click "Grant admin consent for [your tenant]"

3. **Create Client Secret**:
   - Go to "Certificates & secrets" → "New client secret"
   - Description: "Directory Roles Analysis Secret"
   - Copy the **Value** (not the Secret ID)

4. **Get Required IDs**:
   - **Tenant ID**: Overview tab → Directory (tenant) ID
   - **Client ID**: Overview tab → Application (client) ID

## Configuration

Update the `src/main/resources/application.yaml` file:

```yaml
microsoft:
  graph:
    tenant-id: "your-actual-tenant-id-here"
    client-id: "your-actual-client-id-here"
    client-secret: "your-actual-client-secret-here"
```

## Usage

### Command Line

Run the Microsoft Graph analysis mode:

```bash
./gradlew bootRun --args="--microsoft-graph-test"
```

### Expected Output

```
=== MICROSOFT GRAPH DIRECTORY ROLES ANALYSIS ===
🔐 Authenticating with Microsoft Graph...
✅ Authentication successful
📊 Analyzing directory roles and user assignments...

=== MICROSOFT GRAPH DIRECTORY ROLES ANALYSIS SUMMARY ===
Analysis Timestamp: 2025-11-05 16:04:27

--- USER STATISTICS ---
Total Users: 150
Active Users: 142
Inactive Users: 8

--- ROLE STATISTICS ---
Total Directory Roles: 25

--- TOP 10 ROLES BY ASSIGNMENT COUNT ---
- Global Reader: 45 users
- User Administrator: 12 users
- Application Administrator: 8 users
...

--- DEPARTMENT BREAKDOWN ---
- Engineering: 65 users
- Sales: 28 users
- Marketing: 22 users
...

--- REPORTS ---
CSV Report Generated: reports/microsoft-graph/microsoft_graph_roles_20251105_160427.csv
```

## Output Files

### CSV Report Structure

The generated CSV contains the following columns:

| Column | Description |
|--------|-------------|
| User ID | Azure AD user ID (GUID) |
| Display Name | User's display name |
| User Principal Name | User's UPN (email) |
| Department | User's department |
| Job Title | User's job title |
| Account Enabled | true/false for account status |
| Assigned Roles | Semicolon-separated list of role names |
| Role Count | Number of roles assigned to user |

### Report Location

Reports are saved to: `reports/microsoft-graph/microsoft_graph_roles_YYYYMMDD_HHMMSS.csv`

## Security Considerations

1. **Client Secret Protection**: Store the client secret securely, never commit it to version control
2. **Least Privilege**: The app uses read-only permissions for security
3. **Audit Trail**: All API calls are logged for audit purposes
4. **Token Management**: Access tokens are automatically managed and refreshed

## Troubleshooting

### Common Errors

1. **"Specified tenant identifier is neither a valid DNS name"**
   - Verify the tenant ID in `application.yaml`
   - Ensure it's the Directory (tenant) ID from Azure AD

2. **"Insufficient privileges to complete the operation"**
   - Verify API permissions are granted
   - Ensure admin consent was provided
   - Check that the required permissions are present

3. **"AADSTS7000215: Invalid client secret"**
   - Regenerate the client secret in Azure AD
   - Update `application.yaml` with the new secret value

4. **"AADSTS700016: Application not found"**
   - Verify the client ID in `application.yaml`
   - Ensure the app registration exists in the correct tenant

### Debugging

Enable debug logging by adding to `application.yaml`:

```yaml
logging:
  level:
    com.gcptest.microsoftgraph: DEBUG
    com.microsoft.aad.msal4j: DEBUG
```

## API Rate Limits

Microsoft Graph has rate limits:
- 10,000 requests per 10 minutes per application
- The tool respects these limits with appropriate error handling

## Data Privacy

- The tool only reads user and role information
- No personal data is modified or stored permanently
- CSV reports contain organizational data - handle according to your privacy policies

## PowerShell Script Reference

The Java implementation replicates the functionality of this PowerShell script:

```powershell
# Script para obtener Directory Roles - Versión con manejo de usuarios inexistentes
# Autor: Script generado para Ernesto-Angeles_inretai
# Fecha: 2025-11-05

# Función para obtener roles de directorio de un usuario
function Get-UserDirectoryRoles {
    param([string]$UserId)
    try {
        # Primero verificar si el usuario existe
        $user = Get-MgUser -UserId $UserId -ErrorAction Stop
        
        $userMemberships = Get-MgUserMemberOf -UserId $UserId -ErrorAction Stop
        $directoryRoles = $userMemberships | 
            Where-Object {$_.AdditionalProperties['@odata.type'] -eq "#microsoft.graph.directoryRole"} |
            ForEach-Object {
                $role = Get-MgDirectoryRole -DirectoryRoleId $_.Id
                $role.DisplayName
            }
        return @{
            Status = "Existe"
            Roles = $directoryRoles
        }
    }
    catch {
        if ($_.Exception.Message -like "*does not exist*" -or $_.Exception.Message -like "*ResourceNotFound*") {
            Write-Host "Usuario no encontrado: $UserId" -ForegroundColor Yellow
            return @{
                Status = "No existe"
                Roles = @()
            }
        } else {
            Write-Warning "Error al consultar usuario $UserId : $($_.Exception.Message)"
            return @{
                Status = "Error"
                Roles = @()
            }
        }
    }
}

# Conectar a Microsoft Graph
Write-Host "Conectando a Microsoft Graph..." -ForegroundColor Yellow
Connect-MgGraph -Scopes "User.Read.All", "Directory.Read.All"

# Leer usuarios desde archivo
$userFile = "users.txt"

if (Test-Path $userFile) {
    $userList = Get-Content $userFile | Where-Object {$_.Trim() -ne ""} | ForEach-Object {$_.Trim()}
    Write-Host "Usuarios cargados desde $userFile : $($userList.Count)" -ForegroundColor Green
} else {
    Write-Host "Archivo $userFile no encontrado. Usando lista por defecto..." -ForegroundColor Yellow
    $userList = @("lesly.villanueva@oechsle.pe")
    $userList | Out-File -FilePath $userFile -Encoding UTF8
    Write-Host "Se creó $userFile con usuarios de ejemplo." -ForegroundColor Cyan
}

# Obtener todos los roles únicos (solo de usuarios que existen)
Write-Host "Analizando roles..." -ForegroundColor Yellow
$allRoles = @()
$userStatusMap = @{}

foreach ($user in $userList) {
    $result = Get-UserDirectoryRoles -UserId $user
    $userStatusMap[$user] = $result
    
    if ($result.Status -eq "Existe") {
        $allRoles += $result.Roles
    }
}

$uniqueRoles = $allRoles | Sort-Object | Get-Unique

# Estadísticas
$existingUsers = ($userStatusMap.Values | Where-Object {$_.Status -eq "Existe"}).Count
$nonExistingUsers = ($userStatusMap.Values | Where-Object {$_.Status -eq "No existe"}).Count
$errorUsers = ($userStatusMap.Values | Where-Object {$_.Status -eq "Error"}).Count

Write-Host "`n=== ESTADÍSTICAS ===" -ForegroundColor Cyan
Write-Host "👥 Total usuarios procesados: $($userList.Count)" -ForegroundColor White
Write-Host "✅ Usuarios existentes: $existingUsers" -ForegroundColor Green
Write-Host "❌ Usuarios no encontrados: $nonExistingUsers" -ForegroundColor Yellow
Write-Host "⚠️  Usuarios con error: $errorUsers" -ForegroundColor Red
Write-Host "🔐 Roles únicos encontrados: $($uniqueRoles.Count)" -ForegroundColor Cyan

if ($uniqueRoles.Count -eq 0) {
    Write-Host "No se encontraron Directory Roles en ningún usuario." -ForegroundColor Red
    $uniqueRoles = @("Sin_Roles_Encontrados")
}

# Crear el reporte
Write-Host "`nGenerando reporte..." -ForegroundColor Yellow
$report = foreach ($user in $userList) {
    $userResult = $userStatusMap[$user]
    
    # Crear objeto con el usuario y su estatus
    $userReport = [PSCustomObject]@{
        Usuario = $user
        Estatus = $userResult.Status
        Total_Roles = if ($userResult.Status -eq "Existe") { $userResult.Roles.Count } else { 0 }
    }
    
    # Agregar columna para cada rol único
    foreach ($role in $uniqueRoles) {
        $columnName = $role.Replace(" ", "_").Replace(".", "_")
        
        if ($userResult.Status -eq "No existe") {
            $userReport | Add-Member -NotePropertyName $columnName -NotePropertyValue "No existe"
        } elseif ($userResult.Status -eq "Error") {
            $userReport | Add-Member -NotePropertyName $columnName -NotePropertyValue "Error"
        } elseif ($userResult.Roles -contains $role) {
            $userReport | Add-Member -NotePropertyName $columnName -NotePropertyValue "✓"
        } else {
            $userReport | Add-Member -NotePropertyName $columnName -NotePropertyValue ""
        }
    }
    
    $userReport
}

# Generar archivo CSV
$timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$csvFileName = "directory_roles_report_$timestamp.csv"

$report | Export-Csv -Path $csvFileName -NoTypeInformation -Encoding UTF8

# Generar archivo de resumen de usuarios no encontrados
if ($nonExistingUsers -gt 0) {
    $notFoundUsers = $userList | Where-Object { $userStatusMap[$_].Status -eq "No existe" }
    $notFoundFile = "usuarios_no_encontrados_$timestamp.txt"
    $notFoundUsers | Out-File -FilePath $notFoundFile -Encoding UTF8
    Write-Host "📄 Lista de usuarios no encontrados: $notFoundFile" -ForegroundColor Yellow
}

# Resumen final
Write-Host "`n=== RESUMEN FINAL ===" -ForegroundColor Green
Write-Host "✅ Archivo CSV principal: $csvFileName" -ForegroundColor Green
Write-Host "📊 Registros en el reporte: $($report.Count)" -ForegroundColor Cyan
if ($nonExistingUsers -gt 0) {
    Write-Host "📄 Archivo de usuarios no encontrados: usuarios_no_encontrados_$timestamp.txt" -ForegroundColor Yellow
}
Write-Host "📁 Ubicación: $(Get-Location)" -ForegroundColor Gray

# Preview del reporte (solo primeros 10 registros)
Write-Host "`n=== PREVIEW (primeros 10 registros) ===" -ForegroundColor Green
$report | Select-Object -First 10 | Format-Table -AutoSize

Write-Host "`n🎯 Proceso completado exitosamente." -ForegroundColor Green
Write-Host "💡 Tip: Los usuarios 'No existe' aparecen claramente marcados en el CSV" -ForegroundColor Cyan
```

### Script Comparison

| Feature | PowerShell Script | Java Implementation |
|---------|------------------|-------------------|
| User Input | `users.txt` file | ✅ `users.txt` file |
| User Validation | Checks if user exists | ✅ Same validation |
| Error Handling | "Existe", "No existe", "Error" | ✅ Same status types |
| CSV Matrix Format | Role columns with ✓ | ✅ Identical format |
| Output Files | CSV + usuarios_no_encontrados.txt | ✅ Same files |
| Statistics | Emoji + Spanish text | ✅ Same format |
| Preview Display | First 10 records | ✅ Same preview |