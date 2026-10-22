# Casos de prueba automatizados – ITMentorSoft

Los tres casos **ya existen en Azure Test Plans**; aquí se diseñan con detalle para automatizarlos. Los criterios de aceptación se citan del backlog sin modificarlos. Lo que se agregó al caso original de Azure aparece marcado como **[Precisión de automatización]**: son datos concretos, controles positivos y verificaciones negativas que el caso original no detallaba.

**Estado de ejecución de los tres: NO EJECUTADOS.** La aplicación web todavía no está disponible.

Convención de resultados en Katalon:

| Estado | Significado |
|---|---|
| PASSED | Se cumplieron todas las verificaciones. |
| FAILED | Hay un defecto funcional. El mensaje del caso empieza con `DEFECTO`. |
| ERROR | La prueba **no se ejecutó**: falta configuración (`CONFIGURACIÓN PENDIENTE`) o no se cumplió una precondición, como el inicio de sesión. **No es un resultado funcional.** |

---

## TC01 – Aislamiento de datos académicos entre estudiantes

| Campo | Detalle |
|---|---|
| Identificador | `TC01_HU122_Aislamiento_datos_academicos_entre_estudiantes`. Origen: **Azure #496** "Verificar aislamiento estricto de información académica y enmascaramiento con HTTP 404". |
| Historia de usuario | **HU 1.2.2 Acceso y privacidad en datos** (Azure #198, estado Done). Habilitador HH 1.2.1 (#360). |
| Criterios de aceptación | **CA2:** "...retorna únicamente los datos asociados al identificador del usuario autenticado, nunca información de otros usuarios." **CA3:** "Dado que el usuario está autenticado, es un estudiante y solicita información de otro estudiante, entonces el sistema retorna un error 404 genérico informando que el usuario no existe." |
| Riesgo | RH01 / SQA R03: exposición de datos académicos de otro estudiante (IDOR). Criticidad 15. |
| Objetivo | Comprobar que un estudiante autenticado no puede ver resultados de otro estudiante, ni navegando ni manipulando la URL. |
| Tipo | Negativo (seguridad), precedido de un control positivo. |
| Precondiciones | 1. Existen los estudiantes A y B, cada uno con al menos una evaluación registrada. 2. Se conoce el identificador de B y un dato visible exclusivo de B. 3. Las variables `BASE_URL`, `RUTA_RESULTADOS_PROPIOS` y `RUTA_RESULTADOS_ESTUDIANTE` están configuradas; esta última contiene `{idEstudiante}`. 4. La contraseña de A está en la variable de entorno `ITM_PWD_ESTUDIANTE_A`. |
| Datos de entrada | `Data Files/TD_TC01_Aislamiento`: `id_estudiante_b`, `marcador_dato_estudiante_b` (un dato visible y único de B, como su nombre; **no** usar su ID) y `mensaje_404_esperado` (texto real del 404 genérico). |

**Pasos**

1. Iniciar sesión como Estudiante A (HU 1.1.2) y verificar que se muestra la pantalla de bienvenida.
2. Abrir `RUTA_RESULTADOS_PROPIOS`.
3. Verificar que el contenedor de resultados es visible. **[Precisión de automatización]** Este control positivo evita que las verificaciones negativas pasen sobre una página vacía.
4. Verificar que el marcador de B **no** aparece (CA2).
5. Abrir `RUTA_RESULTADOS_ESTUDIANTE` con `{idEstudiante}` = id de B, es decir, manipulando la URL.
6. Esperar el mensaje 404 genérico (CA3).
7. Verificar que el marcador de B no aparece y que el contenedor de resultados no se renderiza.

**Resultado esperado:** A ve sus resultados sin datos de B. Al solicitar la URL de B, se muestra el mensaje 404 genérico y no aparece ningún dato de B.

**Criterios de evaluación**

| Resultado | Condición |
|---|---|
| Pasa | Se cumplen los pasos 3, 4, 6 y 7. |
| Falla | Aparece cualquier dato de B, no se muestra el mensaje 404, o se renderiza el contenedor de resultados ajenos. |
| ERROR | A no ve sus propios resultados (precondición) o falta configuración. |

**Evidencia:** `Evidencias/TC01/*_01_resultados_propios.png`, `*_02_solicitud_resultados_estudiante_B.png`, `bitacora.txt` (incluye la URL solicitada) y el reporte de Katalon.

**Limitación:** desde la interfaz se verifica el **mensaje** 404, no el código HTTP de la API. Se recomienda complementar con una prueba de API, descrita en `Pendientes_Integracion.md`.

---

## TC02 – Restricción de acceso por rol a la administración de evaluaciones

| Campo | Detalle |
|---|---|
| Identificador | `TC02_HU213_Restriccion_acceso_por_rol_a_administracion_de_evaluaciones`. Origen: **Azure #466** "Verificar restricción de acceso al módulo de administración de evaluaciones según el rol del usuario". |
| Historia de usuario | **HU 2.1.3 Creación de rúbricas** (Azure #224, estado Done). Relacionadas: HU 1.2.1 CA4 (#193), HH 1.2.1 CA1 (#360) y CR-008. |
| Criterio de aceptación | **HU 2.1.3 CA1:** "Dado que un usuario intenta acceder al módulo de administración de evaluaciones técnicas, cuando el sistema valida su rol, entonces permite el ingreso si es Docente o Administrador y muestra la lista de temas; en caso contrario, deniega el acceso con mensaje de autorización." |
| Riesgo | RH02 / SQA R03 + R01: un estudiante manipula o consulta el banco de preguntas y rúbricas que usa el LLM para calificar. Criticidad 15. |
| Objetivo | Verificar que el control de acceso por rol se aplica también al **acceso directo por URL**, y no solo ocultando el menú. |
| Tipo | Negativo para Estudiante, con controles positivos para Docente y Administrador. Dirigido por datos. |
| Precondiciones | Existen cuentas de prueba Estudiante, Docente y Administrador. Hay al menos un tema registrado. `RUTA_ADMIN_EVALUACIONES` está configurada. Las contraseñas están en `ITM_PWD_ESTUDIANTE_A`, `ITM_PWD_DOCENTE` e `ITM_PWD_ADMINISTRADOR`. |

**Datos de entrada:** `Data Files/TD_TC02_Roles`.

| Rol | Acceso esperado | Mensaje de denegación esperado |
|---|---|---|
| ESTUDIANTE | DENEGADO | `autorización insuficiente` (texto tomado de HU 1.2.1 CA4; **confirmar contra la UI real**) |
| DOCENTE | PERMITIDO | — |
| ADMINISTRADOR | PERMITIDO | — |

**Pasos (por cada fila)**

1. Iniciar sesión con el rol de la fila.
2. Abrir directamente `RUTA_ADMIN_EVALUACIONES`.
3. Según el rol:
   - **PERMITIDO:** verificar que la lista de temas es visible.
   - **DENEGADO:** esperar el mensaje de autorización y verificar que la lista de temas **no** está presente.
4. Cerrar la sesión.

**Resultado esperado:** Docente y Administrador ven la lista de temas. Al Estudiante se le deniega el acceso con mensaje y sin lista de temas.

**Criterios de evaluación**

| Resultado | Condición |
|---|---|
| Pasa | Todas las filas cumplen lo esperado. |
| Falla | El Estudiante ve la lista de temas o no recibe el mensaje, o un rol autorizado no puede entrar. |

Si los controles positivos (Docente y Administrador) fallan, revise primero el entorno y los localizadores antes de interpretar el resultado del Estudiante.

**Evidencia:** `Evidencias/TC02/*_<fila>_<ROL>_acceso_(permitido|denegado).png`, `bitacora.txt` y el reporte.

---

## TC03 – Evaluación inicial secuencial y persistencia de respuestas

| Campo | Detalle |
|---|---|
| Identificador | `TC03_HU211_Evaluacion_inicial_secuencial_y_persistencia_de_respuestas`. Origen: **Azure #434** "Validar interacción de preguntas de la evaluación inicial". |
| Historia de usuario | **HU 2.1.1 Desarrollo de evaluación** (Azure #212, estado Done). Relacionada: CR-006 (#330, autosave incremental, estado Implementado). |
| Criterios de aceptación | **CA2:** "...las muestra una a una en orden secuencial sin permitir saltar entre preguntas ni retroceder." **CA3:** "...cuando confirma su respuesta, entonces el sistema persiste la respuesta en base de datos asociada al identificador del estudiante, la evaluación y la pregunta." **CR-006:** "cada respuesta confirmada se persiste inmediatamente en base de datos. El estudiante puede retomar la evaluación desde donde quedó." |
| Riesgo | RH03: pérdida o alteración de respuestas de la evaluación inicial, que es la entrada de la categorización ML y de la calificación LLM (R01). Criticidad 15. |
| Objetivo | Verificar el flujo secuencial sin retroceso y que la respuesta confirmada sobrevive a la navegación y al reingreso. |
| Tipo | Positivo (flujo y persistencia) y negativo (intento de retroceso). |
| Precondiciones | Existe una cuenta Estudiante **sin evaluación inicial iniciada** (`USUARIO_ESTUDIANTE_EVAL`, contraseña en `ITM_PWD_ESTUDIANTE_EVAL`). La evaluación tiene al menos 2 preguntas. **El caso modifica el estado de esa cuenta: use una cuenta nueva o restablecida en cada ejecución.** |
| Datos de entrada | `Data Files/TD_TC03_Respuestas`: texto de respuesta abierta (CR-003). Son entradas de prueba, no respuestas correctas. |

**Pasos**

1. Iniciar sesión y abrir `RUTA_EVALUACION_INICIAL`. Pulsar *Iniciar* si el botón existe.
2. Registrar el enunciado de la pregunta 1 y verificar que no hay controles para retroceder u omitir preguntas (CA2).
3. Escribir la respuesta 1 y confirmar (CA3).
4. Verificar que aparece una pregunta distinta (la pregunta 2) y que tampoco hay controles para retroceder u omitir.
5. **[Precisión de automatización]** Pulsar *Atrás* en el navegador y verificar que la pregunta 1 no queda editable.
6. **[Precisión de automatización]** Reingresar al módulo y verificar que continúa en la pregunta 2, no en la 1. Esto evidencia que la respuesta se persistió y que la evaluación se reanuda (CR-006).

**Resultado esperado:** la evaluación avanza solo hacia adelante, no permite volver a editar la pregunta 1 y al reingresar continúa en la pregunta 2.

**Criterios de evaluación**

| Resultado | Condición |
|---|---|
| Pasa | Se cumplen todas las verificaciones. |
| Falla | No avanza tras confirmar, existe un control para retroceder u omitir, la pregunta 1 queda editable tras *Atrás*, o al reingresar aparece la pregunta 1 o una pregunta distinta de la pendiente. |

**Evidencia:** `Evidencias/TC03/*_01_pregunta_1.png`, `*_02_pregunta_2.png`, `*_03_despues_de_atras.png`, `*_04_reingreso.png` y `bitacora.txt` (con los enunciados).

**Limitaciones**

- La persistencia en base de datos (CA3) se evidencia de forma indirecta, por la reanudación. La consulta SQL directa queda pendiente porque no hay datos de conexión.
- El objeto `btn_NavegacionNoPermitida` usa un XPath genérico basado en textos como "Anterior", "Atrás", "Saltar" u "Omitir". Hay que revisarlo si la UI usa iconos sin texto o un indicador de preguntas clicable.
