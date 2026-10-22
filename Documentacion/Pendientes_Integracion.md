# Pendientes para ejecutar las pruebas

Mientras falte algo de esta lista, los casos terminan en **ERROR** con el mensaje `CONFIGURACIÓN PENDIENTE`, que enumera exactamente lo que falta. Eso significa que la prueba **no se ejecutó**; no indica un defecto ni un resultado funcional.

## 1. Entorno y URL (Profiles/default.glbl y Profiles/Staging.glbl)

| Variable | Qué poner | Usado por |
|---|---|---|
| `BASE_URL` | URL del frontend Angular, sin `/` final. | Todos |
| `RUTA_RESULTADOS_PROPIOS` | Ruta donde el estudiante ve sus resultados académicos. | TC01 |
| `RUTA_RESULTADOS_ESTUDIANTE` | Ruta de resultados por estudiante con el marcador `{idEstudiante}`, por ejemplo `/<ruta-real>/{idEstudiante}`. | TC01 |
| `RUTA_ADMIN_EVALUACIONES` | Ruta del módulo de administración de evaluaciones técnicas. | TC02 |
| `RUTA_EVALUACION_INICIAL` | Ruta del módulo de evaluación inicial. | TC03 |
| `TIMEOUT_CORTO` / `TIMEOUT_LARGO` | Ajustar si el entorno es lento (valores actuales: 5 s y 20 s). | Todos |

## 2. Cuentas de prueba

En el perfil va solo el email. La contraseña va en una **variable de entorno del sistema**, nunca en el proyecto.

| Cuenta | Variable de perfil (email) | Variable de entorno (contraseña) | Condición |
|---|---|---|---|
| Estudiante A | `USUARIO_ESTUDIANTE_A` | `ITM_PWD_ESTUDIANTE_A` | Con al menos una evaluación registrada (TC01). También es el rol ESTUDIANTE de TC02. |
| Estudiante B | — (solo su id y un dato visible en TD_TC01) | — | Con resultados registrados. |
| Estudiante de evaluación | `USUARIO_ESTUDIANTE_EVAL` | `ITM_PWD_ESTUDIANTE_EVAL` | **Sin evaluación inicial iniciada.** Se consume en cada ejecución de TC03. |
| Docente | `USUARIO_DOCENTE` | `ITM_PWD_DOCENTE` | — |
| Administrador | `USUARIO_ADMINISTRADOR` | `ITM_PWD_ADMINISTRADOR` | — |

Después de crear las variables de entorno, reinicie Katalon Studio para que las lea.

## 3. Localizadores (Object Repository)

Hay 11 objetos con selector provisional `[data-todo="TODO_..."]`, que nunca coincide con ningún elemento. Reemplácelos por el localizador real; lo ideal es un atributo estable como `data-testid` o `id`:

- `Autenticacion/Page_Inicio/btn_Login`
- `Autenticacion/Page_Login/txt_Email`
- `Autenticacion/Page_Login/txt_Password`
- `Autenticacion/Page_Login/btn_Enviar`
- `Autenticacion/Page_Bienvenida/cnt_Bienvenida`
- `Privacidad_Datos/Page_ResultadosAcademicos/cnt_ResultadosAcademicos`
- `Administracion_Evaluaciones/Page_AdminEvaluaciones/tbl_ListaTemas`
- `Evaluacion_Inicial/Page_EvaluacionInicial/btn_IniciarEvaluacion`
- `Evaluacion_Inicial/Page_EvaluacionInicial/lbl_EnunciadoPregunta`
- `Evaluacion_Inicial/Page_EvaluacionInicial/txt_Respuesta`
- `Evaluacion_Inicial/Page_EvaluacionInicial/btn_ConfirmarRespuesta`

Revisar también, aunque **no** bloquea la ejecución: `Evaluacion_Inicial/Page_EvaluacionInicial/btn_NavegacionNoPermitida` usa un XPath genérico por texto (Anterior, Atrás, Saltar, Omitir). Ajústelo si la UI usa iconos o un indicador de preguntas clicable.

