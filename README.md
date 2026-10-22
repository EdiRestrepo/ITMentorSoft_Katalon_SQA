# ITMentorSoft – Automatización de pruebas críticas con Katalon Studio

Proyecto Katalon Studio (Web UI, Groovy) con los **3 casos de prueba funcionales más críticos** de ITMentorSoft. Los casos se seleccionaron a partir del backlog de Azure DevOps y de la evaluación de riesgos del Documento Técnico SQA.

> **Estado actual:** la automatización está implementada y compila, pero **ningún caso se ha ejecutado** porque la aplicación web aún no está disponible. Mientras falte la URL, los localizadores, los datos o las cuentas, cada caso termina en **ERROR – CONFIGURACIÓN PENDIENTE**, nunca en PASSED.

| Caso | Azure | HU | Riesgo | Qué verifica |
|---|---|---|---|---|
| `TC01_HU122_Aislamiento_datos_academicos_entre_estudiantes` | #496 | HU 1.2.2 CA2, CA3 | RH01 / R03 | Un estudiante no puede ver resultados de otro, ni manipulando la URL (404 genérico). |
| `TC02_HU213_Restriccion_acceso_por_rol_a_administracion_de_evaluaciones` | #466 | HU 2.1.3 CA1 | RH02 / R03 + R01 | Solo Docente y Administrador entran a la administración de evaluaciones. |
| `TC03_HU211_Evaluacion_inicial_secuencial_y_persistencia_de_respuestas` | #434 | HU 2.1.1 CA2, CA3 | RH03 | Evaluación sin saltos ni retroceso, y respuesta persistida al reingresar. |

Más detalle:

- `Documentacion/Analisis_Riesgos.md`: priorización y justificación de la selección.
- `Documentacion/Casos_Prueba.md`: diseño completo de los casos.
- `Documentacion/Matriz_Trazabilidad.xlsx`: trazabilidad HU → CA → riesgo → caso → artefactos.
- `Documentacion/Pendientes_Integracion.md`: todo lo que falta para ejecutar.

## Estructura

```text
ITMentorSoft_Katalon_SQA/
├── ITMentorSoft_Katalon_SQA.prj        Definición del proyecto (Katalon 11.6, tipo WEBUI)
├── Profiles/                           Perfiles de ejecución: default (local) y Staging
├── Test Cases/                         TC01, TC02, TC03 (.tc: metadatos y Gherkin)
├── Scripts/<caso>/Script*.groovy       Código Groovy de cada caso
├── Test Suites/TS_Pruebas_Criticas.ts  Suite con los 3 casos
├── Object Repository/                  Objetos por funcionalidad:
│   ├── Autenticacion/                    login (HU 1.1.2)
│   ├── Privacidad_Datos/                 resultados académicos (HU 1.2.2)
│   ├── Administracion_Evaluaciones/      módulo de evaluaciones técnicas (HU 2.1.3)
│   └── Evaluacion_Inicial/               evaluación inicial (HU 2.1.1)
├── Data Files/                         TD_TC01_Aislamiento, TD_TC02_Roles, TD_TC03_Respuestas (.dat + .csv)
├── Keywords/itmentorsoft/
│   ├── Configuracion.groovy              detecta configuración pendiente y detiene con ERROR
│   ├── Autenticacion.groovy              login por rol; contraseñas desde variables de entorno
│   └── Evidencia.groovy                  capturas y bitácora por caso
├── Documentacion/
└── Evidencias/                         (se crea al ejecutar) capturas y bitácora por caso
```

Katalon genera al abrir el proyecto los archivos `.classpath`, `.project`, `Libs/` y `bin/`; no se versionan.

## Guía de uso en Katalon Studio

### 1. Abrir el proyecto

1. En Katalon Studio 11.6 elija **File → Open Project…**.
2. Seleccione la carpeta `ITMentorSoft_Katalon_SQA` o el archivo `ITMentorSoft_Katalon_SQA.prj`.
3. Espere a que termine la indexación y revise el panel **Problems**: no debe haber errores de compilación.

### 2. Configurar la URL y las rutas

1. Abra **Profiles → default**, o **Staging** para el entorno en AWS.
2. Complete `BASE_URL` (sin `/` final) y las rutas `RUTA_*`.
3. `RUTA_RESULTADOS_ESTUDIANTE` debe contener el marcador `{idEstudiante}`.
4. Para ejecutar en Staging, seleccione el perfil en la barra de ejecución (lista desplegable junto a **Run**).

