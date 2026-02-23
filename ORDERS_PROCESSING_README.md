# Procesamiento de Pedidos VTEX - Multi-Company

Sistema de procesamiento asíncrono de pedidos VTEX con integración a API de pagos, soportando múltiples empresas.

## 🚀 Características

- ✅ **Multi-Company**: Soporte para plazavea, promart, oechsle y promartec
- ✅ **Procesamiento Asíncrono**: Retorna 200 OK inmediatamente y procesa en background
- ✅ **Integración VTEX**: Obtiene datos completos de pedidos
- ✅ **Integración Payment API**: Obtiene estado de pagos (condicional)
- ✅ **Generación CSV Automática**: Archivo con resultados en formato CSV
- ✅ **Conversión de Timezone**: Fechas convertidas de UTC a Lima (UTC-5)
- ✅ **Logs Optimizados**: Progreso por línea, errores detallados
- ✅ **Skip Payment API**: No invoca payment API para SafetyPay o Lotérica

## 📋 Endpoint

```
GET /api/orders/process/{company}?filePath=orders.txt
```

### Path Parameters

- **company** (requerido): `plazavea` | `promart` | `oechsle` | `promartec`

### Query Parameters

- **filePath** (opcional): Ruta del archivo con orderIds (default: `orders.txt`)

### Ejemplos

```bash
# Procesar pedidos de Oechsle
GET http://localhost:8081/api/orders/process/oechsle

# Procesar pedidos de Promart con archivo específico
GET http://localhost:8081/api/orders/process/promart?filePath=orders_promart.txt

# Procesar pedidos de PlazaVea
GET http://localhost:8081/api/orders/process/plazavea

# Procesar pedidos de Promart Ecuador
GET http://localhost:8081/api/orders/process/promartec
```

## 🏢 Configuración por Company

Cada company tiene su propia configuración en `application.yaml`:

| Company    | Company Code | VTEX Base URL                               |
|-----------|--------------|---------------------------------------------|
| plazavea  | pvea         | https://plazavea.vtexcommercestable.com.br |
| promart   | hpsa         | https://promart.vtexcommercestable.com.br  |
| oechsle   | tpsa         | https://oechsle.vtexcommercestable.com.br  |
| promartec | hpsaec       | https://promartec.vtexcommercestable.com.br|

Cada company tiene sus propias credenciales:
- `app-token`: Token de autenticación VTEX
- `app-key`: Key de autenticación VTEX

## 📄 Formato del Archivo de Entrada

Archivo de texto plano con un `orderId` por línea:

```
1607480222673-01
1607460222191-01
1607440221743-01
```

## 📊 Campos Extraídos

### De VTEX API

1. **hostname**: Nombre del host VTEX
2. **orderId**: ID del pedido
3. **creationDate**: Fecha de creación (convertida a Lima UTC-5)
4. **CancellationDate**: Fecha de cancelación (convertida a Lima UTC-5)
5. **cancellationRequestDate**: Fecha de solicitud de cancelación (convertida a Lima UTC-5)
6. **vtexPaymentId**: ID de pago en VTEX (paymentData.transactions.payments.id)
7. **paymentSystem**: Sistema de pago (paymentData.transactions.payments.paymentSystem)
8. **paymentSystemName**: Nombre del sistema de pago (paymentData.transactions.payments.paymentSystemName)
9. **orderStatus**: Estado del pedido
10. **orderStatusDescription**: Descripción del estado
11. **orderIsCompleted**: ¿Pedido completado?

### De Payment API (Condicional)

> **Nota**: Si `paymentSystemName` es "SafetyPay" o "Lotérica", estos campos quedan en null

12. **paymentDate**: Fecha del pago (reformateada)
13. **paymentStatus**: Estado del pago
14. **paymentMethod**: Método de pago
15. **cardIssuer**: Emisor de tarjeta
16. **cardBrand**: Marca de tarjeta
17. **purchaseType**: Tipo de compra
18. **trxType**: Tipo de transacción
19. **trxStatus**: Estado de transacción
20. **trxResponse**: Respuesta de transacción
21. **message**: Mensaje adicional

### Campos de Control

22. **success**: true/false
23. **errorMessage**: Mensaje de error si falló

## 🎯 Lógica de Procesamiento

1. **Validación de Company**: 
   - Valida que el company sea válido (plazavea, promart, oechsle, promartec)
   - Carga credenciales VTEX específicas del company

2. **Lectura de OrderIds**: 
   - Lee archivo de texto línea por línea
   - Ignora líneas vacías

3. **Para cada OrderId**:
   - Invoca VTEX API con credenciales del company
   - Extrae paymentSystemName
   - **SI** paymentSystemName es "SafetyPay" o "Lotérica":
     - ⏭️ **SKIP** - No invoca Payment API
     - Campos de payment quedan en null
   - **SINO**:
     - ✅ Invoca Payment API con companyCode
     - Extrae datos de pago

4. **Conversión de Fechas**:
   - creationDate, CancellationDate, cancellationRequestDate: UTC → Lima (UTC-5)
   - paymentDate: Solo reformateo (ya está en timezone correcto)
   - Formato final: `yyyy-MM-dd HH:mm:ss.SSS`

5. **Generación CSV**:
   - Crea archivo en `output/resultadoYYYYMMDD_HHMMSS.txt`
   - 23 columnas con todos los datos
   - Línea final con tiempo de procesamiento

