# ITMentorSoft – Automatización de pruebas críticas con Katalon Studio

Proyecto Katalon Studio (Web UI, Groovy) con los **3 casos de prueba funcionales más críticos** de ITMentorSoft. Los casos se seleccionaron a partir del backlog de Azure DevOps y de la evaluación de riesgos del Documento Técnico SQA.

> **Estado actual:** la automatización está integrada con **https://itmentorsoft.com** (URL, rutas, localizadores, APIs y cuentas configurados) y compila con Katalon 11.6. Primera ejecución asistida: **2026-10-21, los 3 casos en PASSED** (ver `Documentacion/Informe_Ejecucion_Pruebas.md` y `Documentacion/Reporte_Pruebas.html`). El login tiene verificación en dos pasos: el código OTP se escribe en el navegador o se deja en `Evidencias/otp/codigo.txt`. Las contraseñas se configuran como variables de entorno (sección 4).

| Caso | Azure | HU | Riesgo | Qué verifica |
|---|---|---|---|---|
| `TC01_HU122_Aislamiento_datos_academicos_entre_estudiantes` | #496 | HU 1.2.2 CA2, CA3 | RH01 / R03 | Un estudiante no obtiene datos de otro, ni por la API (404 genérico) ni manipulando la URL. |
| `TC02_HU213_Restriccion_acceso_por_rol_a_administracion_de_evaluaciones` | #466 | HU 2.1.3 CA1 | RH02 / R03 + R01 | Solo el Docente entra al módulo de preguntas (`/teacher/questions`); el Estudiante es rechazado en la UI y en la API. |
| `TC03_HU211_Evaluacion_inicial_secuencial_y_persistencia_de_respuestas` | #434 | HU 2.1.1 CA2, CA3 | RH03 | Preguntas una a una desde la 1, y respuesta conservada al salir y retomar la evaluación. |

Más detalle:

- `Documentacion/Analisis_Riesgos.md`: priorización y justificación de la selección.
- `Documentacion/Casos_Prueba.md`: diseño completo de los casos.
- `Documentacion/Matriz_Trazabilidad.xlsx`: trazabilidad HU → CA → riesgo → caso → artefactos.
- `Documentacion/Pendientes_Integracion.md`: configuración, login con OTP, **hallazgos H1-H6** (diferencias entre requisito y aplicación) y checklist de ejecución.

## Estructura

```text
ITMentorSoft_Katalon_SQA/
├── ITMentorSoft_Katalon_SQA.prj        Definición del proyecto (Katalon 11.6, tipo WEBUI)
├── Profiles/                           Perfiles de ejecución: default (https://itmentorsoft.com) y Staging
├── Test Cases/                         TC01, TC02, TC03 (.tc: metadatos y Gherkin)
├── Scripts/<caso>/Script*.groovy       Código Groovy de cada caso
├── Test Suites/TS_Pruebas_Criticas.ts  Suite con los 3 casos (y TS_TC01..TS_TC03, una por caso)
├── Object Repository/                  Objetos por funcionalidad:
│   ├── Autenticacion/                    login y verificación OTP (HU 1.1.2)
│   ├── Privacidad_Datos/                 resultados académicos (HU 1.2.2)
│   ├── Administracion_Evaluaciones/      módulo de evaluaciones técnicas (HU 2.1.3)
│   └── Evaluacion_Inicial/               evaluación inicial (HU 2.1.1)
├── Data Files/                         TD_TC01_Aislamiento, TD_TC02_Roles, TD_TC03_Respuestas (.dat + .csv)
├── Keywords/itmentorsoft/
│   ├── Configuracion.groovy              detecta configuración pendiente y detiene con ERROR
│   ├── Autenticacion.groovy              login + OTP asistido por rol; contraseñas desde variables de entorno
│   ├── Api.groovy                        llamadas a la API con la sesión del navegador (control de acceso en backend)
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

### 2. URL, rutas y cuentas

Ya están en **Profiles → default**: `BASE_URL = https://itmentorsoft.com`, las rutas `RUTA_*`, las APIs `API_*` y los emails `USUARIO_ESTUDIANTE` y `USUARIO_DOCENTE`. El perfil **Staging** tiene las mismas rutas; complete su `BASE_URL` y sus cuentas si se usa.

### 3. Object Repository

Los objetos usan los localizadores CSS del frontend publicado (por ejemplo `input#email`, `input#otp`, `app-question-bank table.cuest__table`, `textarea#answer`). Si la interfaz cambia, ábralos con la aplicación abierta y use **Verify and Highlight**.

**Recomendación al equipo de desarrollo:** agregar atributos `data-testid` para que los localizadores no dependan de clases CSS.

### 4. Contraseñas y código OTP

Cree las variables de entorno y luego **reinicie Katalon**. En PowerShell:

```powershell
[Environment]::SetEnvironmentVariable('ITM_PWD_ESTUDIANTE', '<contraseña>', 'User')
[Environment]::SetEnvironmentVariable('ITM_PWD_DOCENTE',    '<contraseña>', 'User')
```

Las contraseñas no se escriben en el log: el keyword las envía directamente al campo.

Después de la contraseña, la aplicación envía un código al correo del usuario. Cuando el log muestre `ACCIÓN MANUAL`, escriba el código en el navegador que abrió Katalon y pulse **Verificar e ingresar**. El caso espera hasta `TIMEOUT_OTP` segundos (180). Por eso la suite **no puede ejecutarse desatendida** (por ejemplo, en CI) mientras exista el segundo factor.

### 5. Ejecutar un caso individual

Abra el caso en **Test Cases**, elija el navegador (por ejemplo Chrome) en el botón **Run** y pulse **Run**. Tenga el correo abierto para el OTP. El estado aparece en **Log Viewer** y **Job Progress**.

### 6. Ejecutar la Test Suite

Abra **Test Suites → TS_Pruebas_Criticas** y pulse **Run** con el navegador deseado.

Para ejecutarla por consola se necesita la API key de Katalon y una licencia que permita *katalonc*. Igual hay que escribir los OTP en la ventana del navegador:

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
| **PASSED** | Se cumplieron todas las verificaciones del caso. | Archivar la evidencia y registrar el resultado en Azure Test Plans (manual). Revisar las advertencias (*warning*): señalan diferencias entre el requisito y la aplicación (hallazgos H2 y H4). |
| **FAILED** | Defecto funcional. El mensaje empieza con `DEFECTO`. | Revisar la evidencia y reportar el bug con el CA incumplido. |
| **ERROR** + `CONFIGURACIÓN PENDIENTE` | La prueba **no se ejecutó**. El mensaje lista lo que falta. | Completar la configuración. **No es un resultado funcional.** |
| **ERROR** + `Precondición no cumplida` | No se pudo preparar el escenario (login, OTP no ingresado a tiempo, datos). | Revisar cuentas, contraseñas, el OTP y la captura de evidencia. |

**Cómo comprobar que el oráculo funciona:** antes de confiar en un PASSED, provoque un fallo controlado. Por ejemplo, cambie temporalmente la fila ESTUDIANTE de `TD_TC02_Roles.csv` a `PERMITIDO` y confirme que TC02 termina en FAILED (el estudiante no ve la lista). Después restaure el valor.

## Restricciones respetadas

- Azure DevOps se consultó solo en modo lectura.
- El documento SQA original no se modificó.
- No hay credenciales en el proyecto.
- Los resultados informados salen de los logs reales de Katalon. Para regenerar el informe y el reporte HTML: `python -I Documentacion/herramientas/generar_reportes.py`.
