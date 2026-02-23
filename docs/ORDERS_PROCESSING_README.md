# VTEX Orders Processing

Este módulo permite procesar pedidos de VTEX y obtener información de pagos de forma automática con procesamiento en background.

## Descripción

El sistema lee un archivo `orders.txt` que contiene una lista de orderIds (uno por línea) y para cada uno:

1. **Consulta la API de VTEX** para obtener:
   - `hostname` - Host de la orden
   - `creationDate` - Fecha de creación
   - `status` - Estado del pedido
   - `statusDescription` - Descripción del estado
   - `isCompleted` - Si el pedido está completado
   - `cancellationData.cancellationDate` - Fecha de cancelación
   - `cancellationRequests.cancellationRequestDate` - Fecha de solicitud de cancelación
   - `vtexPaymentId` - ID del pago (extraído de `paymentData.transactions.payments.id`)

2. **Consulta la API de Payment** con el `orderId` para obtener:
   - `paymentDate` - Fecha del pago
   - `status` - Estado del pago
   - `paymentMethod` - Método de pago
   - `cardIssuer` - Emisor de la tarjeta
   - `cardBrand` - Marca de la tarjeta
   - `purchaseType` - Tipo de compra
   - `trxType` - Tipo de transacción
   - `trxStatus` - Estado de la transacción
   - `trxResponse` - Respuesta de la transacción
   - `message` - Mensaje adicional

3. **Genera un archivo CSV** en `output/resultadoYYYYMMDD_HHMMSS.txt` con todos los resultados

## Configuración

### 1. Actualizar credenciales en `application.yaml`

```yaml
vtex:
  api:
    company-name: "tu-company-name"  # Nombre de tu empresa en VTEX
    app-token: "tu-token-value"      # Token de tu aplicación VTEX
    app-key: "tu-key-value"          # Key de tu aplicación VTEX

payment:
  api:
    app-id: "tu-app-id"              # App ID del API de pagos
    api-key: "tu-api-key"            # API Key del servicio de pagos
```

### 2. Crear archivo `orders.txt`

Crea un archivo `orders.txt` en la raíz del proyecto con los orderIds (uno por línea):

```
1234567890123-01
1234567890124-01
1234567890125-01
```

## Uso

### Iniciar la aplicación

```bash
# Windows
gradlew.bat bootRun

# Linux/Mac
./gradlew bootRun
```

### Procesar pedidos

**Endpoint principal (procesamiento asíncrono):**
```
GET http://localhost:8081/api/orders/process
```

El endpoint retorna **200 OK** inmediatamente y procesa los pedidos en background.

**Respuesta inmediata:**
```json
{
  "status": "PROCESSING",
  "message": "El procesamiento de pedidos ha iniciado en background",
  "filePath": "orders.txt",
  "info": "Los resultados se guardarán en output/resultadoYYYYMMDD_HHMMSS.txt"
}
```

**Con ruta personalizada:**
```
GET http://localhost:8081/api/orders/process?filePath=d:/path/to/orders.txt
```

**Health check:**
```
GET http://localhost:8081/api/orders/health
```

### Resultado del Procesamiento

Los resultados se guardan automáticamente en un archivo CSV en la carpeta `output/`:
- Nombre: `resultadoYYYYMMDD_HHMMSS.txt`
- Formato: CSV (separado por comas)
- Columnas en orden:
  1. hostname
  2. orderId
  3. creationDate
  4. CancellationDate
  5. cancellationRequestDate
  6. vtexPaymentId
  7. orderStatus
  8. orderStatusDescription
  9. orderIsCompleted
  10. paymentDate
  11. paymentStatus
  12. paymentMethod
  13. cardIssuer
  14. cardBrand
  15. purchaseType
  16. trxType
  17. trxStatus
  18. trxResponse
  19. message
  20. success
  21. errorMessage

Al final del archivo se incluye una línea con el tiempo total de procesamiento en milisegundos.

## Estructura del Proyecto

```
src/main/java/com/gcptest/orders/
├── controller/
│   └── OrderProcessingController.java    # REST controller
├── dto/
│   ├── VtexOrderResponse.java           # DTO para respuesta de VTEX
│   ├── PaymentStatusResponse.java       # DTO para respuesta de Payment API
│   ├── OrderProcessingResult.java       # Resultado del procesamiento
│   └── OrderProcessingReport.java       # Reporte completo
└── service/
    └── OrderProcessingService.java      # Lógica de negocio
```