**Recomendación al equipo de desarrollo:** agregar atributos `data-testid` a estos elementos en Angular para que los localizadores sean estables.

## 4. Datos de prueba (Data Files)

| Archivo | Pendiente |
|---|---|
| `TD_TC01_Aislamiento.csv` | `id_estudiante_b`, `marcador_dato_estudiante_b` (un dato visible y único de B, no su ID) y `mensaje_404_esperado` (texto real del 404 genérico de HU 1.2.2 CA3). |
| `TD_TC02_Roles.csv` | Confirmar que el mensaje `autorización insuficiente` (de HU 1.2.1 CA4) es el que muestra el módulo de evaluaciones. HU 2.1.3 CA1 solo dice "mensaje de autorización". |
| `TD_TC03_Respuestas.csv` | Listo. Ajustar si la evaluación exige un formato de respuesta específico. |

## 5. Aclaraciones pendientes con el equipo de producto

1. Texto exacto del error 404 genérico (HU 1.2.2 CA3) y del mensaje de autorización del módulo de evaluaciones (HU 2.1.3 CA1).
2. Si la evaluación inicial se reanuda en la pregunta pendiente (CR-006) o si al reingresar reinicia. TC03 asume lo que dice CR-006.
3. Si las preguntas de la evaluación inicial son abiertas (CR-003, estado Implementado) o de selección. `txt_Respuesta` asume respuesta abierta.

## 6. Pruebas complementarias recomendadas (fuera del alcance de los 3 casos de UI)

| Riesgo | Por qué no se cubre con UI | Prueba recomendada |
|---|---|---|
| R01 / RH07 – Alucinación del LLM | No hay oráculo en la UI y la salida no es determinista. | Benchmark de prompts contra rúbricas patrón docentes, con umbral ≥ 95 % (SQA). |
| R01 / RH08 – *Prompt injection* (MC 2.1.2) | Requiere comparar la nota contra una calificación patrón. | Conjunto de respuestas adversarias con nota esperada, más verificación del registro del intento. |
| R02 / RH09 – Falla del LLM o del modelo de ML | Requiere inyectar fallas. | Pruebas de integración con el doble de prueba de HH 2.1.2 (timeout o error), verificando la DLQ de HH 2.1.1 y HU 2.2.1 CA5 y CA7. |
| R03 – Códigos HTTP 401, 403 y 404 (HU 1.2.2 CA1 y CA3) | La UI muestra mensajes, no códigos de estado. | Pruebas de API, que pueden hacerse en este mismo proyecto Katalon con Web Service Requests cuando exista la especificación OpenAPI. |
| HU 2.1.1 CA3 – Persistencia en base de datos | La UI solo evidencia la reanudación. | Consulta SQL a PostgreSQL de la respuesta por estudiante, evaluación y pregunta, cuando haya credenciales de solo lectura del entorno de pruebas. |
| R04 – Latencia | No es funcional. | Pruebas de carga con K6 o JMeter (SQA). |

## 7. Checklist para la primera ejecución real

- [ ] Abrir el proyecto en Katalon Studio 11.6 y comprobar que no hay errores de compilación (panel *Problems*).
- [ ] Completar el perfil `default` (o `Staging`), las variables de entorno, los localizadores y los datos.
- [ ] Ejecutar cada caso de forma individual y confirmar que ya no termina en `CONFIGURACIÓN PENDIENTE`.
- [ ] Verificar los controles positivos (TC01 paso 3, TC02 filas DOCENTE y ADMINISTRADOR) antes de interpretar los resultados negativos.
- [ ] Ejecutar `TS_Pruebas_Criticas` y archivar el reporte y la carpeta `Evidencias/`.
- [ ] Registrar los resultados reales en Azure Test Plans (casos #496, #466 y #434). Esto se hace manualmente; el proyecto no escribe en Azure DevOps.
