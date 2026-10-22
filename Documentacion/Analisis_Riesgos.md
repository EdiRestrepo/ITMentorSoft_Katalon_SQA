# Análisis de riesgos orientado a pruebas – ITMentorSoft

Fecha del análisis: 2026-10-14

## 1. Fuentes consultadas (solo lectura)

| Fuente | Qué se consultó | Observaciones |
|---|---|---|
| Azure DevOps – proyecto *Trabajo de Grado - Tutor inteligente* | 4 épicas, 8 funcionalidades, **27 historias de usuario** (Product Backlog Item) con descripción y criterios de aceptación, 9 habilitadores (HH), 10 mejoras continuas (MC), 13 solicitudes de cambio (CR), **43 casos de prueba** y **8 planes de prueba** (Sprint 1 a 8), y los vínculos *Tested By* entre historias y casos. | No se creó, editó ni eliminó ningún elemento. HU de los temas 1 y 2 en estado **Done**; HU de los temas 3 y 4 en estado **New**. Los 43 casos están en estado **Design**: ninguno se ha ejecutado. |
| `Downloads\Documento Técnico SQA - ITMentorSoft.docx` | Documento completo, incluidos los 18 comentarios de revisión. | Se leyó sin modificarlo. La copia `(1).docx` es idéntica byte a byte. |

## 2. Metodología

El documento SQA aplica **Risk-Based Testing (RBT)**. Califica cada riesgo con probabilidad e impacto cualitativos y concentra el esfuerzo de pruebas en **R01 y R03**. El SQA no define una escala numérica, así que para comparar riesgos se convirtió su escala cualitativa a una de 1 a 5, sin cambiar las calificaciones del SQA:

| SQA – Probabilidad | Valor | SQA – Impacto | Valor |
|---|---|---|---|
| Baja | 2 | Alto | 4 |
| Media | 3 | Crítico | 5 |
| Alta | 4 | | |

**Criticidad = Probabilidad × Impacto**

| Criticidad | Nivel |
|---|---|
| 16 a 25 | Crítico |
| 10 a 15 | Alto |
| 5 a 9 | Medio |
| 1 a 4 | Bajo |

### 2.1 Riesgos del documento SQA

| ID | Riesgo (SQA) | P | I | Criticidad | Prioridad RBT del SQA |
|---|---|---|---|---|---|
| R01 | Alucinación del LLM en evaluaciones | Alta (4) | Crítico (5) | **20 – Crítico** | Sí |
| R02 | Indisponibilidad del proveedor de IA | Media (3) | Crítico (5) | 15 – Alto | No |
| R03 | Inyección de SQL o fallos en RBAC | Baja (2) | Crítico (5) | 10 – Alto | **Sí** |
| R04 | Latencia excesiva en picos de carga | Media (3) | Alto (4) | 12 – Alto | No |

### 2.2 Riesgos de producto por historia de usuario

Cada riesgo se vincula al riesgo del SQA del que se deriva. Cuando la probabilidad se aparta del valor base del SQA, la columna *Justificación* explica por qué.