## 📦 Archivo de Salida

Se genera automáticamente en la carpeta `output/`:

```
output/resultado20260222_153045.txt
```

### Formato del CSV

```csv
hostname,orderId,creationDate,CancellationDate,cancellationRequestDate,vtexPaymentId,paymentSystem,paymentSystemName,orderStatus,orderStatusDescription,orderIsCompleted,paymentDate,paymentStatus,paymentMethod,cardIssuer,cardBrand,purchaseType,trxType,trxStatus,trxResponse,message,success,errorMessage
promart.com,1607480222673-01,2026-02-01 15:30:22.123,,,12345,2,Visa,ready-for-handling,Listo para manejar,true,2026-02-01 16:00:45.678,APPROVED,CREDIT_CARD,VISA,VISA,NORMAL,SALE,APPROVED,00,Transacción aprobada,true,
```

## 🔧 Configuración

### application.yaml

```yaml
vtex:
  companies:
    plazavea:
      company-code: "pvea"
      base-url: "https://plazavea.vtexcommercestable.com.br"
      app-token: "YOUR_PLAZAVEA_TOKEN"
      app-key: "YOUR_PLAZAVEA_KEY"
    promart:
      company-code: "hpsa"
      base-url: "https://promart.vtexcommercestable.com.br"
      app-token: "YOUR_PROMART_TOKEN"
      app-key: "YOUR_PROMART_KEY"
    oechsle:
      company-code: "tpsa"
      base-url: "https://oechsle.vtexcommercestable.com.br"
      app-token: "YOUR_OECHSLE_TOKEN"
      app-key: "YOUR_OECHSLE_KEY"
    promartec:
      company-code: "hpsaec"
      base-url: "https://promartec.vtexcommercestable.com.br"
      app-token: "YOUR_PROMARTEC_TOKEN"
      app-key: "YOUR_PROMARTEC_KEY"
  orders-endpoint: "/api/oms/pvt/orders"

payment:
  api:
    base-url: "https://payment-prd.cc.cloudintercorpretail.pe"
    detail-endpoint: "/api/customer-care/v1/payments"

logging:
  level:
    com.gcptest: WARN

server:
  port: 8081
```

## 🔍 Logs del Proceso

```
Iniciando procesamiento asíncrono de pedidos desde archivo: orders.txt para company: oechsle
Iniciando procesamiento de pedidos desde archivo: orders.txt para company: oechsle
Procesando línea 1/500: 1607480222673-01
  → Línea 1/500: ✓ EXITOSO
Procesando línea 2/500: 1607460222191-01
  → Línea 2/500: ✗ FALLIDO - Error VTEX API: ...
...
Generando archivo CSV: output/resultado20260222_153045.txt
Archivo CSV generado exitosamente
```

## ⚠️ Manejo de Errores

- **Company inválido**: HTTP 400 con mensaje de error
- **Archivo no encontrado**: Se registra en logs y continúa
- **Error en VTEX API**: Se registra error por pedido, continúa con siguiente
- **Error en Payment API**: Se registra error por pedido, continúa con siguiente
- **Error inesperado**: Se captura excepción, registra y marca pedido como fallido

## 🎨 Características Especiales

### Skip Payment API

Si `paymentSystemName` es uno de los siguientes valores, **NO** se invoca el Payment API:
- SafetyPay
- Lotérica

En estos casos:
- Los campos de payment quedan en `null`
- El procesamiento es más rápido
- No se generan errores de Payment API

### Formato de Fechas

Todas las fechas se muestran en formato:
```
yyyy-MM-dd HH:mm:ss.SSS
```

Ejemplo: `2026-02-22 15:30:45.123`

### Timezone

- **Fechas de VTEX** (creationDate, CancellationDate, cancellationRequestDate):
  - Vienen en UTC
  - Se convierten a Lima (UTC-5)
  
- **Fecha de Payment** (paymentDate):
  - Ya viene en timezone correcto
  - Solo se reformatea

## 📚 Arquitectura

```
OrderProcessingController
    └─> OrderProcessingService
            ├─> VtexCompanyConfig (configuración por company)
            ├─> VTEX API (con credenciales del company)
            ├─> Payment API (solo si NO es SafetyPay/Lotérica)
            │   └─> URL incluye companyCode
            └─> CSV Generator
```

## 🧪 Testing

1. Crear archivo `orders.txt` con orderIds de prueba
2. Ejecutar aplicación: `gradlew bootRun`
3. Invocar endpoint: `GET http://localhost:8081/api/orders/process/oechsle`
4. Verificar respuesta 200 OK
5. Revisar logs para ver progreso
6. Verificar archivo CSV generado en `output/`

## 📝 Notas Importantes

1. **Credenciales**: Asegurarse de configurar app-token y app-key para cada company
2. **Payment API URL**: El companyCode se usa en el path: `/api/customer-care/v1/payments/{companyCode}/{orderId}/detail`
3. **Procesamiento Asíncrono**: El endpoint retorna inmediatamente, el procesamiento continúa en background
4. **Manejo de Errores**: Los errores de pedidos individuales no detienen el proceso general
5. **CSV Automático**: Siempre se genera un archivo CSV al finalizar el procesamiento

## 🚦 Estado del Servicio

Health Check: `GET http://localhost:8081/api/orders/health`

Respuesta:
```json
{
  "status": "UP",
  "service": "Order Processing Service"
}
```
