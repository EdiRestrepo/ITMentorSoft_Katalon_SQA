### Forma de ejecución

- Los casos se ejecutaron uno a uno desde Katalon Studio 11.6 mediante su servidor MCP local (StudioAssist, `executeTest` sobre las suites `TS_TC01`, `TS_TC02` y `TS_TC03`), con el perfil `default` y Chrome. La ejecución por consola (`katalonc`) no fue posible: exige licencia de Runtime Engine o API key.
- Código OTP del **Estudiante**: se leyó del correo `edisonestival@gmail.com` (remitente `itmentorsoft@…brevosend.com`) y se entregó a la prueba por `Evidencias/otp/codigo.txt`. Código OTP del **Docente**: lo escribió el tester en el navegador (el buzón `@correo.itm.edu.co` no estaba accesible). El código vence a los 2 minutos.
- Las contraseñas se configuraron como variables de entorno de usuario (`ITM_PWD_ESTUDIANTE`, `ITM_PWD_DOCENTE`); no están en el proyecto.

### Ajustes realizados

- **Data Files (`TD_TC01_Aislamiento`, `TD_TC02_Roles`, `TD_TC03_Respuestas`)**: el separador CSV estaba como `,` y Katalon espera el valor `COMMA`. Error original: `No enum constant ...CSVSeparator.,`. Afectaba a los tres casos.
- **Guía de bienvenida**: la aplicación abre una guía (`app-guide-dialog`) la primera vez que un usuario entra desde un navegador (`localStorage itm.guide.seen.<rol>.<id>`). Katalon usa un perfil nuevo en cada ejecución, así que la guía siempre aparecía y su fondo (`div.guide__backdrop`) interceptaba los clics. Se agregó el objeto `Autenticacion/Page_Bienvenida/btn_OmitirGuia` y el keyword `Autenticacion.omitirGuia`, que pulsa **Omitir** después del login.
- **OTP**: el keyword `Autenticacion.esperarOtp` acepta el código escrito a mano en el navegador o dejado en `Evidencias/otp/codigo.txt` (carpeta no versionada).
- **Suites**: se agregaron `TS_TC01`, `TS_TC02` y `TS_TC03` (una por caso) para ejecutarlos de forma individual. `TS_Pruebas_Criticas` sigue agrupando los tres.

### Observaciones para el equipo

- **TC01**: el control positivo por API (resumen propio del Estudiante A en `/reports/student_summary`) respondió `404 Resumen del estudiante no encontrado`, probablemente porque la cuenta no tiene evaluaciones calificadas. La evidencia de aislamiento se apoya en que las dos APIs devuelven `404 User not found` para el Estudiante B y en que la vista `/teacher/students/<id B>` redirige a `/login`. Se recomienda repetir el caso con un estudiante que tenga resultados, para que el control positivo devuelva 200.
- **TC02**: al Estudiante se le niega el acceso (API `403 Not enough permissions` y redirección a `/login`), pero la aplicación no muestra un mensaje de autorización como pide HU 2.1.3 CA1 (advertencia registrada; hallazgo H2 de `Pendientes_Integracion.md`).
- **TC03**: la interfaz ofrece el botón **Anterior** y el panel de preguntas para navegar dentro de la evaluación; la redacción de HU 2.1.1 CA2 dice "sin permitir saltar ni retroceder". Confirmar con producto (hallazgo H4).
