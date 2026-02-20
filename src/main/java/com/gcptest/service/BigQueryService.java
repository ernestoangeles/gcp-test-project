package com.gcptest.service;

import com.google.cloud.bigquery.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BigQueryService {
    public void extractPromartRecords() {
        try {
            System.out.println("\nExtracting Promart records from view...");
            String query = String.format("""
                SELECT DISTINCT
                    A.PARTITION_DATE,
                    A.SOURCE,
                    A.INTERNAL_CODE,
                    A.SELL_TENANT_ID,
                    A.DISPATCH_TENANT_ID,
                    A.ORDER_ID,
                    A.SALE_NOTE_NUMBER,
                    A.INVOICE_NUMBER,
                    A.STORE_TYPE,
                    A.SALE_DATE,
                    A.SALE_STORE_CODE,
                    A.SALE_STORE_NAME,
                    A.POS,
                    A.SEQUENCE,
                    A.TOTAL_AMOUNT_ORDER,
                    A.DOCUMENT_TYPE,
                    A.ID_DOCUMENT_TYPE,
                    A.RUC_COMPANY,
                    A.TRANSACTION_TYPE,
                    IF(LENGTH(SAFE_CAST(AEAD.DECRYPT_STRING(B.key,A.CUSTOMER_DOCUMENT,A.INTERNAL_CODE) AS STRING))<8 AND
                      CUSTOMER_DOCUMENT_TYPE='DNI',
                      RIGHT(CONCAT('00000000',Ltrim(Rtrim(SAFE_CAST(AEAD.DECRYPT_STRING(B.key,A.CUSTOMER_DOCUMENT,A.INTERNAL_CODE) AS STRING)))),8),
                      SAFE_CAST(AEAD.DECRYPT_STRING(B.key,A.CUSTOMER_DOCUMENT,A.INTERNAL_CODE) AS STRING)
                    ) AS CUSTOMER_DOCUMENT,
                    A.CUSTOMER_DOCUMENT_TYPE,
                    SAFE_CAST(AEAD.DECRYPT_STRING(C.key,A.CUSTOMER_NAME,A.INTERNAL_CODE) AS STRING) AS CUSTOMER_NAME,
                    SAFE_CAST(AEAD.DECRYPT_STRING(D.key,A.CUSTOMER_LAST_NAME,A.INTERNAL_CODE) AS STRING) AS CUSTOMER_LAST_NAME,
                    SAFE_CAST(AEAD.DECRYPT_STRING(E.key,A.CUSTOMER_EMAIL,A.INTERNAL_CODE) AS STRING) AS CUSTOMER_EMAIL,
                    SAFE_CAST(AEAD.DECRYPT_STRING(F.key,A.CUSTOMER_PHONE,A.INTERNAL_CODE) AS STRING) AS CUSTOMER_PHONE,
                    A.SKU_CODE,
                    A.PRODUCT_NAME,
                    A.QUANTITY,
                    A.MEASUREMENT_UNIT,
                    A.ORIGIN_STORE_CODE,
                    A.ORIGIN_STORE_NAME,
                    A.ORIGIN_STORE_TYPE,
                    A.PRICE,
                    A.BRAND,
                    A.CURRENT_STATE,
                    A.CURRENT_STATE_DESCRIPTION,
                    A.DELIVERY_DATE,
                    A.DELIVERY_MODE,
                    A.DELIVERY_TYPE,
                    A.WITHDRAW_STORE_CODE,
                    A.WITHDRAW_STORE_NAME,
                    A.DEPARTMENT_CODE,
                    A.DEPARTMENT_NAME,
                    A.CATEGORY_CODE,
                    A.CATEGORY_NAME,
                    A.FLAG_DAD
                FROM `%s` A
                INNER JOIN `pe-intercorpretail-cld-001.raw_key_security.v_config_protected_data_hp`  B ON 1=1 and B.code = 'C_ID'
                INNER JOIN `pe-intercorpretail-cld-001.raw_key_security.v_config_protected_data_hp`  C ON 1=1 and C.code = 'C_NAME'
                INNER JOIN `pe-intercorpretail-cld-001.raw_key_security.v_config_protected_data_hp`  D ON 1=1 and D.code = 'C_LAST_NAME'
                INNER JOIN `pe-intercorpretail-cld-001.raw_key_security.v_config_protected_data_hp`  E ON 1=1 and E.code = 'C_EMAIL'
                INNER JOIN `pe-intercorpretail-cld-001.raw_key_security.v_config_protected_data_hp`  F ON 1=1 and F.code = 'C_TELEPHONE'
                WHERE PARTITION_DATE='2025-05-05'
                LIMIT 10
                """, viewNamePr);
            System.out.println("Executing query: " + query);
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
            JobId jobId = JobId.of(java.util.UUID.randomUUID().toString());
            Job queryJob = bigQuery.create(JobInfo.newBuilder(queryConfig).setJobId(jobId).build());
            queryJob = queryJob.waitFor();
            if (queryJob == null) {
                throw new RuntimeException("Job no longer exists");
            } else if (queryJob.getStatus().getError() != null) {
                throw new RuntimeException("Query failed: " + queryJob.getStatus().getError().toString());
            }
            TableResult result = queryJob.getQueryResults();
            System.out.println("Query completed successfully!");
            System.out.println("Total rows returned: " + result.getTotalRows());
            System.out.println("\n=== Results ===");
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            java.nio.file.Path outputPath = java.nio.file.Paths.get("output", "bigquery_promart_results_" + timestamp + ".txt");
            java.nio.file.Files.createDirectories(outputPath.getParent());
            try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(outputPath)) {
                StringBuilder headerLine = new StringBuilder();
                result.getSchema().getFields().forEach(field -> headerLine.append(field.getName()).append("\t"));
                writer.write(headerLine.toString().trim());
                writer.newLine();
                writer.write("-".repeat(80));
                writer.newLine();
                int rowCount = 0;
                for (FieldValueList row : result.iterateAll()) {
                    rowCount++;
                    StringBuilder rowLine = new StringBuilder();
                    for (FieldValue val : row) {
                        String value = val.getValue() != null ? val.getValue().toString() : "NULL";
                        if (value.length() > 30) {
                            value = value.substring(0, 27) + "...";
                        }
                        rowLine.append(value).append("\t");
                    }
                    writer.write(rowLine.toString().trim());
                    writer.newLine();
                    if (rowCount >= 10) break;
                }
            }
            System.out.println("\n✅ Data extraction completed successfully! Result saved to " + outputPath.toString());
        } catch (Exception e) {
            System.err.println("❌ Failed to extract Promart data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    @Value("${gcp.bigquery.view-name-oe}")
    private String viewNameOe;

    @Value("${gcp.bigquery.view-name-pr}")
    private String viewNamePr;
    public void extractOechsleRecords() {
        try {
            System.out.println("\nExtracting Oechsle records from view...");
            String query = String.format("""
                SELECT DISTINCT
                    A.partition_date,
                    A.internal_code,
                    A.sell_tenant_id,
                    A.dispatch_tenant_id,
                    A.order_id,
                    A.sale_note_number,
                    A.invoice_number,
                    A.store_type,
                    A.sale_date,
                    A.sale_store_code,
                    A.sale_store_name,
                    A.pos,
                    A.sequence,
                    A.total_amount_order,
                    A.document_type,
                    A.id_document_type,
                    A.ruc_company,
                    A.transaction_type,
                    IF(LENGTH(SAFE_CAST(AEAD.DECRYPT_STRING(B.key, A.customer_document ,'1') AS STRING))<8 AND
                      A.customer_document_type='DNI',
                      RIGHT(CONCAT('00000000',Ltrim(Rtrim(SAFE_CAST(AEAD.DECRYPT_STRING(B.key, A.customer_document ,'1') AS STRING)))),8),
                      SAFE_CAST(AEAD.DECRYPT_STRING(B.key, A.customer_document ,'1') AS STRING)
                    ) AS customer_document,
                    A.customer_document_type,
                    SAFE_CAST(AEAD.DECRYPT_STRING(C.key, A.customer_name ,'1') AS STRING) customer_name,
                    SAFE_CAST(AEAD.DECRYPT_STRING(D.key, A.customer_last_name ,'1') AS STRING) customer_last_name,
                    SAFE_CAST(AEAD.DECRYPT_STRING(E.key, A.customer_email ,'1') AS STRING) customer_email,
                    SAFE_CAST(AEAD.DECRYPT_STRING(F.key, A.customer_phone ,'1') AS STRING) customer_phone,
                    A.sku_code,
                    A.product_name,
                    A.quantity,
                    A.measurement_unit,
                    A.origin_store_code,
                    A.origin_store_name,
                    A.origin_store_type,
                    A.price,
                    A.brand,
                    A.current_state,
                    A.current_state_description,
                    A.delivery_date,
                    A.delivery_mode,
                    A.delivery_type,
                    A.withdraw_store_code,
                    A.withdraw_store_name,
                    A.department_code,
                    A.department_name,
                    A.category_code,
                    A.category_name,
                    A.flag_dad
                FROM `%s` A
                INNER JOIN `pe-intercorpretail-cld-001.raw_key_security.v_config_protected_data_tp`  B ON 1=1 and B.code = 'C_DOCUMENT'
                INNER JOIN `pe-intercorpretail-cld-001.raw_key_security.v_config_protected_data_tp`  C ON 1=1 and C.code = 'C_NAME'
                INNER JOIN `pe-intercorpretail-cld-001.raw_key_security.v_config_protected_data_tp`  D ON 1=1 and D.code = 'C_LAST_NAME'
                INNER JOIN `pe-intercorpretail-cld-001.raw_key_security.v_config_protected_data_tp`  E ON 1=1 and E.code = 'C_EMAIL'
                INNER JOIN `pe-intercorpretail-cld-001.raw_key_security.v_config_protected_data_tp`  F ON 1=1 and F.code = 'C_TELEPHONE'
                WHERE PARTITION_DATE='2025-05-05'
                LIMIT 10
                """, viewNameOe);
            System.out.println("Executing query: " + query);
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
            JobId jobId = JobId.of(java.util.UUID.randomUUID().toString());
            Job queryJob = bigQuery.create(JobInfo.newBuilder(queryConfig).setJobId(jobId).build());
            queryJob = queryJob.waitFor();
            if (queryJob == null) {
                throw new RuntimeException("Job no longer exists");
            } else if (queryJob.getStatus().getError() != null) {
                throw new RuntimeException("Query failed: " + queryJob.getStatus().getError().toString());
            }
            TableResult result = queryJob.getQueryResults();
            System.out.println("Query completed successfully!");
            System.out.println("Total rows returned: " + result.getTotalRows());
            System.out.println("\n=== Results ===");
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            java.nio.file.Path outputPath = java.nio.file.Paths.get("output", "bigquery_oechsle_results_" + timestamp + ".txt");
            java.nio.file.Files.createDirectories(outputPath.getParent());
            try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(outputPath)) {
                StringBuilder headerLine = new StringBuilder();
                result.getSchema().getFields().forEach(field -> headerLine.append(field.getName()).append("\t"));
                writer.write(headerLine.toString().trim());
                writer.newLine();
                writer.write("-".repeat(80));
                writer.newLine();
                int rowCount = 0;
                for (FieldValueList row : result.iterateAll()) {
                    rowCount++;
                    StringBuilder rowLine = new StringBuilder();
                    for (FieldValue val : row) {
                        String value = val.getValue() != null ? val.getValue().toString() : "NULL";
                        if (value.length() > 30) {
                            value = value.substring(0, 27) + "...";
                        }
                        rowLine.append(value).append("\t");
                    }
                    writer.write(rowLine.toString().trim());
                    writer.newLine();
                    if (rowCount >= 10) break;
                }
            }
            System.out.println("\n✅ Data extraction completed successfully! Result saved to " + outputPath.toString());
        } catch (Exception e) {
            System.err.println("❌ Failed to extract Oechsle data: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private final BigQuery bigQuery;

    @Value("${gcp.project-id}")
    private String projectId;

    @Value("${gcp.bigquery.dataset-id}")
    private String datasetId;

    @Value("${gcp.bigquery.view-name}")
    private String viewName;

    public void testConnection() {
        try {
            System.out.println("Testing BigQuery connection...");
            
            // Test basic connection by listing datasets
            System.out.println("Available datasets:");
            bigQuery.listDatasets(projectId).iterateAll().forEach(dataset -> 
                System.out.println("  - " + dataset.getDatasetId().getDataset())
            );
            
            System.out.println("✅ BigQuery connection successful!");
            
        } catch (Exception e) {
            System.err.println("❌ BigQuery connection failed: " + e.getMessage());
            throw new RuntimeException("Failed to connect to BigQuery", e);
        }
    }

    public void extractLatest10Records() {
        try {
            System.out.println("\nExtracting latest 10 records from view...");
                        String query = String.format("""
                                SELECT DISTINCT
                                        A.PARTITION_DATE,
                                        A.SOURCE,
                                        A.INTERNAL_CODE,
                                        A.SELL_TENANT_ID,
                                        A.DISPATCH_TENANT_ID,
                                        A.ORDER_ID,
                                        A.SALE_NOTE_NUMBER,
                                        A.INVOICE_NUMBER,
                                        A.STORE_TYPE,
                                        A.SALE_DATE,
                                        A.SALE_STORE_CODE,
                                        A.SALE_STORE_NAME,
                                        A.POS,
                                        A.SEQUENCE,
                                        A.TOTAL_AMOUNT_ORDER,
                                        A.DOCUMENT_TYPE,
                                        A.ID_DOCUMENT_TYPE,
                                        A.RUC_COMPANY,
                                        A.TRANSACTION_TYPE,

                                        IF(LENGTH(SAFE_CAST(AEAD.DECRYPT_STRING(B.key,A.CUSTOMER_DOCUMENT,A.INTERNAL_CODE) AS STRING))<8 AND
                                            CUSTOMER_DOCUMENT_TYPE='DNI',
                                            RIGHT(CONCAT('00000000',Ltrim(Rtrim(SAFE_CAST(AEAD.DECRYPT_STRING(B.key,A.CUSTOMER_DOCUMENT,A.INTERNAL_CODE) AS STRING)))),8),
                                            SAFE_CAST(AEAD.DECRYPT_STRING(B.key,A.CUSTOMER_DOCUMENT,A.INTERNAL_CODE) AS STRING)
                                        ) AS CUSTOMER_DOCUMENT,

                                        A.CUSTOMER_DOCUMENT_TYPE,
                                        SAFE_CAST(AEAD.DECRYPT_STRING(C.key,A.CUSTOMER_NAME,A.INTERNAL_CODE) AS STRING) AS CUSTOMER_NAME,
                                        SAFE_CAST(AEAD.DECRYPT_STRING(D.key,A.CUSTOMER_LAST_NAME,A.INTERNAL_CODE) AS STRING) AS CUSTOMER_LAST_NAME,
                                        SAFE_CAST(AEAD.DECRYPT_STRING(E.key,A.CUSTOMER_EMAIL,A.INTERNAL_CODE) AS STRING) AS CUSTOMER_EMAIL,
                                        SAFE_CAST(AEAD.DECRYPT_STRING(F.key,A.CUSTOMER_PHONE,A.INTERNAL_CODE) AS STRING) AS CUSTOMER_PHONE,
                                        A.SKU_CODE,
                                        A.PRODUCT_NAME,
                                        A.QUANTITY,
                                        A.MEASUREMENT_UNIT,
                                        A.ORIGIN_STORE_CODE,
                                        A.ORIGIN_STORE_NAME,
                                        A.ORIGIN_STORE_TYPE,
                                        A.PRICE,
                                        A.BRAND,
                                        A.CURRENT_STATE,
                                        A.CURRENT_STATE_DESCRIPTION,
                                        A.DELIVERY_DATE,
                                        A.DELIVERY_MODE,
                                        A.DELIVERY_TYPE,
                                        A.WITHDRAW_STORE_CODE,
                                        A.WITHDRAW_STORE_NAME,
                                        A.DEPARTMENT_CODE,
                                        A.DEPARTMENT_NAME,
                                        A.CATEGORY_CODE,
                                        A.CATEGORY_NAME,
                                        A.FLAG_DAD
                                FROM `%s` A
                                INNER JOIN `pe-intercorpretail-cld-001.raw_key_security.v_config_protected_data_hp`  B ON 1=1 and B.code = 'C_ID'
                                INNER JOIN `pe-intercorpretail-cld-001.raw_key_security.v_config_protected_data_hp`  C ON 1=1 and C.code = 'C_NAME'
                                INNER JOIN `pe-intercorpretail-cld-001.raw_key_security.v_config_protected_data_hp`  D ON 1=1 and D.code = 'C_LAST_NAME'
                                INNER JOIN `pe-intercorpretail-cld-001.raw_key_security.v_config_protected_data_hp`  E ON 1=1 and E.code = 'C_EMAIL'
                                INNER JOIN `pe-intercorpretail-cld-001.raw_key_security.v_config_protected_data_hp`  F ON 1=1 and F.code = 'C_TELEPHONE'
                                WHERE PARTITION_DATE='2025-05-05'
                                LIMIT 10
                                """, viewName);
            System.out.println("Executing query: " + query);
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
            JobId jobId = JobId.of(java.util.UUID.randomUUID().toString());
            Job queryJob = bigQuery.create(JobInfo.newBuilder(queryConfig).setJobId(jobId).build());
            queryJob = queryJob.waitFor();
            if (queryJob == null) {
                throw new RuntimeException("Job no longer exists");
            } else if (queryJob.getStatus().getError() != null) {
                throw new RuntimeException("Query failed: " + queryJob.getStatus().getError().toString());
            }
            TableResult result = queryJob.getQueryResults();
            System.out.println("Query completed successfully!");
            System.out.println("Total rows returned: " + result.getTotalRows());
            System.out.println("\n=== Results ===");

            // Prepare output file
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            java.nio.file.Path outputPath = java.nio.file.Paths.get("output", "bigquery_results_" + timestamp + ".txt");
            java.nio.file.Files.createDirectories(outputPath.getParent());
            try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(outputPath)) {
                // Write headers
                StringBuilder headerLine = new StringBuilder();
                result.getSchema().getFields().forEach(field -> headerLine.append(field.getName()).append("\t"));
                writer.write(headerLine.toString().trim());
                writer.newLine();
                writer.write("-".repeat(80));
                writer.newLine();

                // Write data
                int rowCount = 0;
                for (FieldValueList row : result.iterateAll()) {
                    rowCount++;
                    StringBuilder rowLine = new StringBuilder();
                    for (FieldValue val : row) {
                        String value = val.getValue() != null ? val.getValue().toString() : "NULL";
                        if (value.length() > 30) {
                            value = value.substring(0, 27) + "...";
                        }
                        rowLine.append(value).append("\t");
                    }
                    writer.write(rowLine.toString().trim());
                    writer.newLine();
                    if (rowCount >= 10) break;
                }
            }

            System.out.println("\n✅ Data extraction completed successfully! Result saved to " + outputPath.toString());

        } catch (Exception e) {
            System.err.println("❌ Failed to extract data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void executeCustomQuery(String customQuery) {
        try {
            System.out.println("\nExecuting custom query...");
            System.out.println("Query: " + customQuery);
            
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(customQuery).build();
            TableResult result = bigQuery.query(queryConfig);
            
            System.out.println("Query completed! Rows: " + result.getTotalRows());
            
            // Print results
            result.iterateAll().forEach(row -> {
                row.forEach(val -> System.out.print(val.getValue() + "\t"));
                System.out.println();
            });
            
        } catch (Exception e) {
            System.err.println("❌ Custom query failed: " + e.getMessage());
        }
    }
}