| ID | HU (Azure) | Riesgo de producto | Característica ISO 25010 | SQA | P | I | Crit. | ¿Verificable con prueba funcional de UI? | Justificación |
|---|---|---|---|---|---|---|---|---|---|
| RH07 | HU 2.1.1 (#212) CA4, CR-003 | El LLM califica mal respuestas abiertas, lo que produce debilidades y categoría incorrectas | Adecuación funcional – corrección | R01 | 4 | 5 | **20** | **No.** Requiere un oráculo, es decir una calificación patrón docente, y un benchmark de ≥ 95 % de coincidencia. La salida no es determinista. | Probabilidad Alta según el SQA; el SQA lo marca como prioritario. |
| RH08 | MC 2.1.2 (#391) | Inyección de instrucciones (*prompt injection*) en la respuesta del estudiante para obtener una nota no merecida | Seguridad – integridad | R01 | 4 | 5 | **20** | **Parcial.** El ataque se puede enviar desde la UI, pero verificar que "la nota se calcula solo con la rúbrica" exige un oráculo de nota. Además es una mejora continua, no una HU, y está en estado New. | Igual a R01. |
| RH01 | HU 1.2.2 (#198) CA2, CA3; HH 1.2.1 CA2 | Un estudiante obtiene resultados académicos de otro estudiante (IDOR) | Seguridad – confidencialidad | R03 | 3 | 5 | **15** | **Sí** | Se sube un punto la base Baja del SQA porque el control se aplica por identificador en varios módulos (perfil, resultados, reportes) y ningún caso se ha ejecutado. |
| RH02 | HU 2.1.3 (#224) CA1; HU 1.2.1 CA4; HU 1.2.3 CA1; HH 1.2.1 CA1; CR-008 | Un estudiante accede a la administración de evaluaciones y rúbricas, que son la base de la calificación del LLM | Seguridad – integridad y confidencialidad | R03 + R01 | 3 | 5 | **15** | **Sí** | Mismo criterio que RH01. Además, si las rúbricas o el banco de preguntas se manipulan, toda calificación del LLM pasa a ser incorrecta (R01). |
| RH03 | HU 2.1.1 (#212) CA2, CA3; CR-006 | Se pierden o alteran respuestas de la evaluación inicial: el estudiante puede saltar o retroceder, o la respuesta no se persiste | Fiabilidad e integridad | R01 (entrada) | 3 | 5 | **15** | **Sí** | La evaluación inicial es la única entrada de la categorización (HU 2.2.1) y de la ruta de aprendizaje. El SQA exige un "registro inalterable de respuestas". El flujo combina una SPA, autosave (CR-006) y colas SQS. |
| RH09 | HU 2.2.1 (#228) CA5, CA7; HH 2.1.1; HH 2.1.2 | Si el LLM o el modelo de ML fallan, se asigna una categoría errónea o se pierde la clasificación | Fiabilidad – tolerancia a fallos | R02 | 3 | 5 | **15** | **No por ahora.** Requiere inyectar fallas con el doble de prueba de HH 2.1.2, que está en estado New. | Igual a R02. |
| RH04 | HU 1.1.2 (#181) CA3, CA4 | Acceso no autorizado: enumeración de usuarios, o un JWT expuesto o con vigencia mayor a 15 minutos | Seguridad – autenticidad | R03 | 2 | 5 | 10 | Sí, en parte. HttpOnly y la expiración necesitan DevTools o una espera. | Base del SQA. |
| RH05 | HU 1.1.3 (#186) CA4, CA6, CA8 | Toma de cuenta con un token de recuperación vencido o reutilizable | Seguridad – autenticidad | R03 | 2 | 5 | 10 | Sí, en parte. Requiere acceso al buzón y esperar 5 minutos. | Base del SQA. |
| RH06 | HU 1.2.1 (#193) CA4, CA5 | Se configuran mal roles y permisos o se elimina un rol en uso | Seguridad – integridad | R03 | 2 | 5 | 10 | Sí | Base del SQA. Su efecto se observa en RH01 y RH02. |
| RH14 | HU 1.1.1 (#176) CA4 | Contraseñas almacenadas sin hash bcrypt o Argon2 | Seguridad – confidencialidad | R03 | 2 | 5 | 10 | **No.** Se verifica en la base de datos o con revisión de código. | Base del SQA. |
| RH11 | Transversal | Latencia P95 mayor a 2 s en picos de carga | Eficiencia de desempeño | R04 | 3 | 4 | 12 | **No.** Es una prueba de carga con K6 o JMeter. | Igual a R04. |
| RH10 | HU 2.2.1 CA3 | Se fuerza la clasificación sin haber hecho la evaluación | Integridad | R03 | 2 | 4 | 8 | Sí | La clasificación incorrecta afecta la ruta, pero no expone datos. |
| RH12 | HU 2.2.3, HU 3.2.2, HU 4.1.3 | Métricas de reportes mal calculadas | Adecuación funcional – corrección | — | 3 | 3 | 9 | Sí | Afecta decisiones pedagógicas, no la seguridad. HU 3.x y 4.x están en estado New. |
| RH13 | HU 3.1.2, HU 3.2.3 | Recomendaciones de recursos que no corresponden a las debilidades | Adecuación funcional – pertinencia | R01 (derivado) | 3 | 3 | 9 | Sí | HU en estado New; dependen del spike HH 3.2.1. |

## 3. Riesgos altos y críticos

- **Críticos (20):** RH07 y RH08 (R01). Son los riesgos más altos de ITMentorSoft, pero **no se pueden validar de forma confiable con una prueba funcional de interfaz**. Una prueba de UI no tiene un oráculo para decidir si una nota es "correcta", y la salida del LLM no es determinista. Una prueba de este tipo pasaría o fallaría sin evidencia real. El SQA ya asigna la mitigación adecuada: benchmarking de prompts, pruebas ciegas y validación cruzada contra rúbricas patrón con un umbral de ≥ 95 %.
- **Altos (15) verificables por UI:** RH01, RH02 y RH03.
- **Altos (15) no verificables hoy:** RH09. Requiere inyección de fallas y el doble de prueba de HH 2.1.2, que aún no está implementado.

## 4. Selección de los tres casos

Regla aplicada: Azure Test Plans ya contiene casos formales, así que **se seleccionaron tres casos existentes**. No hubo que diseñar casos nuevos.

| ID HU | Funcionalidad | Caso de prueba seleccionado | Riesgo asociado | Nivel de criticidad | Justificación |
|---|---|---|---|---|---|
| HU 1.2.2 (#198) | Épica 1.2 – Gestión de roles y accesos | **Azure #496** – Verificar aislamiento estricto de información académica y enmascaramiento con HTTP 404 → **TC01** | RH01 / R03 | 15 – Alto | Prioridad RBT del SQA (R03). Un fallo expone datos académicos personales. Se verifica de extremo a extremo con dos cuentas reales. La HU está en estado Done. |
| HU 2.1.3 (#224) | Épica 2.1 – Evaluación de conocimientos | **Azure #466** – Verificar restricción de acceso al módulo de administración de evaluaciones según el rol → **TC02** | RH02 / R03 + R01 | 15 – Alto | Cubre a la vez los dos riesgos prioritarios del SQA: el control de acceso (R03) protege la integridad de las rúbricas y preguntas de las que depende el LLM (R01). Incluye controles positivos para Docente y Administrador. |
| HU 2.1.1 (#212) | Épica 2.1 – Evaluación de conocimientos | **Azure #434** – Validar interacción de preguntas de la evaluación inicial → **TC03** | RH03 / entrada de R01 | 15 – Alto | Es el escenario E2E que el plan del SQA nombra para pruebas de sistema ("desde el inicio del examen"). Si las respuestas se alteran o pierden, la calificación del LLM y la categorización se basan en datos incorrectos. |

### Por qué estos tres y no otros

- **RH07 y RH08** tienen criticidad mayor, pero una prueba funcional de UI no puede verificarlos sin oráculo (ver §3). Automatizarlos aquí daría resultados sin valor de evidencia. Se dejan recomendados en `Pendientes_Integracion.md`.
- **RH09** no se puede reproducir sin inyectar fallas, y el habilitador HH 2.1.2 todavía no existe.
- **RH04 y RH05** (login y recuperación de contraseña) tienen menor criticidad (10). Además, el inicio de sesión **sí queda ejercitado** como precondición en los tres casos, a través del keyword `Autenticacion.iniciarSesion`, que valida HU 1.1.2 CA1, CA2 y CA5.
- Se descartaron otros casos más fáciles de automatizar, como reportes o recursos (RH12 y RH13), por su menor criticidad y porque sus HU están en estado New.

## 5. Limitaciones del análisis

- El SQA califica la probabilidad de R03 como **Baja**. En RH01 y RH02 se subió un punto (a 3), con la justificación indicada. Si se mantiene en Baja (2), su criticidad baja a 10, pero los tres casos siguen siendo los de mayor criticidad entre los verificables por UI, y R03 sigue siendo prioridad del SQA.
- El campo *Prioridad* de las 27 HU es 2 en todos los casos, y *Business Value* está vacío. El backlog no permite distinguir la importancia relativa de las HU, por lo que la priorización se basa en el SQA y en el impacto de un fallo.