## APIs Utilizadas

### VTEX Orders API
```bash
GET https://{companyName}.vtexcommercestable.com.br/api/oms/pvt/orders/{orderId}
Headers:
  X-VTEX-API-AppToken: {token}
  X-VTEX-API-AppKey: {key}
```

### Payment API
```bash
GET https://payment-prd.cc.cloudintercorpretail.pe/api/customer-care/v1/payments/hpsa/{orderId}/detail
```

## Manejo de Errores

El sistema maneja los siguientes escenarios de error:

- **Archivo no encontrado**: Retorna error 500 con mensaje descriptivo
- **Error en VTEX API**: Registra el error y continúa con el siguiente pedido
- **Error en Payment API**: Registra el error y continúa con el siguiente pedido
- **Payment ID no encontrado**: Registra que no hay información de pago en el resultado
- **Timeout de conexión**: Configurado a 30 segundos por defecto

Cada resultado individual indica si fue exitoso o no con un mensaje de error descriptivo. Los errores no detienen el procesamiento completo.

## Procesamiento Asíncrono

- El endpoint `/api/orders/process` retorna **200 OK inmediatamente**
- El procesamiento se ejecuta en **background** usando `@Async`
- Ideal para procesar grandes cantidades de pedidos (500+)
- No hay timeout en el endpoint
- Los resultados se guardan automáticamente en archivo CSV

## Logs

Los logs se pueden ver en la consola con el siguiente formato:

```
INFO  - Iniciando procesamiento asíncrono de pedidos desde archivo: orders.txt
INFO  - Se leyeron 500 orderIds del archivo
DEBUG - Procesando pedido: 1607480222673-01
DEBUG - Llamando a VTEX API: https://promart.vtexcommercestable.com.br/api/oms/pvt/orders/1607480222673-01
DEBUG - Respuesta VTEX para orderId 1607480222673-01: {...}
DEBUG - Llamando a Payment API: https://payment-prd.cc.cloudintercorpretail.pe/api/customer-care/v1/payments/hpsa/1607480222673-01/detail
INFO  - Pedido 1607480222673-01 procesado: EXITOSO
INFO  - Procesamiento completado - Total: 500, Exitosos: 485, Fallidos: 15, Tiempo: 145230ms
INFO  - Generando archivo CSV: output/resultado20260219_203055.txt
INFO  - Archivo CSV generado exitosamente
```

## Ejemplo con cURL / PowerShell

### PowerShell (recomendado en Windows)
```powershell
# Procesar pedidos con archivo por defecto (orders.txt)
Invoke-WebRequest -Uri http://localhost:8081/api/orders/process -UseBasicParsing | Select-Object -ExpandProperty Content

# Procesar pedidos con ruta personalizada
Invoke-WebRequest -Uri "http://localhost:8081/api/orders/process?filePath=d:/data/orders.txt" -UseBasicParsing | Select-Object -ExpandProperty Content

# Health check
Invoke-WebRequest -Uri http://localhost:8081/api/orders/health -UseBasicParsing | Select-Object -ExpandProperty Content
```

### cURL
```bash
# Procesar pedidos con archivo por defecto (orders.txt)
curl http://localhost:8081/api/orders/process

# Procesar pedidos con ruta personalizada
curl "http://localhost:8081/api/orders/process?filePath=d:/data/orders.txt"

# Health check
curl http://localhost:8081/api/orders/health
```

## Notas Importantes

1. ✅ Asegúrate de tener las credenciales correctas en `application.yaml`
2. ✅ El archivo `orders.txt` debe existir en la ruta especificada
3. ✅ Los orderIds deben estar separados por saltos de línea
4. ✅ Las líneas vacías en el archivo se ignoran automáticamente
5. ✅ **El procesamiento es asíncrono** - retorna 200 inmediatamente
6. ✅ Los errores individuales no detienen el procesamiento completo
7. ✅ Todas las respuestas y errores se registran en los logs
8. ✅ El archivo CSV se genera automáticamente en `output/`
9. ✅ Adecuado para procesar grandes volúmenes (500+ pedidos)
10. ✅ La carpeta `output/` se crea automáticamente si no existe

## Reiniciar el Servicio

Si haces cambios en la configuración, reinicia el servicio:

```powershell
# Detener el servicio
Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }

# Reiniciar
cd d:\DATA\source\azure-repos\gcp-test-project
.\gradlew.bat bootRun
```
