# Inventario de casos de prueba de Azure DevOps y su automatización

Fuente: Azure DevOps, proyecto *Trabajo de Grado - Tutor inteligente* (organización `especializacion-itm`), consultado en modo de solo lectura el 2026-10-23. El proyecto tiene **43 casos de prueba** (work items *Test Case*), todos en estado *Design* y con *Automation Status = Not Automated* en Azure. No se modificó nada en Azure DevOps: la trazabilidad se documenta solo en este proyecto.

Aplicación bajo prueba: https://itmentorsoft.com (entorno de pruebas). Cuentas disponibles: Estudiante y Docente. No hay cuenta de Administrador.

## Criterio de clasificación

| Clasificación | Significado |
|---|---|
| **Automatizado** | El caso de Azure se cubre completo con un caso Katalon de prueba funcional de sistema (interfaz y API, caja negra). |
| **Parcial** | Se automatiza la parte observable desde la interfaz o la API con las cuentas disponibles; el resto se explica en la columna *Alcance*. |
| **No automatizable** | No se puede ejecutar como prueba funcional de sistema en este entorno: requiere cuenta de Administrador, inyectar fallos en la infraestructura, inspeccionar internos (modelo de ML, base de datos) o una funcionalidad que la aplicación no ofrece. |

## Resumen

| Clasificación | Casos Azure |
|---|---|
| Automatizado | 20 |
| Parcial | 14 |
| No automatizable | 9 |
| **Total** | **43** |

## Detalle por caso

