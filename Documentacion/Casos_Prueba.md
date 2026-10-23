# Casos de prueba automatizados – ITMentorSoft

Los tres casos **ya existen en Azure Test Plans**; aquí se diseñan con detalle para automatizarlos contra **https://itmentorsoft.com**. Los criterios de aceptación se citan del backlog sin modificarlos. Lo que se agregó al caso original de Azure aparece marcado como **[Precisión de automatización]**. Las diferencias entre el requisito y la aplicación están en `Pendientes_Integracion.md`, sección 3 (H1 a H6).

**Estado de ejecución: pendiente de la primera ejecución asistida** (el login requiere el código OTP que llega por correo).

Convención de resultados en Katalon:

| Estado | Significado |
|---|---|
| PASSED | Se cumplieron todas las verificaciones. Puede traer advertencias (*warning*) con observaciones sobre el requisito. |
| FAILED | Hay un defecto funcional. El mensaje del caso empieza con `DEFECTO`. |
| ERROR | La prueba **no se ejecutó**: falta configuración (`CONFIGURACIÓN PENDIENTE`) o no se cumplió una precondición, como el login u OTP. **No es un resultado funcional.** |

Inicio de sesión común (HU 1.1.2): `/login` → correo y contraseña → `/otp` → **el tester escribe el código recibido por correo** → área del rol con menú lateral visible.

---

## TC01 – Aislamiento de datos académicos entre estudiantes

