package com.gcptest.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class GcpConfig {

    @Value("${gcp.project-id}")
    private String projectId;

    @Value("${gcp.credentials.oechsle.location:}")
    private String credentialsOechsle;

    @Value("${gcp.credentials.promart.location:}")
    private String credentialsPromart;

    private String resolveCredentialsPath() {
        String tipo = System.getProperty("gcp.tipo");
        if ("promart".equalsIgnoreCase(tipo)) {
            return credentialsPromart;
        }
        // Default to oechsle
        return credentialsOechsle;
    }

    @Bean
    public GoogleCredentials googleCredentials() throws IOException {
        String path = resolveCredentialsPath();
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("No se encontró la ruta de credenciales para el tipo seleccionado");
        }
        return GoogleCredentials.fromStream(new FileInputStream(path));
    }

    @Bean
    public BigQuery bigQuery(GoogleCredentials credentials) {
        return BigQueryOptions.newBuilder()
                .setProjectId(projectId)
                .setCredentials(credentials)
                .build()
                .getService();
    }

    @Bean
    public Storage cloudStorage(GoogleCredentials credentials) {
        return StorageOptions.newBuilder()
                .setProjectId(projectId)
                .setCredentials(credentials)
                .build()
                .getService();
    }
}
