# ITMentorSoft – Automatización de pruebas funcionales de sistema con Katalon Studio

Proyecto Katalon Studio (Web UI, Groovy) con **20 casos de prueba funcionales de sistema** de ITMentorSoft (https://itmentorsoft.com). Los 3 primeros (TC01-TC03) son los casos críticos seleccionados por riesgo; TC04-TC20 automatizan el resto de casos de Azure DevOps que se pueden probar como caja negra con las cuentas de Estudiante y Docente. La clasificación de los **43 casos de Azure** (20 automatizados, 14 parciales, 9 no automatizables) está en `Documentacion/Inventario_Casos_Azure.md`.

> **Estado actual (2026-10-23):** los 20 casos se ejecutaron uno a uno: **17 PASSED y 3 FAILED por defectos reales** de la aplicación (TC07 vigencia del JWT, TC09 vigencia del enlace de recuperación, TC11 evaluaciones descartadas que quedan registradas). Ver `Documentacion/Informe_Ejecucion_Pruebas.md` y `Documentacion/Reporte_Pruebas.html`. El login tiene verificación en dos pasos (OTP por correo): el código se escribe en el navegador o se deja en `Evidencias/otp/codigo.txt`, y la sesión obtenida se reutiliza en los casos siguientes.

| Caso | Azure | Qué verifica |
|---|---|---|
| `TC01_HU122_Aislamiento_datos_academicos_entre_estudiantes` | #496 | Un estudiante no obtiene datos de otro, ni por la API (404 genérico) ni manipulando la URL. |
| `TC02_HU213_Restriccion_acceso_por_rol_a_administracion_de_evaluaciones` | #466 | Solo el Docente entra al banco de preguntas; el Estudiante es rechazado en la UI y en la API. |
| `TC03_HU211_Evaluacion_inicial_secuencial_y_persistencia_de_respuestas` | #434 | Preguntas una a una y respuesta conservada al salir y retomar la evaluación. |
| `TC04_AZ483_Login_errores_de_formato_y_mensaje_generico` | #483 | Validaciones del login y mensaje genérico ante credenciales incorrectas. |
| `TC05_AZ478_Registro_limites_de_longitud_y_formato` | #478 | Longitudes, patrones y complejidad de contraseña del registro (dirigido por datos). |
| `TC06_AZ482_Registro_unicidad_de_usuario_y_correo` | #482 | Rechazo del registro con usuario y correo ya existentes. |
| `TC07_AZ485_Login_exitoso_cookie_segura_y_redireccion_por_rol` | #485 | Login con OTP por rol: redirección, cookie HttpOnly/Secure y vigencia de 15 min del JWT. |
| `TC08_AZ486_Recuperacion_mensaje_generico_y_vigencia` | #486 | Mensaje genérico de recuperación para correo registrado y no registrado. |
| `TC09_AZ488_Restablecimiento_enlace_invalido_y_complejidad` | #488, #486 | Enlace sin token, token falso, enlace real vencido y complejidad de la nueva contraseña. |
| `TC10_AZ494_Middleware_401_y_403_segun_token_y_rol` | #494, #489 | 401 sin token o con token inválido, 403 por rol y vistas de otro rol (dirigido por datos). |
| `TC11_AZ422_Ingreso_al_modulo_de_evaluacion_inicial` | #422 | Temas, preguntas por sección, selección aleatoria y que descartar no registre la evaluación. |
| `TC12_AZ435_AZ457_Finalizacion_y_retroalimentacion_de_la_evaluacion` | #435, #457 | Envío completo, calificación y retroalimentación pregunta a pregunta. |
| `TC13_AZ426_AZ451_AZ475_Progreso_perfil_y_categoria_del_estudiante` | #426, #451, #459, #465, #475 | Nivel, perfil de conocimiento, feedback y gráfica de progreso contra la API. |
| `TC14_AZ436_AZ446_AZ461_Ruta_de_aprendizaje_y_recomendaciones` | #436, #446, #461 | Ruta personalizada, totales y avance al marcar un recurso como visto. |
| `TC15_AZ448_Calificacion_de_recursos_recomendados` | #448 | Calificación 1-5, modificación sin duplicados y persistencia. |
| `TC16_AZ450_AZ500_Acceso_por_rol_a_reportes_y_datos_consolidados` | #450, #500 | Reportes y JSON consolidado para el Docente; denegación al Estudiante. |
| `TC17_AZ502_AZ476_AZ455_Reportes_graficos_y_filtro_por_nivel` | #502, #418, #476, #455 | Métricas y gráficos consistentes con la API y filtro por nivel. |
| `TC18_AZ440_AZ444_Reportes_de_recursos_y_banco_de_preguntas` | #440, #444 | Recursos mejor/peor valorados, gráficos del banco de preguntas y progreso por tema. |
| `TC19_AZ430_Gestion_de_recursos_validaciones_y_consulta` | #430 | Validaciones del formulario de recursos, filtro por categoría, búsqueda y edición. |
| `TC20_AZ468_Banco_de_preguntas_paginacion_filtros_y_versiones` | #468, #499 | Paginación, filtros, validaciones y historial de versiones del banco de preguntas. |

`Test Cases/Herramientas/TC00_Exploracion_DOM` no es un caso de prueba: guarda el HTML, capturas y respuestas de API de las páginas de un rol (`Evidencias/dom/`) para mantener los localizadores cuando cambia el frontend.

Más detalle:

- `Documentacion/Analisis_Riesgos.md`: priorización y justificación de la selección.
- `Documentacion/Casos_Prueba.md`: diseño completo de los casos.
- `Documentacion/Matriz_Trazabilidad.xlsx`: trazabilidad HU → CA → riesgo → caso → artefactos.
- `Documentacion/Inventario_Casos_Azure.md`: los 43 casos de Azure, su clasificación y el caso Katalon que los cubre.
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

Suites disponibles:

- `TS_Pruebas_Criticas`: TC01-TC03 (casos críticos por riesgo).
- `TS_TC01` … `TS_TC20`: un caso por suite, para ejecutarlos de forma individual (así se ejecutaron en el informe).
- `TS_Regresion_Completa`: los 20 casos en el orden recomendado. TC07 va primero porque hace el login con OTP de cada rol y deja las sesiones que reutilizan los demás; TC12 va antes de TC13-TC15 porque les deja una evaluación calificada.

Abra la suite en **Test Suites** y pulse **Run** con el navegador deseado.

**Sesiones reutilizadas:** después de un login con OTP, el keyword `Autenticacion` guarda la sesión en `Evidencias/sesiones/` (no se versiona) y los casos siguientes la restauran mientras siga vigente. Si la sesión venció, el caso vuelve a pedir el OTP. Para forzar un login nuevo, borre esa carpeta.

**TC09 (enlace real vencido):** ejecute TC08, copie de `USUARIO_ESTUDIANTE` el enlace del correo "Solicitud de recuperación de contraseña" y escríbalo en `Data Files/TD_TC09_Restablecer.csv` (columna `enlace_vencido`, solo la ruta `/reset-password?token=…&trx=…`, y `emitido` con la hora del correo). TC09 espera a que el enlace supere 5 minutos y envía la contraseña actual, así que no la cambia. Vacíe la columna después: el token es de un solo uso. Sin ese dato TC09 omite la verificación y deja una advertencia.

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
