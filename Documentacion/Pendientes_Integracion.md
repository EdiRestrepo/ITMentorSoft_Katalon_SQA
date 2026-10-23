# Configuración, ejecución y hallazgos de integración

Los tres casos están integrados con la aplicación publicada en **https://itmentorsoft.com**. Los localizadores, las rutas y las APIs se tomaron del frontend Angular desplegado (rutas, guards, servicios y plantillas de los componentes).

Si falta algo de la sección 2, el caso termina en **ERROR** con el mensaje `CONFIGURACIÓN PENDIENTE` y la lista de lo que falta. Eso significa que la prueba **no se ejecutó**; no es un defecto.

## 1. Cómo funciona el inicio de sesión (afecta a los tres casos)

1. `/login`: correo, contraseña y botón **Iniciar sesión**.
2. `/otp`: **verificación en dos pasos**. El sistema envía al correo del usuario un código de 6 caracteres (0-9, A-F), de un solo uso.
3. Redirección al área del rol: `/student`, `/teacher` o `/admin`.

El código OTP no se puede automatizar desde el proyecto sin acceso al buzón, así que **la ejecución es asistida**: cuando el caso llega a `/otp`, el log muestra `ACCIÓN MANUAL: escriba en el navegador el código OTP…`. El tester abre su correo, escribe el código en la ventana que abrió Katalon y pulsa **Verificar e ingresar**. El caso espera hasta `TIMEOUT_OTP` segundos (180 por defecto) y continúa solo.

| Caso | Inicios de sesión (OTP) por ejecución |
|---|---|
| TC01 | 2: Docente (para obtener el id de otro estudiante) y Estudiante. 1 si se fija `id_estudiante_b` en el Data File. |
| TC02 | 2: Estudiante y Docente. |
| TC03 | 1: Estudiante. |

El OTP del Docente llega al correo institucional `@correo.itm.edu.co`.

## 2. Configuración

### Perfil `default` (ya configurado)

| Variable | Valor |
|---|---|
| `BASE_URL` | `https://itmentorsoft.com` |
| `RUTA_LOGIN` | `/login` |
| `RUTA_RESULTADOS_PROPIOS` | `/student/dashboard` |
| `RUTA_RESULTADOS_ESTUDIANTE` | `/teacher/students/{idEstudiante}` |
| `RUTA_ADMIN_EVALUACIONES` | `/teacher/questions` |
| `RUTA_EVALUACION_INICIAL` | `/student/assessments` |
| `API_ESTUDIANTES_DOCENTE` | `/reports/students?page=0&page_size=50` |
| `API_RESUMEN_ESTUDIANTE` | `/reports/student_summary?id={idEstudiante}` |
| `API_HISTORIAL_ESTUDIANTE` | `/assessments/summary?student_id={idEstudiante}&page=0&page_size=20` |
| `API_BANCO_PREGUNTAS` | `/assessments/all-questions?page=0&page_size=5` |
| `USUARIO_ESTUDIANTE` | `edisonestival@gmail.com` |
| `USUARIO_DOCENTE` | `edisonrestrepo68991@correo.itm.edu.co` |
| `TIMEOUT_CORTO` / `TIMEOUT_LARGO` / `TIMEOUT_OTP` / `TIMEOUT_EVALUACION` | 5 / 20 / 180 / 90 s |

El perfil `Staging` tiene las mismas rutas; falta su `BASE_URL` y sus cuentas.

### Contraseñas (variables de entorno, una sola vez por equipo)

Las contraseñas **no** están en el proyecto. En PowerShell:

```powershell
[Environment]::SetEnvironmentVariable('ITM_PWD_ESTUDIANTE', '<contraseña del estudiante>', 'User')
[Environment]::SetEnvironmentVariable('ITM_PWD_DOCENTE',    '<contraseña del docente>',    'User')
```

Después **reinicie Katalon Studio** para que las lea.

### Data Files

| Archivo | Contenido |
|---|---|
| `TD_TC01_Aislamiento.csv` | `id_estudiante_b = AUTO`: el caso entra como Docente y toma el primer estudiante distinto del Estudiante A. Puede poner un id fijo para evitar ese login. `codigo_http_esperado = 404` (HU 1.2.2 CA3). |
| `TD_TC02_Roles.csv` | `ESTUDIANTE → DENEGADO` y `DOCENTE → PERMITIDO`. `mensaje_denegacion_esperado` vacío: ver hallazgo H2. |
| `TD_TC03_Respuestas.csv` | Respuesta abierta de prueba (máximo 600 caracteres). |

## 3. Hallazgos de la integración (para revisar con producto)

