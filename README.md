# GCP Test Project

Proyecto Java simple para pruebas con servicios de Google Cloud Platform (GCP).

## Configuración

1. **Credenciales de servicio**: 
   - Coloca tu archivo JSON de credenciales en: `credentials/service-account-key.json`

2. **Configuración en `application.yaml`**:
   - `gcp.project-id`: Tu project ID de GCP
   - `gcp.bigquery.dataset-id`: ID de tu dataset en BigQuery
   - `gcp.bigquery.view-name`: Nombre de tu vista a consultar

## Ejecución

```bash
# Compilar y ejecutar
./gradlew bootRun

# Solo compilar
./gradlew build
```

## Funcionalidades

- ✅ Test de conexión a BigQuery
- ✅ Extracción de los 10 registros más recientes
- ✅ Configuración lista para otros servicios GCP (Storage, PubSub)
- ✅ Soporte para PostgreSQL

## Estructura del proyecto

```
gcp-test-project/
├── build.gradle
├── src/main/java/com/gcptest/
│   ├── GcpTestApplication.java
│   ├── config/
│   │   └── GcpConfig.java
│   └── service/
│       └── BigQueryService.java
├── src/main/resources/
│   └── application.yaml
└── credentials/
    └── service-account-key.json (tu archivo)
```

## Notas importantes

- Los cargos se aplicarán al project-id configurado
- La cuenta de servicio debe tener permisos de BigQuery Data Viewer
- El query por defecto ordena por la primera columna (ajústalo según tu vista)
