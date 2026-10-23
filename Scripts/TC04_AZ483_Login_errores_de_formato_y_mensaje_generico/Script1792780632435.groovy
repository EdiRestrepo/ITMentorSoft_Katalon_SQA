import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.util.KeywordUtil as KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import internal.GlobalVariable as GlobalVariable
import itmentorsoft.Configuracion
import itmentorsoft.Evidencia
import itmentorsoft.Pagina

/*
 * TC04 - Manejo de errores de formato y mensaje genérico ante credenciales incorrectas
 * Azure Test Plans #483 | HU 1.1.2 Inicio de sesión
 *
 * PASA si:  (1) con campos vacíos o formato inválido el formulario muestra el error de cada campo y no envía la solicitud;
 *           (2) con un correo registrado y contraseña incorrecta, y con un correo no registrado, la aplicación muestra
 *               el mismo mensaje genérico, sin revelar qué dato falló, y no avanza a la verificación OTP.
 * FALLA si: falta algún mensaje de validación, el mensaje revela si el correo existe o la sesión avanza.
 * Solo hace un intento fallido con la cuenta real para no activar el bloqueo por intentos (HU 1.1.2).
 */
final String CASO = 'TC04'
final String ALERTA = 'Autenticacion/Page_Login/lbl_AlertaCredenciales'
final String ENVIAR = 'Comun/btn_EnviarFormulario'
final String CLAVE_INCORRECTA = 'ClaveIncorrecta#2026'

List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL', 'RUTA_LOGIN', 'USUARIO_ESTUDIANTE'])
pendientes += Configuracion.objetosPendientes(['Comun/txt_Campo', 'Comun/lbl_ErrorCampo', ENVIAR, ALERTA])
Configuracion.detenerSiHayPendientes(CASO, pendientes)

def verificarError = { String campo, String esperado, String paso ->
	String obtenido = Pagina.errorDe(campo)
	Evidencia.registrar(CASO, "$paso -> error de '$campo': '$obtenido'")
	if (obtenido != esperado) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: $paso: se esperaba el mensaje '$esperado' en el campo $campo y se obtuvo '$obtenido'. Incumple HU 1.1.2 (#483).")
	}
}

/**
 * Envía credenciales y devuelve el mensaje de error (notificación) y el texto de la alerta del formulario
 * (sugerencia de registro), que también debe ser igual para un correo registrado y uno no registrado.
 */
def intentarLogin = { String correo, String clave ->
	Pagina.abrir(GlobalVariable.RUTA_LOGIN.toString())
	Pagina.escribir('email', correo)
	Pagina.escribir('password', clave)
	WebUI.click(findTestObject(ENVIAR))
	String mensaje = Pagina.esperarToast()
	String alerta = Pagina.texto(ALERTA, null, 3)
	return [mensaje: mensaje, alerta: alerta, ruta: Pagina.rutaActual()]
}

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que un usuario abre el formulario de inicio de sesión')
	Pagina.abrir(GlobalVariable.RUTA_LOGIN.toString())

	WebUI.comment('Cuando envía el formulario con los campos vacíos')
	WebUI.click(findTestObject(ENVIAR))
	verificarError('email', 'El correo electrónico es requerido', 'Campos vacíos')
	verificarError('password', 'La contraseña es requerida', 'Campos vacíos')

	WebUI.comment('Y cuando escribe un correo con estructura inválida y una contraseña de menos de 6 caracteres')
	Pagina.escribir('email', 'usuario.sin.dominio@')
	Pagina.escribir('password', '123')
	WebUI.click(findTestObject(ENVIAR))
	verificarError('email', 'Ingresa un correo electrónico válido', 'Correo con formato inválido')
	verificarError('password', 'La contraseña debe tener al menos 6 caracteres', 'Contraseña corta')
	Evidencia.capturar(CASO, '01_errores_de_formato')
	if (Pagina.rutaActual() != GlobalVariable.RUTA_LOGIN.toString()) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: con datos de formato inválido el formulario se envió (ruta ${Pagina.rutaActual()}).")
	}

	WebUI.comment('Y cuando ingresa un correo registrado con una contraseña incorrecta')
	Map registrado = intentarLogin(GlobalVariable.USUARIO_ESTUDIANTE.toString(), CLAVE_INCORRECTA)
	Evidencia.capturar(CASO, '02_correo_registrado_clave_incorrecta')
	Evidencia.registrar(CASO, "Correo registrado + clave incorrecta -> '${registrado.mensaje}', alerta '${registrado.alerta}' (ruta ${registrado.ruta})")

	WebUI.comment('Y cuando ingresa un correo que no está registrado')
	Map noRegistrado = intentarLogin("qa.katalon.no.registrado.${System.currentTimeMillis()}@example.com", CLAVE_INCORRECTA)
	Evidencia.capturar(CASO, '03_correo_no_registrado')
	Evidencia.registrar(CASO, "Correo no registrado -> '${noRegistrado.mensaje}', alerta '${noRegistrado.alerta}' (ruta ${noRegistrado.ruta})")

	WebUI.comment('Entonces el sistema muestra el mismo mensaje genérico, sin revelar cuál dato falló')
	[registrado, noRegistrado].each { Map r ->
		if (r.ruta != GlobalVariable.RUTA_LOGIN.toString()) {
			KeywordUtil.markFailed("[$CASO] DEFECTO DE SEGURIDAD: con credenciales incorrectas la aplicación avanzó a ${r.ruta}.")
		}
	}
	if (!registrado.mensaje || !noRegistrado.mensaje) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: no se mostró mensaje ante credenciales incorrectas (registrado: '${registrado.mensaje}', no registrado: '${noRegistrado.mensaje}').")
	} else if (registrado.mensaje != noRegistrado.mensaje) {
		KeywordUtil.markFailed("[$CASO] DEFECTO DE SEGURIDAD: el mensaje cambia según exista o no el correo ('${registrado.mensaje}' vs '${noRegistrado.mensaje}'). Incumple #483 (mensaje genérico).")
	} else if (registrado.alerta != noRegistrado.alerta) {
		KeywordUtil.markFailed("[$CASO] DEFECTO DE SEGURIDAD: la alerta del formulario cambia según exista o no el correo ('${registrado.alerta}' vs '${noRegistrado.alerta}').")
	} else if (registrado.mensaje =~ /(?i)no existe|no registrad|no encontrad|contraseña incorrecta$/) {
		KeywordUtil.markFailed("[$CASO] DEFECTO DE SEGURIDAD: el mensaje revela qué dato falló: '${registrado.mensaje}'.")
	}
	if (registrado.mensaje && !(registrado.mensaje =~ /(?i)usuario o contraseña incorrect/)) {
		KeywordUtil.markWarning("[$CASO] Observación #483: el mensaje genérico es '${registrado.mensaje}'; Azure indica el texto 'Usuario o contraseña incorrectas'. Es genérico y no revela el dato, pero el texto difiere.")
	}
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