| Id | Caso | Requisito en Azure | Comportamiento de la aplicación | Tratamiento en la automatización |
|---|---|---|---|---|
| H1 | Todos | HU 1.1.2: login con email y contraseña. | Hay un segundo factor (OTP por correo). | Login asistido: el tester escribe el OTP. |
| H2 | TC02 | HU 2.1.3 CA1: denegar "con mensaje de autorización". | El guard de Angular redirige a `/login` sin mensaje. | Se verifica que no se muestre el módulo y que la API rechace la petición. La falta de mensaje queda como **advertencia** en el reporte. Para exigirlo, escriba el texto en `mensaje_denegacion_esperado`. |
| H3 | TC02 | HU 2.1.3 CA1: acceso para Docente **y Administrador**. | `/teacher/**` solo admite el rol `teacher`; el administrador tiene su propia área (`/admin`). | Se quitó la fila ADMINISTRADOR (además no hay cuenta de prueba). |
| H4 | TC03 | HU 2.1.1 CA2: "sin permitir saltar entre preguntas ni retroceder". | Hay botón **Anterior** (deshabilitado solo en la pregunta 1) y el panel "Tus preguntas" permite saltar. La propia UI dice "Puedes volver a una pregunta anterior cuando quieras". | Se verifica que la evaluación empiece en la pregunta 1 con "Anterior" deshabilitado. La posibilidad de retroceder queda como **advertencia**. |
| H5 | TC03 | HU 2.1.1 CA3: cada respuesta confirmada se persiste en base de datos. | Durante la evaluación las respuestas se guardan en el navegador (`localStorage`, borrador de 24 h) y se envían al backend al pulsar **Enviar evaluación**. | Se verifica la reanudación (CR-006): evaluación en curso, "Retomar" en la pregunta pendiente y respuesta 1 conservada. La persistencia en base de datos por pregunta no se puede evidenciar desde la UI. |
| H6 | TC01 | HU 1.2.2 CA3: 404 genérico al pedir datos de otro estudiante. | Las vistas de otros estudiantes están en el área docente; el guard redirige a `/login`. | El caso verifica el control en la **API** (el punto donde se produce un IDOR) y además la URL directa. |

## 4. Efectos sobre las cuentas

- **TC03** genera una evaluación nueva (el backend crea el registro de la evaluación), responde una pregunta y **no la envía**: no se califica ni aparece en el historial. Al final descarta la evaluación en curso. Si una ejecución se interrumpe, la siguiente descarta la evaluación pendiente antes de empezar.
- **TC01** y **TC02** solo consultan datos.

## 5. Pruebas complementarias recomendadas (fuera del alcance de los 3 casos de UI)

| Riesgo | Por qué no se cubre con UI | Prueba recomendada |
|---|---|---|
| R01 / RH07 – Alucinación del LLM | No hay oráculo en la UI y la salida no es determinista. | Benchmark de prompts contra rúbricas patrón docentes, con umbral ≥ 95 % (SQA). |
| R01 / RH08 – *Prompt injection* (MC 2.1.2) | Requiere comparar la nota contra una calificación patrón. | Conjunto de respuestas adversarias con nota esperada, más verificación del registro del intento. |
| R02 / RH09 – Falla del LLM o del modelo de ML | Requiere inyectar fallas. | Pruebas de integración con el doble de prueba de HH 2.1.2 (timeout o error), verificando la DLQ de HH 2.1.1 y HU 2.2.1 CA5 y CA7. |
| R03 – Códigos HTTP 401, 403 y 404 en todos los endpoints | TC01 y TC02 cubren solo algunos. | Suite de API (Web Service Requests de Katalon) cuando exista la especificación OpenAPI. |
| HU 2.1.1 CA3 – Persistencia en base de datos | Ver H5. | Consulta SQL de solo lectura a PostgreSQL después de enviar una evaluación. |
| R04 – Latencia | No es funcional. | Pruebas de carga con K6 o JMeter (SQA). |

## 6. Checklist de ejecución

- [ ] Crear las variables de entorno `ITM_PWD_ESTUDIANTE` e `ITM_PWD_DOCENTE` y reiniciar Katalon.
- [ ] Abrir el proyecto y comprobar que el panel *Problems* no muestra errores.
- [ ] Tener abiertos los dos buzones de correo para leer los OTP.
- [ ] Ejecutar cada caso de forma individual con el perfil `default` y Chrome.
- [ ] Verificar los controles positivos (TC01: panel propio; TC02: fila DOCENTE) antes de interpretar los negativos.
- [ ] Ejecutar `TS_Pruebas_Criticas` y archivar el reporte y la carpeta `Evidencias/`.
- [ ] Registrar los resultados en Azure Test Plans (#496, #466 y #434). Es manual; el proyecto no escribe en Azure DevOps.