| Campo | Detalle |
|---|---|
| Identificador | `TC01_HU122_Aislamiento_datos_academicos_entre_estudiantes`. Origen: **Azure #496** "Verificar aislamiento estricto de información académica y enmascaramiento con HTTP 404". |
| Historia de usuario | **HU 1.2.2 Acceso y privacidad en datos** (Azure #198). Habilitador HH 1.2.1 (#360). |
| Criterios de aceptación | **CA2:** "...retorna únicamente los datos asociados al identificador del usuario autenticado, nunca información de otros usuarios." **CA3:** "Dado que el usuario está autenticado, es un estudiante y solicita información de otro estudiante, entonces el sistema retorna un error 404 genérico informando que el usuario no existe." |
| Riesgo | RH01 / SQA R03: exposición de datos académicos de otro estudiante (IDOR). Criticidad 15. |
| Objetivo | Comprobar que un estudiante autenticado no puede obtener datos de otro estudiante, ni por la API ni manipulando la URL. |
| Tipo | Negativo (seguridad), precedido de controles positivos. |
| Precondiciones | Cuenta Estudiante (A) y, si `id_estudiante_b = AUTO`, cuenta Docente. Existe al menos otro estudiante (B). Contraseñas en `ITM_PWD_ESTUDIANTE` e `ITM_PWD_DOCENTE`. |
| Datos de entrada | `Data Files/TD_TC01_Aislamiento`: `id_estudiante_b` (`AUTO` o un id) y `codigo_http_esperado` (`404`). |

**Pasos**

1. **[Precisión de automatización]** Si `id_estudiante_b = AUTO`: iniciar sesión como Docente, leer la lista de estudiantes (`API_ESTUDIANTES_DOCENTE`) y cerrar la sesión.
2. Iniciar sesión como Estudiante A y leer su id de la sesión. Elegir como B el primer estudiante distinto de A.
3. Abrir `/student/dashboard` y verificar que el panel académico propio es visible (control positivo, CA2).
4. **[Precisión de automatización]** Pedir a la API el resumen de A (control positivo: la sesión es aceptada).
5. Con la sesión de A, pedir `API_RESUMEN_ESTUDIANTE` y `API_HISTORIAL_ESTUDIANTE` con el id de B (CA3).
6. Abrir `/teacher/students/{id de B}` (manipulación de URL) y verificar que el reporte de B no se renderiza.

**Resultado esperado:** A ve su panel. Las dos APIs responden **404** para B, sin datos. La URL del reporte de B no muestra el reporte.

| Resultado | Condición |
|---|---|
| Pasa | Se cumplen los pasos 3 a 6. |
| Falla | Una API responde 2xx para B (**defecto de seguridad**), responde un código distinto de 404 (CA3), o se renderiza el reporte de B. |
| ERROR | Falta configuración, falla el login/OTP, A no ve su panel, la API rechaza la sesión de A o no existe un Estudiante B. |

**Evidencia:** `Evidencias/TC01/*_01_resultados_propios.png`, `*_02_solicitud_resultados_estudiante_B.png` y `bitacora.txt` (ids, URLs, códigos HTTP y extracto de cada respuesta).

---

## TC02 – Restricción de acceso por rol a la administración de evaluaciones

| Campo | Detalle |
|---|---|
| Identificador | `TC02_HU213_Restriccion_acceso_por_rol_a_administracion_de_evaluaciones`. Origen: **Azure #466** "Verificar restricción de acceso al módulo de administración de evaluaciones según el rol del usuario". |
| Historia de usuario | **HU 2.1.3 Creación de rúbricas** (Azure #224). Relacionadas: HU 1.2.1 CA4 (#193), HH 1.2.1 CA1 (#360) y CR-008. |
| Criterio de aceptación | **HU 2.1.3 CA1:** "Dado que un usuario intenta acceder al módulo de administración de evaluaciones técnicas, cuando el sistema valida su rol, entonces permite el ingreso si es Docente o Administrador y muestra la lista de temas; en caso contrario, deniega el acceso con mensaje de autorización." |
| Riesgo | RH02 / SQA R03 + R01: un estudiante consulta o manipula el banco de preguntas y rúbricas que usa el LLM para calificar. Criticidad 15. |
| Objetivo | Verificar que el control de acceso se aplica al **acceso directo por URL** y en la **API**, no solo ocultando el menú. |
| Tipo | Negativo para Estudiante, con control positivo para Docente. Dirigido por datos. |
| Módulo en la aplicación | **Preguntas** del Docente: `/teacher/questions` (tabla ID, Pregunta, Dificultad, Categoría). API: `/assessments/all-questions`. |

**Datos de entrada:** `Data Files/TD_TC02_Roles`.

| Rol | Acceso esperado | Mensaje de denegación |
|---|---|---|
| ESTUDIANTE | DENEGADO | vacío: no se exige (hallazgo H2) |
| DOCENTE | PERMITIDO | — |

**Pasos (por cada fila)**

1. Iniciar sesión con el rol de la fila (login + OTP).
2. Abrir directamente `/teacher/questions`.
3. **[Precisión de automatización]** Consultar la API del banco de preguntas con la sesión del rol.
4. Según el rol:
   - **PERMITIDO:** verificar que la tabla de preguntas es visible.
   - **DENEGADO:** verificar que el módulo no se muestra y que la API responde 401, 403 o 404.
5. Cerrar la sesión.

| Resultado | Condición |
|---|---|
| Pasa | Todas las filas cumplen lo esperado. |
| Falla | El Estudiante ve el módulo o recibe datos de la API, o el Docente no ve la lista. |

Si el control positivo (Docente) falla, revise primero el entorno antes de interpretar la fila del Estudiante.

**Evidencia:** `Evidencias/TC02/*_<fila>_<ROL>_acceso_(permitido|denegado).png` y `bitacora.txt` (URL final y respuesta de la API).

---

## TC03 – Evaluación secuencial y persistencia de respuestas

| Campo | Detalle |
|---|---|
| Identificador | `TC03_HU211_Evaluacion_inicial_secuencial_y_persistencia_de_respuestas`. Origen: **Azure #434** "Validar interacción de preguntas de la evaluación inicial". |
| Historia de usuario | **HU 2.1.1 Desarrollo de evaluación** (Azure #212). Relacionada: CR-006 (#330, autosave incremental). |
| Criterios de aceptación | **CA2:** "...las muestra una a una en orden secuencial sin permitir saltar entre preguntas ni retroceder." **CA3:** "...cuando confirma su respuesta, entonces el sistema persiste la respuesta en base de datos asociada al identificador del estudiante, la evaluación y la pregunta." **CR-006:** "cada respuesta confirmada se persiste inmediatamente en base de datos. El estudiante puede retomar la evaluación desde donde quedó." |
| Riesgo | RH03: pérdida o alteración de respuestas de la evaluación, que es la entrada de la categorización ML y de la calificación LLM (R01). Criticidad 15. |
| Objetivo | Verificar la presentación secuencial y que la respuesta confirmada sobrevive a salir y volver al módulo. |
| Tipo | Positivo (flujo y reanudación). |
| Precondiciones | Cuenta Estudiante (contraseña en `ITM_PWD_ESTUDIANTE`). Hay al menos un tema con 2 o más preguntas. |
| Datos de entrada | `Data Files/TD_TC03_Respuestas`: respuesta abierta (CR-003). |

**Pasos**

1. Iniciar sesión y abrir `/student/assessments`. Si hay una evaluación en curso de otra ejecución, descartarla.
2. Pulsar **+ Nueva evaluación**, dejar el tema propuesto y pulsar **Comenzar evaluación**.
3. Verificar "Pregunta 1 de N" (N ≥ 2) y que **Anterior** está deshabilitado (CA2).
4. Escribir la respuesta 1 y pulsar **Siguiente** (CA3).
5. Verificar que aparece otra pregunta y el contador "Pregunta 2 de N" (CA2).
6. **[Precisión de automatización]** Ir al panel principal y volver a `/student/assessments`. Verificar el aviso "Tienes una evaluación en curso" con "1 de N respondidas" (CR-006).
7. Pulsar **Retomar** y verificar que continúa en la pregunta 2.
8. Pulsar **Anterior** y verificar que la respuesta 1 se conserva (CA3, CR-006).
9. **Limpieza:** volver al historial y descartar la evaluación. La evaluación no se envía.

**Resultado esperado:** preguntas presentadas una a una desde la 1; al volver, la evaluación sigue en curso, se reanuda en la pregunta 2 y la respuesta 1 está guardada.

| Resultado | Condición |
|---|---|
| Pasa | Se cumplen los pasos 3, 5, 6, 7 y 8. Puede traer la advertencia del hallazgo H4. |
| Falla | No empieza en la pregunta 1, "Anterior" está habilitado en la pregunta 1, no avanza tras "Siguiente", se pierde la evaluación en curso, no reanuda en la pregunta 2 o la respuesta 1 no se conserva. |
| ERROR | Falta configuración, falla el login/OTP o el sistema no genera las preguntas en `TIMEOUT_EVALUACION`. |

**Evidencia:** `Evidencias/TC03/*_01_pregunta_1.png`, `*_02_pregunta_2.png`, `*_03_evaluacion_en_curso.png`, `*_04_reingreso.png`, `*_05_respuesta_1_conservada.png` y `bitacora.txt` (enunciados y avance).

**Limitaciones**

- La aplicación guarda las respuestas en el navegador hasta que se envía la evaluación (hallazgo H5). La persistencia en base de datos por pregunta (CA3 literal) no se puede evidenciar desde la UI.
- La aplicación permite retroceder y saltar entre preguntas (hallazgo H4). El caso lo informa como advertencia, no como defecto, hasta que producto confirme el comportamiento vigente.
