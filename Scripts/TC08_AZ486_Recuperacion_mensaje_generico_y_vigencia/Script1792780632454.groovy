import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.util.KeywordUtil as KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import internal.GlobalVariable as GlobalVariable
import itmentorsoft.Configuracion
import itmentorsoft.Evidencia
import itmentorsoft.Pagina

/*
 * TC08 - Mensaje genérico de privacidad y vigencia del enlace de recuperación de contraseña
 * Azure Test Plans #486 | HU 1.1.3 Recuperación de contraseña
 *
 * PASA si:  (1) un correo con formato inválido muestra su error y no se envía;
 *           (2) con un correo registrado y con uno no registrado la aplicación muestra exactamente el mismo
 *               mensaje genérico, que además informa la vigencia de 5 minutos del enlace.
 * FALLA si: el mensaje permite deducir si el correo existe o no informa la vigencia.
 * La recepción del correo en la cuenta registrada (USUARIO_ESTUDIANTE) se verifica fuera de Katalon y se documenta
 * en el informe. La prueba no abre el enlace: no cambia la contraseña.
 */
final String CASO = 'TC08'
final String RUTA = '/recover-password'
final String ENVIAR = 'Comun/btn_EnviarFormulario'
final String TITULO = 'Autenticacion/Page_Recuperar/lbl_Titulo'
final String MENSAJE = 'Autenticacion/Page_Recuperar/lbl_Mensaje'

List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL', 'USUARIO_ESTUDIANTE'])
pendientes += Configuracion.objetosPendientes(['Comun/txt_Campo', 'Comun/lbl_ErrorCampo', ENVIAR, TITULO, MENSAJE])
Configuracion.detenerSiHayPendientes(CASO, pendientes)

def solicitar = { String correo, String paso ->
	Pagina.abrir(RUTA)
	Pagina.escribir('email', correo)
	WebUI.click(findTestObject(ENVIAR))
	WebUI.delay(2)
	String titulo = Pagina.texto(TITULO, null, Configuracion.timeoutLargo())
	long limite = System.currentTimeMillis() + Configuracion.timeoutLargo() * 1000L
	while (titulo != 'Revisa tu correo' && System.currentTimeMillis() < limite) {
		WebUI.delay(1)
		titulo = Pagina.texto(TITULO, null, 1)
	}
	String mensaje = Pagina.texto(MENSAJE)
	Evidencia.capturar(CASO, paso)
	Evidencia.registrar(CASO, "$paso -> título '$titulo', mensaje '$mensaje'")
	return [titulo: titulo, mensaje: mensaje]
}

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que el usuario abre el formulario de recuperación de contraseña')
	Pagina.abrir(RUTA)
	Pagina.escribir('email', 'correo-invalido')
	WebUI.click(findTestObject(ENVIAR))
	String error = Pagina.errorDe('email')
	Evidencia.registrar(CASO, "Correo con formato inválido -> '$error'")
	if (!error || Pagina.texto(TITULO) == 'Revisa tu correo') {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el formulario de recuperación aceptó un correo con formato inválido (error mostrado: '$error').")
	}

	WebUI.comment('Cuando envía un correo registrado y luego uno no registrado')
	Map registrado = solicitar(GlobalVariable.USUARIO_ESTUDIANTE.toString(), '01_correo_registrado')
	Map noRegistrado = solicitar("qa.katalon.no.registrado.${System.currentTimeMillis()}@example.com", '02_correo_no_registrado')

	WebUI.comment('Entonces muestra siempre el mismo mensaje genérico con la vigencia de 5 minutos')
	if (registrado.titulo != 'Revisa tu correo' || noRegistrado.titulo != 'Revisa tu correo') {
		KeywordUtil.markFailed("[$CASO] DEFECTO: la solicitud no llegó al estado de confirmación (registrado: '${registrado.titulo}', no registrado: '${noRegistrado.titulo}').")
	}
	if (registrado.mensaje != noRegistrado.mensaje) {
		KeywordUtil.markFailed("[$CASO] DEFECTO DE PRIVACIDAD: el mensaje difiere según el correo exista o no ('${registrado.mensaje}' vs '${noRegistrado.mensaje}'). Incumple #486.")
	}
	if (!(registrado.mensaje =~ /(?i)si el correo est[aá] registrado/)) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el mensaje no es el genérico de privacidad ('${registrado.mensaje}').")
	}
	if (!(registrado.mensaje =~ /5 minutos/)) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el mensaje no informa la vigencia de 5 minutos del enlace ('${registrado.mensaje}').")
	}
	Evidencia.registrar(CASO, "Solicitud enviada para ${GlobalVariable.USUARIO_ESTUDIANTE} a las ${new Date().format('yyyy-MM-dd HH:mm:ss')}: verificar la recepción del correo de recuperación.")
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
