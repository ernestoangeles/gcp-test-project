package com.gcptest.orders.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "vtex")
public class VtexCompanyConfig {
    private Map<String, CompanySettings> companies;
    private String ordersEndpoint;

    @Data
    public static class CompanySettings {
        private String companyCode;
        private String baseUrl;
        private String appToken;
        private String appKey;
    }

    public CompanySettings getCompany(String companyName) {
        CompanySettings settings = companies.get(companyName.toLowerCase());
        if (settings == null) {
            throw new IllegalArgumentException("Company no válido: " + companyName + 
                ". Valores permitidos: plazavea, promart, oechsle, promartec");
        }
        return settings;
    }
}