| Azure | Título en Azure | Clasificación | Caso Katalon | Alcance / motivo |
|---|---|---|---|---|
| #418 | Verificar que se genera un reporte dada una categoría | Automatizado | TC17 | Reporte de estudiantes filtrado por nivel (categoría), contrastado con la API. El caso de Azure no tiene pasos. |
| #422 | Validar el ingreso al módulo de realización de la evaluación inicial | Automatizado | TC11 | Temas publicados, preguntas por sección y selección aleatoria. |
| #426 | CP-4.1.1 Visualización y actualización del progreso de aprendizaje | Parcial | TC13 | Se verifica la vista "Mi progreso" y el avance de la ruta; los estados de cada recurso dependen de la ruta generada. |
| #430 | Verificar registro y actualización de contenido académico | Parcial | TC19 | Validaciones, consulta por categoría, búsqueda y formulario de edición. No se guardan recursos para no alterar el catálogo compartido. |
| #434 | Validar interacción de preguntas de la evaluación inicial | Automatizado | TC03 | Flujo secuencial y persistencia de respuestas (CR-006). |
| #435 | Garantizar finalización de evaluación inicial | Automatizado | TC12 | Envío completo, registro en el historial y calificación. |
| #436 | Verificar generación y actualización de ruta de aprendizaje personalizada | Parcial | TC14 | Ruta generada según la evaluación y sus recursos; la notificación al administrador no es observable. |
| #438 | Verificar actualización y desactivación de recursos académicos | No automatizable | — | Requiere rol Administrador (no hay cuenta). La denegación a otros roles se cubre en TC10. |
| #440 | Verificar generación de reportes de contenido académico | Parcial | TC18 | Recursos mejor y peor valorados por tema y totales; el resaltado de promedios < 3.5 no existe en la interfaz (advertencia). |
| #442 | Verificar actualización de rutas de aprendizaje | No automatizable | — | La aplicación no ofrece al docente un módulo de rutas: las rutas se generan automáticamente para cada estudiante. |
| #444 | Verificar generación de reportes de debilidades por estudiante | Parcial | TC18 | Progreso por tema en el reporte individual; no existe el consolidado de "debilidad común" (advertencia). |
| #446 | Verificar recepción de ruta de aprendizaje personalizada | Parcial | TC14 | Recomendaciones visibles; el cruce con intereses no es observable (la aplicación no registra intereses). |
| #448 | Verificar calificación de recursos recomendados | Automatizado | TC15 | Calificación 1-5, modificación sin duplicados. |
| #450 | Verificar acceso autorizado a reportes por estudiante | Automatizado | TC16 | Interfaz y API, Estudiante denegado y Docente autorizado. |
| #451 | CP-4.2.2 Identificación, clasificación y actualización de debilidades | Parcial | TC13 | Perfil de conocimiento por tema y aspectos a reforzar; la severidad alta/media no se muestra como tal. |
| #455 | CP-4.1.3 Consulta y filtrado del progreso desde el dashboard docente | Automatizado | TC17 | Métricas, gráficos y filtro por nivel con verificación contra la API. |
| #457 | CP-4.2.1 Generación y consulta de retroalimentación de una evaluación | Automatizado | TC12 | Detalle pregunta a pregunta con puntaje y retroalimentación. |
| #459 | CP-4.2.3 Generación y visualización de la explicación de categorización | Parcial | TC13 | Nivel asignado y feedback visibles para el estudiante; las variables del modelo no son observables. |
| #461 | Validar confirmación de recurso finalizado y selección de 10 preguntas aleatorias | Parcial | TC14 | Marcar recurso como visto y avance de la ruta; la evaluación de seguimiento de 10 preguntas no existe en la aplicación. |
| #465 | Verificar recalificación de nivel y gráfica comparativa de progreso histórico | Parcial | TC13 | Gráfica de puntajes de las últimas evaluaciones; la recalificación requiere varias evaluaciones de seguimiento. |
| #466 | Verificar restricción de acceso al módulo de administración de evaluaciones según el rol | Automatizado | TC02 | Estudiante rechazado en interfaz y API; Docente autorizado. |
| #468 | Validar paginación de 20 preguntas por tema y persistencia de operaciones CRUD | Parcial | TC20 | Paginación, filtros, validaciones e historial; no se crean ni editan preguntas (banco de evaluaciones real). |
| #469 | Validar la generación de JSON y asignación de categoría única por el modelo de ML | No automatizable | — | Interno del modelo de ML. Evidencia indirecta: TC12/TC13 verifican que la evaluación recibe un único nivel. |
| #471 | Verificar la resiliencia ante fallos del modelo de ML o de base de datos | No automatizable | — | Requiere inyectar fallos en la infraestructura del entorno. |
| #472 | Validar actualización de categoría, histórico de versiones y notificación al perfil | No automatizable | — | Requiere varias reclasificaciones del mismo estudiante y acceso al histórico del modelo. |
| #473 | Validar regla de al menos 3 objetivos por categoría y alerta de no asignados | No automatizable | — | Configuración de categorías y objetivos reservada al Administrador. |
| #475 | Verificar despliegue de objetivos según la categoría o mensaje de evaluación pendiente | Automatizado | TC13 | Nivel asignado o mensaje "Sin categoría asignada aún" con acceso a la evaluación. |
| #476 | Validar consolidación de estudiantes por categoría, métricas y filtros | Automatizado | TC17 | Consolidación, porcentajes y filtro por nivel; el filtro por fechas no existe (advertencia). |
| #477 | Verificar alerta "requiere mejora" para categorías con promedio < 70 % | No automatizable | — | Alerta dirigida al Administrador; no es visible para Docente ni Estudiante. |
| #478 | Validar límites de longitud y expresiones regulares del formulario de registro | Automatizado | TC05 | Dirigido por datos, sin crear cuentas. |
| #482 | Verificar unicidad de username/email y almacenamiento seguro (hash) | Parcial | TC06 | Rechazo de datos duplicados; el hash bcrypt/Argon2 no es observable. |
| #483 | Validar manejo de errores de formato y mensaje genérico ante credenciales incorrectas | Automatizado | TC04 | Validaciones y mensaje genérico idéntico para correo registrado y no registrado. |
| #485 | Verificar cookie JWT HttpOnly/Secure con expiración de 15 minutos y redirección según rol | Automatizado | TC07 | Login completo con OTP por rol. |
| #486 | Validar mensaje de privacidad genérico y vigencia de 5 minutos del token de recuperación | Automatizado | TC08 (+TC09) | Mensaje genérico y recepción del correo; la vigencia se comprueba en TC09. |
| #488 | Verificar token vencido, contraseña segura y revocación de sesiones | Automatizado | TC09 | Enlace sin token, token falso, enlace real vencido y complejidad. La revocación de sesiones no se fuerza (no se cambian contraseñas). |
| #489 | Validar edición de permisos por rol y restricción de acceso | Parcial | TC10 | Solo la restricción de acceso por rol; la edición de permisos requiere Administrador. |
| #493 | Verificar bloqueo de eliminación de roles con usuarios activos | No automatizable | — | Gestión de roles reservada al Administrador. |
| #494 | Validar interceptación del middleware con HTTP 401 y 403 | Automatizado | TC10 | Dirigido por datos: sin token, token inválido y roles sin permiso. |
| #496 | Verificar aislamiento estricto de información académica (HTTP 404) | Automatizado | TC01 | Interfaz y API con dos estudiantes. |
| #497 | Validar acceso al módulo de rúbricas y marcado "pendiente de baja" | No automatizable | — | La aplicación no tiene módulo de rúbricas ni estado "pendiente de baja": la rúbrica es parte de cada pregunta y la desactivación es inmediata. |
| #499 | Verificar preservación de versión activa y aprobación/rechazo de cambios en rúbricas | Parcial | TC20 | Historial de versiones de la pregunta; la aprobación o el rechazo los hace el Administrador. |
| #500 | Validar control de acceso al módulo de reportes generales y extracción JSON | Automatizado | TC16 | JSON consolidado para el Docente y denegación al Estudiante. |
| #502 | Verificar la representación gráfica de reportes y la vista sin datos | Automatizado | TC17 | Tarjetas, barras y tortas; la vista "sin datos" no se puede provocar con datos reales (advertencia). |

## Casos Katalon y casos Azure cubiertos

| Caso Katalon | Azure |
|---|---|
| TC01 | #496 |
| TC02 | #466 |
| TC03 | #434 |
| TC04 | #483 |
| TC05 | #478 |
| TC06 | #482 |
| TC07 | #485 |
| TC08 | #486 |
| TC09 | #488, #486 (vigencia) |
| TC10 | #494, #489 (parcial) |
| TC11 | #422 |
| TC12 | #435, #457 |
| TC13 | #426, #451, #459, #465, #475 |
| TC14 | #436, #446, #461 |
| TC15 | #448 |
| TC16 | #450, #500 |
| TC17 | #418, #455, #476, #502 |
| TC18 | #440, #444 |
| TC19 | #430 |
| TC20 | #468, #499 (parcial) |