### 3. Actualizar el Object Repository

1. Con la aplicación disponible, abra cada objeto marcado con la etiqueta (*tag*) `TODO` (11 objetos).
2. En **Selected Locator** reemplace `[data-todo="TODO_..."]` por el localizador real. Puede usar **Spy Web** para capturarlo.
3. Pulse **Verify and Highlight** con la página abierta para comprobar que el localizador encuentra el elemento.
4. Revise `btn_NavegacionNoPermitida` (etiqueta `REVISAR`). Es un XPath genérico.

### 4. Configurar usuarios y datos de prueba

1. En el perfil complete los emails de `USUARIO_*`.
2. Cree las variables de entorno con las contraseñas y luego **reinicie Katalon**. En PowerShell:

   ```powershell
   [Environment]::SetEnvironmentVariable('ITM_PWD_ESTUDIANTE_A',   '<contraseña>', 'User')
   [Environment]::SetEnvironmentVariable('ITM_PWD_ESTUDIANTE_EVAL','<contraseña>', 'User')
   [Environment]::SetEnvironmentVariable('ITM_PWD_DOCENTE',        '<contraseña>', 'User')
   [Environment]::SetEnvironmentVariable('ITM_PWD_ADMINISTRADOR',  '<contraseña>', 'User')
   ```

   Las contraseñas no se escriben en el log: el keyword las envía directamente al campo.
3. Edite los CSV de `Data Files/` (los valores `TODO`). Para comprobar el contenido, ábralos desde Katalon (doble clic en el Data File).

### 5. Ejecutar un caso individual

Abra el caso en **Test Cases**, elija el navegador (por ejemplo Chrome) en el botón **Run** y pulse **Run**. El estado aparece en **Log Viewer** y **Job Progress**.

### 6. Ejecutar la Test Suite

Abra **Test Suites → TS_Pruebas_Criticas** y pulse **Run** con el navegador deseado.

Para ejecutarla por consola (CI), se necesita la API key de Katalon y una licencia que permita *katalonc*:

```powershell
& "$env:USERPROFILE\.katalon\packages\KS-11.6.0\katalonc.exe" -noSplash -runMode=console `
  -projectPath="<ruta>\ITMentorSoft_Katalon_SQA\ITMentorSoft_Katalon_SQA.prj" `
  -testSuitePath="Test Suites/TS_Pruebas_Criticas" -executionProfile="Staging" `
  -browserType="Chrome" -retry=0 -apiKey="<API_KEY>"
```

### 7. Evidencias e informes

- **Reporte de Katalon:** `Reports/<fecha>/TS_Pruebas_Criticas/...`, o la vista **Reports** del explorador. Para exportar HTML, CSV o PDF, active los formatos en **Project → Settings → Plugins → Report** o use **Export** desde el reporte.
- **Evidencias propias:** `Evidencias/<TC>/`, con capturas por paso (`<fecha>_<paso>.png`) y `bitacora.txt`.
- Katalon captura además la pantalla automáticamente cuando falla un paso.

### 8. Interpretar los resultados

| Estado | Significado | Qué hacer |
|---|---|---|
| **PASSED** | Se cumplieron todas las verificaciones del caso. | Archivar la evidencia y registrar el resultado en Azure Test Plans (manual). |
| **FAILED** | Defecto funcional. El mensaje empieza con `DEFECTO`. | Revisar la evidencia y reportar el bug con el CA incumplido. |
| **ERROR** + `CONFIGURACIÓN PENDIENTE` | La prueba **no se ejecutó**. El mensaje lista lo que falta. | Completar la configuración. **No es un resultado funcional.** |
| **ERROR** + `Precondición no cumplida` | No se pudo preparar el escenario (login, datos). | Revisar cuentas, datos y localizadores. |

**Cómo comprobar que el oráculo funciona:** antes de confiar en un PASSED, provoque un fallo controlado. Por ejemplo, cambie temporalmente `marcador_dato_estudiante_b` por un texto que sí aparezca en la página de A y confirme que TC01 termina en FAILED. Después restaure el valor.

## Restricciones respetadas

- Azure DevOps se consultó solo en modo lectura.
- El documento SQA original no se modificó.
- No hay credenciales en el proyecto.
- No se informan resultados de ejecución, porque no se ha ejecutado ninguno.
