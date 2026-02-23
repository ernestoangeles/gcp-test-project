package com.gcptest.orders.controller;

import com.gcptest.orders.dto.OrderProcessingReport;
import com.gcptest.orders.service.OrderProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderProcessingController {

    private final OrderProcessingService orderProcessingService;

    /**
     * Procesa pedidos desde el archivo orders.txt (modo asíncrono)
     * Retorna 200 inmediatamente y procesa en background
     * 
     * Endpoint: GET /api/orders/process/{company}
     * Path param: company (requerido: plazavea, promart, oechsle, promartec)
     * Query param: filePath (opcional, por defecto: "orders.txt")
     * 
     * Ejemplo: 
     *   GET http://localhost:8081/api/orders/process/oechsle
     *   GET http://localhost:8081/api/orders/process/promart?filePath=orders.txt
     *   GET http://localhost:8081/api/orders/process/plazavea?filePath=d:/path/to/orders.txt
     */
    @GetMapping("/process/{company}")
    public ResponseEntity<?> processOrders(
            @PathVariable String company,
            @RequestParam(value = "filePath", required = false, defaultValue = "orders.txt") String filePath) {
        
        log.info("Solicitud recibida para procesar pedidos de company: {} desde archivo: {}", company, filePath);
        
        try {
            // Iniciar procesamiento asíncrono
            orderProcessingService.processOrdersFromFileAsync(company, filePath);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "PROCESSING");
            response.put("message", "El procesamiento de pedidos ha iniciado en background");
            response.put("company", company);
            response.put("filePath", filePath);
            response.put("info", "Los resultados se guardarán en output/resultadoYYYYMMDD_HHMMSS.txt");
            
            log.info("Procesamiento asíncrono iniciado para company: {} archivo: {}", company, filePath);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("Error: company no válido: {}", company);
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Company no válido");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("company", company);
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse);
            
        } catch (RuntimeException e) {
            log.error("Error iniciando procesamiento de pedidos: {}", e.getMessage(), e);
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error al iniciar procesamiento de pedidos");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("company", company);
            errorResponse.put("filePath", filePath);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    /**
     * Endpoint de health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Order Processing Service");
        return ResponseEntity.ok(response);
    }
}
