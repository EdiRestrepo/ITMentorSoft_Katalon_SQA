### Forma de ejecución

- Los casos se ejecutaron uno a uno desde Katalon Studio 11.6 mediante su servidor MCP local (StudioAssist, `executeTest` sobre las suites `TS_TC01` a `TS_TC20`), con el perfil `default` y Chrome. La ejecución por consola (`katalonc`) no fue posible: exige licencia de Runtime Engine o API key.
- Código OTP del **Estudiante**: se leyó del correo `edisonestival@gmail.com` (remitente `itmentorsoft@…brevosend.com`) y se entregó a la prueba por `Evidencias/otp/codigo.txt`. Código OTP del **Docente**: lo escribió el tester en el navegador (el buzón `@correo.itm.edu.co` no estaba accesible). El código vence a los 2 minutos.
- **Reutilización de sesión**: después de un login con OTP, el keyword `Autenticacion` guarda sessionStorage y cookies en `Evidencias/sesiones/` (no versionada) y los casos siguientes la restauran. Los casos que prueban el login en sí (TC04, TC07) no la usan. Así un ciclo completo pide un OTP por rol.
- Las contraseñas se configuraron como variables de entorno de usuario (`ITM_PWD_ESTUDIANTE`, `ITM_PWD_DOCENTE`); no están en el proyecto.
- Los casos de Azure se leyeron en modo de solo lectura (43 casos de prueba). No se modificó nada en Azure DevOps. Clasificación y trazabilidad en `Documentacion/Inventario_Casos_Azure.md`.

### Ajustes realizados (primera y segunda ronda)

- **Data Files**: el separador CSV estaba como `,` y Katalon espera `COMMA` (afectaba a TC01-TC03).
- **Guía de bienvenida**: la aplicación abre una guía (`app-guide-dialog`) la primera vez que un usuario entra desde un navegador; su fondo interceptaba los clics. El keyword `Autenticacion.omitirGuia` pulsa **Omitir** después del login.
- **Campos de formulario**: Angular copia el `id` estático del campo también al componente anfitrión (`<app-input id="email">`), por lo que `#email` no era interactuable. El objeto `Comun/txt_Campo` busca solo `input`, `textarea` y `select`.
- **TC04**: la primera versión leía la alerta del formulario (que es una sugerencia de registro) en lugar del mensaje de error; la verificación pasó por una razón equivocada. Se corrigió para leer la notificación ("Correo o contraseña incorrectos.") y comparar también la alerta.
- **TC09**: el primer intento usó el enlace real de recuperación 4 min 39 s después de emitido (error de tiempo del tester), así que el resultado FAILED de ese intento **no es válido**. Se corrigió la prueba para que espere a que el enlace supere los 5 minutos y se repitió con un enlace nuevo. En ambos casos se envió la contraseña actual, por lo que la contraseña de la cuenta no cambió.
- **TC10**: la fila que esperaba 403 del estudiante en `/content/ratings/all` partía de un supuesto errado: ese endpoint es el que usa el estudiante para ver sus propias calificaciones (responde 422 porque falta `user_id`). Se reemplazó por un endpoint exclusivo del docente (`/reports/students-by-category`).
- **TC15**: la verificación de duplicados no reconocía la estructura del JSON (`rating_details`); se corrigió y se repitió.
- **TC19**: los errores de campo del formulario de recursos usan la clase `res__field-error` (no `res__error`).
- **TC11**: se agregó la verificación de que una evaluación descartada no quede registrada como realizada (ver defecto D4).
- **Suites**: `TS_TC01`…`TS_TC20` (una por caso) y `TS_Regresion_Completa` (los 20 en orden de ejecución recomendado).

### Defectos encontrados

- **D1 - Vigencia del token de acceso (TC07, #485)**: el JWT de acceso vence a los **60 minutos** (claim `exp` = 3597 s tras el login) para Estudiante y Docente; #485 exige 15 minutos. La cookie `access_token` sí es HttpOnly y Secure y la redirección por rol es correcta.
- **D2 - Vigencia del enlace de recuperación (TC09, #486/#488)**: un enlace usado **357 s (≈ 6 min)** después de emitido sigue siendo válido ("Contraseña actualizada"). La pantalla y Azure indican 5 minutos; el correo dice "El enlace expirará en 30 minutos". Las tres fuentes no coinciden.
- **D3 - Texto del correo de recuperación (TC08)**: el correo informa 30 minutos de vigencia mientras la interfaz informa 5 minutos (inconsistencia de información al usuario).
- **D4 - Evaluaciones descartadas quedan registradas (TC11, HU 2.1.1 / CR-006)**: al descartar una evaluación en curso, el sistema la guarda como evaluación realizada con nota 0.0 y "Sin clasificar". Infla "Evaluaciones realizadas", baja el promedio y altera la comparación con la evaluación anterior (el panel muestra "▲ +0.4" comparando con una evaluación descartada). Las ejecuciones de TC03 y TC11 dejaron este tipo de registros en el historial del estudiante de pruebas.

### Observaciones (diferencias entre el requisito de Azure y la aplicación)

- **TC02 (#466)**: al Estudiante se le niega el acceso (API 403 y redirección a `/login`) pero no se muestra un mensaje de autorización.
- **TC03 (#434)**: la interfaz permite volver con "Anterior" y saltar entre preguntas; Azure dice "sin permitir saltar ni retroceder".
- **TC04 (#483)**: el mensaje genérico es "Correo o contraseña incorrectos." (Azure: "Usuario o contraseña incorrectas"); es genérico y no revela el dato.
- **TC07 (#485)**: además de la cookie HttpOnly, el frontend guarda una copia del JWT en `sessionStorage`, accesible desde JavaScript.
- **TC13 (#451)**: el perfil muestra el porcentaje por tema pero no clasifica la severidad (alta < 50 %, media 50-69 %).
- **TC14 (#461, #446)**: no existe la evaluación de seguimiento de 10 preguntas al finalizar un recurso ni el registro de intereses del estudiante.
- **TC17 (#476, #455, #502)**: no hay filtro por rango de fechas; la vista "sin datos disponibles" no se puede provocar con los datos reales.
- **TC18 (#440, #444)**: no se resaltan los recursos con promedio < 3.5 y no existe el consolidado de "debilidad común" por tema.
- **TC20 (#468)**: el banco de preguntas pagina de a **10** (Azure exige 20); las vistas filtradas muestran todos los resultados sin paginar. La persistencia del CRUD no se automatizó para no alterar el banco real.
- **TC01 (#496)**: el control positivo por API del Estudiante A depende de que tenga resultados; tras TC12 el estudiante de pruebas ya tiene una evaluación calificada.
- Casos de Azure no automatizables en este entorno (sin cuenta de Administrador, internos del modelo de ML o funcionalidades inexistentes): #438, #442, #469, #471, #472, #473, #477, #493, #497. Detalle en `Inventario_Casos_Azure.md`.

### Efectos en los datos del entorno de pruebas

- TC12 envió una evaluación completa del estudiante de pruebas (nivel asignado: básico); TC15 dejó la calificación 5 en el recurso "Microsoft Learn: principios arquitectónicos" (promedio 5.0, sin cambio); TC14 marcó y desmarcó un recurso (sin cambio neto).
- TC03 y TC11 dejaron evaluaciones descartadas en el historial por el defecto D4.
- No se crearon cuentas, recursos ni preguntas, y no se cambiaron contraseñas.
