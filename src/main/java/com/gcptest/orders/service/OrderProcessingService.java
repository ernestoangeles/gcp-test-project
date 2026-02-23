package com.gcptest.orders.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gcptest.orders.config.VtexCompanyConfig;
import com.gcptest.orders.dto.OrderProcessingReport;
import com.gcptest.orders.dto.OrderProcessingResult;
import com.gcptest.orders.dto.PaymentStatusResponse;
import com.gcptest.orders.dto.VtexOrderResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OrderProcessingService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final VtexCompanyConfig vtexCompanyConfig;

    @Value("${payment.api.base-url}")
    private String paymentBaseUrl;

    @Value("${payment.api.detail-endpoint}")
    private String paymentDetailEndpoint;

    public OrderProcessingService(ObjectMapper objectMapper, VtexCompanyConfig vtexCompanyConfig) {
        this.objectMapper = objectMapper;
        this.vtexCompanyConfig = vtexCompanyConfig;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Procesa el archivo orders.txt y obtiene información de cada pedido (versión asíncrona)
     */
    @Async
    public void processOrdersFromFileAsync(String company, String filePath) {
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("🚀 INICIANDO PROCESAMIENTO ASÍNCRONO");
        log.info("   📂 Archivo: {}", filePath);
        log.info("   🏢 Company: {}", company.toUpperCase());
        log.info("═══════════════════════════════════════════════════════════════");
        
        OrderProcessingReport report = processOrdersFromFile(company, filePath);
        
        // Generar archivo CSV con los resultados
        try {
            generateCsvReport(report);
            log.info("");
            log.info("╔═══════════════════════════════════════════════════════════════╗");
            log.info("║  ✅ ARCHIVO CSV GENERADO EXITOSAMENTE                        ║");
            log.info("╚═══════════════════════════════════════════════════════════════╝");
        } catch (IOException e) {
            log.error("❌ Error generando archivo CSV: {}", e.getMessage(), e);
        }
    }

    /**
     * Procesa el archivo orders.txt y obtiene información de cada pedido
     */
    public OrderProcessingReport processOrdersFromFile(String company, String filePath) {
        log.info("Iniciando procesamiento de pedidos desde archivo: {} para company: {}", filePath, company);
        long startTime = System.currentTimeMillis();

        // Validar company y obtener configuración
        VtexCompanyConfig.CompanySettings companySettings = vtexCompanyConfig.getCompany(company);

        List<String> orderIds = readOrderIdsFromFile(filePath);
        List<OrderProcessingResult> results = new ArrayList<>();

        int successCount = 0;
        int failedCount = 0;
        int totalOrders = orderIds.size();
        int currentLine = 0;
        int lastProgressPercent = 0;

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("📦 INICIANDO PROCESAMIENTO DE {} PEDIDOS PARA: {}", totalOrders, company.toUpperCase());
        log.info("═══════════════════════════════════════════════════════════════");

        for (String orderId : orderIds) {
            currentLine++;
            
            // Mostrar progreso por porcentaje cada 10%
            int currentPercent = (currentLine * 100) / totalOrders;
            if (currentPercent >= lastProgressPercent + 10 && currentPercent > lastProgressPercent) {
                lastProgressPercent = currentPercent;
                log.info("");
                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log.info("📊 PROGRESO: {}% ({}/{}) | ✓ Exitosos: {} | ✗ Fallidos: {}", 
                        currentPercent, currentLine, totalOrders, successCount, failedCount);
                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log.info("");
            }
            
            try {
                log.info("[{}/{}] Procesando: {}", currentLine, totalOrders, orderId);
                OrderProcessingResult result = processOrder(orderId, companySettings);
                results.add(result);
                
                if (result.isSuccess()) {
                    successCount++;
                    log.info("[{}/{}] ✓ EXITOSO - {}", currentLine, totalOrders, orderId);
                } else {
                    failedCount++;
                    log.warn("[{}/{}] ✗ FALLIDO - {} - Error: {}", currentLine, totalOrders, orderId, result.getErrorMessage());
                }
            } catch (Exception e) {
                log.error("[{}/{}] ✗ ERROR procesando {} - {}", currentLine, totalOrders, orderId, e.getMessage());
                OrderProcessingResult errorResult = OrderProcessingResult.builder()
                        .orderId(orderId)
                        .success(false)
                        .errorMessage("Error inesperado: " + e.getMessage())
                        .build();
                results.add(errorResult);
                failedCount++;
            }
        }

        long endTime = System.currentTimeMillis();
        long processingTimeSeconds = (endTime - startTime) / 1000;

        log.info("");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("✅ PROCESAMIENTO COMPLETADO");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("📊 Total procesados: {}", totalOrders);
        log.info("✓  Exitosos: {} ({}%)", successCount, totalOrders > 0 ? (successCount * 100 / totalOrders) : 0);
        log.info("✗  Fallidos: {} ({}%)", failedCount, totalOrders > 0 ? (failedCount * 100 / totalOrders) : 0);
        log.info("⏱  Tiempo total: {} segundos ({} ms)", processingTimeSeconds, endTime - startTime);
        log.info("📁 Archivo CSV será generado en: output/resultado[timestamp].txt");
        log.info("═══════════════════════════════════════════════════════════════");

        return OrderProcessingReport.builder()
                .processingDate(LocalDateTime.now())
                .totalOrders(orderIds.size())
                .successfulOrders(successCount)
                .failedOrders(failedCount)
                .results(results)
                .processingTimeMs(endTime - startTime)
                .build();
    }

    /**
     * Procesa un pedido individual
     */
    private OrderProcessingResult processOrder(String orderId, VtexCompanyConfig.CompanySettings companySettings) {
        // Paso 1: Obtener información del pedido de VTEX
        VtexOrderResponse vtexOrder;
        try {
            vtexOrder = getVtexOrder(orderId, companySettings);
        } catch (Exception e) {
            log.error("Error obteniendo pedido de VTEX para orderId {}: {}", orderId, e.getMessage());
            return OrderProcessingResult.builder()
                    .orderId(orderId)
                    .success(false)
                    .errorMessage("Error VTEX API: " + e.getMessage())
                    .build();
        }

        // Extraer vtexPaymentId (opcional, para referencia)
        String vtexPaymentId = extractVtexPaymentId(vtexOrder);
        String paymentSystem = extractPaymentSystem(vtexOrder);
        String paymentSystemName = extractPaymentSystemName(vtexOrder);
        
        // Extraer nuevos campos de VTEX
        String cancellationDate = extractCancellationDate(vtexOrder);
        String cancellationRequestDate = extractCancellationRequestDate(vtexOrder);

        // Paso 2: Verificar si debe obtener información de pago
        // No llamar al payment API si paymentSystemName es SafetyPay o Lotérica
        boolean skipPaymentApi = paymentSystemName != null && 
                (paymentSystemName.equalsIgnoreCase("SafetyPay") || 
                 paymentSystemName.equalsIgnoreCase("Lotérica"));

        PaymentStatusResponse paymentStatus = null;
        if (!skipPaymentApi) {
            try {
                paymentStatus = getPaymentStatus(orderId, companySettings.getCompanyCode());
            } catch (Exception e) {
                log.error("Error obteniendo estado de pago para orderId {}: {}", orderId, e.getMessage());
                return OrderProcessingResult.builder()
                        .orderId(orderId)
                        .vtexPaymentId(vtexPaymentId)
                        .paymentSystem(paymentSystem)
                        .paymentSystemName(paymentSystemName)
                        .hostname(vtexOrder.getHostname())
                        .creationDate(formatDateFromUtc(vtexOrder.getCreationDate()))
                        .cancellationDate(formatDateFromUtc(cancellationDate))
                        .cancellationRequestDate(formatDateFromUtc(cancellationRequestDate))
                        .orderStatus(vtexOrder.getStatus())
                        .orderStatusDescription(vtexOrder.getStatusDescription())
                        .orderIsCompleted(vtexOrder.getIsCompleted())
                        .success(false)
                        .errorMessage("Error Payment API: " + e.getMessage())
                        .build();
            }
        }

        // Construir resultado exitoso
        return OrderProcessingResult.builder()
                .orderId(orderId)
                .vtexPaymentId(vtexPaymentId)
                .paymentSystem(paymentSystem)
                .paymentSystemName(paymentSystemName)
                .hostname(vtexOrder.getHostname())
                .creationDate(formatDateFromUtc(vtexOrder.getCreationDate()))
                .cancellationDate(formatDateFromUtc(cancellationDate))
                .cancellationRequestDate(formatDateFromUtc(cancellationRequestDate))
                .orderStatus(vtexOrder.getStatus())
                .orderStatusDescription(vtexOrder.getStatusDescription())
                .orderIsCompleted(vtexOrder.getIsCompleted())
                .paymentDate(paymentStatus != null ? formatDate(paymentStatus.getPaymentDate()) : null)
                .paymentStatus(paymentStatus != null ? paymentStatus.getStatus() : null)
                .paymentMethod(paymentStatus != null ? paymentStatus.getPaymentMethod() : null)
                .cardIssuer(paymentStatus != null ? paymentStatus.getCardIssuer() : null)
                .cardBrand(paymentStatus != null ? paymentStatus.getCardBrand() : null)
                .purchaseType(paymentStatus != null ? paymentStatus.getPurchaseType() : null)
                .trxType(paymentStatus != null ? paymentStatus.getTrxType() : null)
                .trxStatus(paymentStatus != null ? paymentStatus.getTrxStatus() : null)
                .trxResponse(paymentStatus != null ? paymentStatus.getTrxResponse() : null)
                .message(paymentStatus != null ? paymentStatus.getMessage() : null)
                .success(true)
                .build();
    }

    /**
     * Obtiene información del pedido desde VTEX API
     */
    private VtexOrderResponse getVtexOrder(String orderId, VtexCompanyConfig.CompanySettings companySettings) throws IOException {
        String url = companySettings.getBaseUrl() + vtexCompanyConfig.getOrdersEndpoint() + "/" + orderId;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-VTEX-API-AppToken", companySettings.getAppToken())
                .addHeader("X-VTEX-API-AppKey", companySettings.getAppKey())
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("VTEX API respondió con código: " + response.code() + 
                                    " - " + response.message() + " url: " + url);
            }

            String responseBody = response.body().string();
            return objectMapper.readValue(responseBody, VtexOrderResponse.class);
        }
    }

    /**
     * Obtiene el estado del pago desde Payment API usando orderId y companyCode
     */
    private PaymentStatusResponse getPaymentStatus(String orderId, String companyCode) throws IOException {
        String url = paymentBaseUrl + paymentDetailEndpoint + "/" + companyCode + "/" + orderId + "/detail";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Payment API respondió con código: " + response.code() + 
                                    " - " + response.message() + " url: " + url);
            }

            String responseBody = response.body().string();
            return objectMapper.readValue(responseBody, PaymentStatusResponse.class);
        }
    }

    /**
     * Extrae el vtexPaymentId de la respuesta de VTEX
     */
    private String extractVtexPaymentId(VtexOrderResponse vtexOrder) {
        if (vtexOrder.getPaymentData() == null || 
            vtexOrder.getPaymentData().getTransactions() == null || 
            vtexOrder.getPaymentData().getTransactions().isEmpty()) {
            return null;
        }

        VtexOrderResponse.Transaction firstTransaction = 
                vtexOrder.getPaymentData().getTransactions().get(0);
        
        if (firstTransaction.getPayments() == null || 
            firstTransaction.getPayments().isEmpty()) {
            return null;
        }

        return firstTransaction.getPayments().get(0).getId();
    }

    /**
     * Extrae el paymentSystem de la respuesta de VTEX
     */
    private String extractPaymentSystem(VtexOrderResponse vtexOrder) {
        if (vtexOrder.getPaymentData() == null || 
            vtexOrder.getPaymentData().getTransactions() == null || 
            vtexOrder.getPaymentData().getTransactions().isEmpty()) {
            return null;
        }

        VtexOrderResponse.Transaction firstTransaction = 
                vtexOrder.getPaymentData().getTransactions().get(0);
        
        if (firstTransaction.getPayments() == null || 
            firstTransaction.getPayments().isEmpty()) {
            return null;
        }

        return firstTransaction.getPayments().get(0).getPaymentSystem();
    }

    /**
     * Extrae el paymentSystemName de la respuesta de VTEX
     */
    private String extractPaymentSystemName(VtexOrderResponse vtexOrder) {
        if (vtexOrder.getPaymentData() == null || 
            vtexOrder.getPaymentData().getTransactions() == null || 
            vtexOrder.getPaymentData().getTransactions().isEmpty()) {
            return null;
        }

        VtexOrderResponse.Transaction firstTransaction = 
                vtexOrder.getPaymentData().getTransactions().get(0);
        
        if (firstTransaction.getPayments() == null || 
            firstTransaction.getPayments().isEmpty()) {
            return null;
        }

        return firstTransaction.getPayments().get(0).getPaymentSystemName();
    }

    /**
     * Extrae la fecha de cancelación
     */
    private String extractCancellationDate(VtexOrderResponse vtexOrder) {
        if (vtexOrder.getCancellationData() == null) {
            return null;
        }
        return vtexOrder.getCancellationData().getCancellationDate();
    }

    /**
     * Extrae la fecha de solicitud de cancelación
     */
    private String extractCancellationRequestDate(VtexOrderResponse vtexOrder) {
        if (vtexOrder.getCancellationRequests() == null || 
            vtexOrder.getCancellationRequests().isEmpty()) {
            return null;
        }
        return vtexOrder.getCancellationRequests().get(0).getCancellationRequestDate();
    }

    /**
     * Formatea una fecha UTC y convierte a timezone de Lima (UTC-5)
     * Formato de salida: "yyyy-MM-dd HH:mm:ss.SSS"
     */
    private String formatDateFromUtc(String utcDate) {
        if (utcDate == null || utcDate.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Parsear la fecha UTC
            ZonedDateTime utcDateTime = ZonedDateTime.parse(utcDate);
            
            // Convertir a timezone de Lima (UTC-5)
            ZoneId limaZone = ZoneId.of("America/Lima");
            ZonedDateTime limaDateTime = utcDateTime.withZoneSameInstant(limaZone);
            
            // Formatear según el formato requerido
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
            return limaDateTime.format(formatter);
        } catch (Exception e) {
            log.error("Error formateando fecha UTC {}: {}", utcDate, e.getMessage());
            return utcDate; // Retornar original si hay error
        }
    }

    /**
     * Formatea una fecha al formato requerido sin cambiar timezone
     * Formato de salida: "yyyy-MM-dd HH:mm:ss.SSS"
     */
    private String formatDate(String date) {
        if (date == null || date.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Parsear la fecha (puede tener timezone o no)
            ZonedDateTime dateTime;
            if (date.contains("T") && (date.contains("Z") || date.contains("+") || date.matches(".*[-+]\\d{2}:\\d{2}$"))) {
                dateTime = ZonedDateTime.parse(date);
            } else {
                // Si no tiene timezone, asumir que ya está en el correcto
                LocalDateTime localDateTime = LocalDateTime.parse(date, DateTimeFormatter.ISO_DATE_TIME);
                dateTime = localDateTime.atZone(ZoneId.systemDefault());
            }
            
            // Formatear según el formato requerido
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
            return dateTime.format(formatter);
        } catch (Exception e) {
            log.error("Error formateando fecha {}: {}", date, e.getMessage());
            return date; // Retornar original si hay error
        }
    }

    /**
     * Genera el archivo CSV con los resultados del procesamiento
     */
    private void generateCsvReport(OrderProcessingReport report) throws IOException {
        // Crear directorio output si no existe
        Path outputDir = Paths.get("output");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        // Nombre del archivo con fecha
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "output/resultado" + timestamp + ".txt";

        log.info("Generando archivo CSV: {}", fileName);

        try (FileWriter writer = new FileWriter(fileName)) {
            // Escribir encabezados
            writer.write("hostname,orderId,creationDate,CancellationDate,cancellationRequestDate,");
            writer.write("vtexPaymentId,paymentSystem,paymentSystemName,orderStatus,orderStatusDescription,orderIsCompleted,");
            writer.write("paymentDate,paymentStatus,paymentMethod,cardIssuer,cardBrand,purchaseType,");
            writer.write("trxType,trxStatus,trxResponse,message,success,errorMessage\n");

            // Escribir resultados
            for (OrderProcessingResult result : report.getResults()) {
                writer.write(escapeCsv(result.getHostname()) + ",");
                writer.write(escapeCsv(result.getOrderId()) + ",");
                writer.write(escapeCsv(result.getCreationDate()) + ",");
                writer.write(escapeCsv(result.getCancellationDate()) + ",");
                writer.write(escapeCsv(result.getCancellationRequestDate()) + ",");
                writer.write(escapeCsv(result.getVtexPaymentId()) + ",");
                writer.write(escapeCsv(result.getPaymentSystem()) + ",");
                writer.write(escapeCsv(result.getPaymentSystemName()) + ",");
                writer.write(escapeCsv(result.getOrderStatus()) + ",");
                writer.write(escapeCsv(result.getOrderStatusDescription()) + ",");
                writer.write(escapeCsv(String.valueOf(result.getOrderIsCompleted())) + ",");
                writer.write(escapeCsv(result.getPaymentDate()) + ",");
                writer.write(escapeCsv(result.getPaymentStatus()) + ",");
                writer.write(escapeCsv(result.getPaymentMethod()) + ",");
                writer.write(escapeCsv(result.getCardIssuer()) + ",");
                writer.write(escapeCsv(result.getCardBrand()) + ",");
                writer.write(escapeCsv(result.getPurchaseType()) + ",");
                writer.write(escapeCsv(result.getTrxType()) + ",");
                writer.write(escapeCsv(result.getTrxStatus()) + ",");
                writer.write(escapeCsv(result.getTrxResponse()) + ",");
                writer.write(escapeCsv(result.getMessage()) + ",");
                writer.write(result.isSuccess() + ",");
                writer.write(escapeCsv(result.getErrorMessage()) + "\n");
            }

            // Escribir línea final con tiempo de procesamiento
            writer.write("\n");
            writer.write("Tiempo total de procesamiento: " + report.getProcessingTimeMs() + " ms\n");
        }

        log.info("Archivo CSV generado exitosamente: {}", fileName);
    }

    /**
     * Escapa valores para CSV (maneja nulos y comas)
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // Si contiene coma, comillas o salto de línea, encerrar entre comillas
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Lee los orderIds del archivo txt
     */
    private List<String> readOrderIdsFromFile(String filePath) {
        List<String> orderIds = new ArrayList<>();
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            log.error("El archivo no existe: {}", filePath);
            throw new RuntimeException("Archivo no encontrado: " + filePath);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (!trimmedLine.isEmpty()) {
                    orderIds.add(trimmedLine);
                }
            }
        } catch (IOException e) {
            log.error("Error leyendo archivo {}: {}", filePath, e.getMessage());
            throw new RuntimeException("Error leyendo archivo: " + e.getMessage(), e);
        }

        log.info("Se leyeron {} orderIds del archivo", orderIds.size());
        return orderIds;
    }
}
