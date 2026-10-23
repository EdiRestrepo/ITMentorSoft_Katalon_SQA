package itmentorsoft

import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import java.nio.file.Paths

import org.openqa.selenium.WebElement

import com.kms.katalon.core.annotation.Keyword
import com.kms.katalon.core.configuration.RunConfiguration
import com.kms.katalon.core.model.FailureHandling
import com.kms.katalon.core.util.KeywordUtil
import com.kms.katalon.core.webui.common.WebUiCommonHelper
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

/**
 * Inicio de sesión de ITMentorSoft (HU 1.1.2, Azure #181) tal como funciona en https://itmentorsoft.com:
 *   1. /login: correo + contraseña y botón "Iniciar sesión".
 *   2. /otp: "Verificación en dos pasos". El sistema envía un código de 6 caracteres al correo del usuario.
 *      El código es de un solo uso, por eso lo escribe el tester en el navegador que abrió Katalon.
 *   3. Redirección al área del rol (/student, /teacher o /admin), con el menú lateral visible (CA5).
 *
 * Las contraseñas NUNCA se guardan en el proyecto: se leen de variables de entorno
 * y se escriben directamente en el campo para que no queden en el log de Katalon.
 */
class Autenticacion {

	static final String TXT_EMAIL = 'Autenticacion/Page_Login/txt_Email'
	static final String TXT_PASSWORD = 'Autenticacion/Page_Login/txt_Password'
	static final String BTN_ENVIAR = 'Autenticacion/Page_Login/btn_Enviar'
	static final String TXT_OTP = 'Autenticacion/Page_Otp/txt_Otp'
	static final String BTN_VERIFICAR = 'Autenticacion/Page_Otp/btn_Verificar'
	static final String CNT_BIENVENIDA = 'Autenticacion/Page_Bienvenida/cnt_Bienvenida'
	static final String BTN_OMITIR_GUIA = 'Autenticacion/Page_Bienvenida/btn_OmitirGuia'

	static final List<String> OBJETOS = [TXT_EMAIL, TXT_PASSWORD, BTN_ENVIAR, TXT_OTP, BTN_VERIFICAR, CNT_BIENVENIDA]

	/** Cuenta lógica -> [variable del perfil con el email, variable de entorno con la contraseña]. */
	static final Map<String, Map<String, String>> CUENTAS = [
		'ESTUDIANTE'     : [usuario: 'USUARIO_ESTUDIANTE', entorno: 'ITM_PWD_ESTUDIANTE'],
		'ESTUDIANTE_A'   : [usuario: 'USUARIO_ESTUDIANTE', entorno: 'ITM_PWD_ESTUDIANTE'],
		'ESTUDIANTE_EVAL': [usuario: 'USUARIO_ESTUDIANTE', entorno: 'ITM_PWD_ESTUDIANTE'],
		'DOCENTE'        : [usuario: 'USUARIO_DOCENTE', entorno: 'ITM_PWD_DOCENTE'],
		'ADMINISTRADOR'  : [usuario: 'USUARIO_ADMINISTRADOR', entorno: 'ITM_PWD_ADMINISTRADOR'],
	]

	/** Configuración faltante para usar una cuenta (email en el perfil y contraseña en el entorno). */
	@Keyword
	static List<String> pendientesDeCuenta(String cuenta) {
		Map<String, String> datos = CUENTAS[cuenta]
		if (datos == null) {
			return ["Cuenta '$cuenta' no definida en Keywords/itmentorsoft/Autenticacion.groovy"]
		}
		List<String> pendientes = Configuracion.variablesPendientes([datos.usuario, 'RUTA_LOGIN'])
		if (Configuracion.esPendiente(System.getenv(datos.entorno))) {
			pendientes << "Variable de entorno del sistema ${datos.entorno} (contraseña de la cuenta $cuenta)"
		}
		return pendientes
	}

	/**
	 * Inicia sesión con la cuenta indicada. Si no logra llegar al área autenticada,
	 * detiene el caso con ERROR: es una precondición no cumplida, no el resultado del caso.
	 */
	@Keyword
	static void iniciarSesion(String cuenta, String caso) {
		Map<String, String> datos = CUENTAS[cuenta]
		String usuario = Configuracion.leerVariable(datos.usuario).toString()
		String password = System.getenv(datos.entorno)

		WebUI.navigateToUrl(Configuracion.url(Configuracion.leerVariable('RUTA_LOGIN')))
		WebUI.waitForPageLoad(Configuracion.timeoutLargo())
		WebUI.waitForElementVisible(findTestObject(TXT_EMAIL), Configuracion.timeoutLargo())
		WebUI.setText(findTestObject(TXT_EMAIL), usuario)
		WebElement campoPassword = WebUiCommonHelper.findWebElement(findTestObject(TXT_PASSWORD), Configuracion.timeoutCorto())
		campoPassword.clear()
		campoPassword.sendKeys(password)
		WebUI.click(findTestObject(BTN_ENVIAR))

		if (!WebUI.waitForElementVisible(findTestObject(TXT_OTP), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)) {
			Evidencia.capturar(caso, "precondicion_login_${cuenta}_fallido")
			KeywordUtil.markErrorAndStop("[$caso] Precondición no cumplida: con la cuenta $cuenta (usuario configurado en ${datos.usuario}) no se llegó a la verificación OTP. " +
					"Revisar la contraseña en ${datos.entorno}, que la cuenta no esté bloqueada y la captura de evidencia.")
		}

		int espera = Configuracion.timeoutOtp()
		String aviso = "ACCIÓN MANUAL: escriba en el navegador el código OTP enviado a $usuario y pulse 'Verificar e ingresar' (espera máxima $espera s)."
		Evidencia.registrar(caso, aviso)
		WebUI.comment(aviso)
		WebUI.click(findTestObject(TXT_OTP), FailureHandling.OPTIONAL)

		if (!esperarOtp(usuario, espera)) {
			Evidencia.capturar(caso, "precondicion_otp_${cuenta}_no_completado")
			KeywordUtil.markErrorAndStop("[$caso] Precondición no cumplida: no se completó la verificación OTP de la cuenta $cuenta en $espera s " +
					"(código no ingresado, incorrecto o vencido). Aumentar TIMEOUT_OTP en el perfil si hace falta más tiempo.")
		}
		Evidencia.registrar(caso, "Sesión iniciada con la cuenta $cuenta (${WebUI.getUrl()})")
		omitirGuia(caso)
	}

	/** Cierra la guía de bienvenida si está abierta: Katalon usa un perfil de navegador nuevo y la guía aparece en cada ejecución. */
	static void omitirGuia(String caso) {
		if (WebUI.waitForElementClickable(findTestObject(BTN_OMITIR_GUIA), Configuracion.timeoutCorto(), FailureHandling.OPTIONAL)) {
			WebUI.click(findTestObject(BTN_OMITIR_GUIA))
			WebUI.waitForElementNotPresent(findTestObject(BTN_OMITIR_GUIA), Configuracion.timeoutCorto(), FailureHandling.OPTIONAL)
			Evidencia.registrar(caso, 'Guía de bienvenida omitida')
		}
	}

	/**
	 * Espera a que la sesión llegue al área autenticada. El código OTP se puede escribir a mano en el navegador
	 * o dejar en Evidencias/otp/codigo.txt (canal opcional para ejecuciones asistidas por consola): mientras espera,
	 * el keyword publica el usuario en Evidencias/otp/ESPERANDO.txt y, si aparece codigo.txt, lo escribe y lo envía.
	 */
	static boolean esperarOtp(String usuario, int segundos) {
		File carpeta = Paths.get(RunConfiguration.getProjectDir(), Evidencia.CARPETA, 'otp').toFile()
		carpeta.mkdirs()
		File esperando = new File(carpeta, 'ESPERANDO.txt')
		File codigo = new File(carpeta, 'codigo.txt')
		codigo.delete()
		esperando.setText("$usuario\n", 'UTF-8')
		try {
			long limite = System.currentTimeMillis() + segundos * 1000L
			while (System.currentTimeMillis() < limite) {
				if (WebUI.waitForElementVisible(findTestObject(CNT_BIENVENIDA), 1, FailureHandling.OPTIONAL)) {
					return true
				}
				if (codigo.exists()) {
					String valor = codigo.getText('UTF-8').trim()
					codigo.delete()
					if (valor) {
						WebUI.setText(findTestObject(TXT_OTP), valor)
						WebUI.click(findTestObject(BTN_VERIFICAR))
						KeywordUtil.logInfo("Código OTP tomado de Evidencias/otp/codigo.txt para $usuario")
					}
				}
			}
			return false
		} finally {
			esperando.delete()
		}
	}

	/** Cierra la sesión: el token vive en sessionStorage y el refresh token en cookies (ver AuthService.logout del frontend). */
	@Keyword
	static void cerrarSesion(String caso) {
		WebUI.executeJavaScript('sessionStorage.clear()', null, FailureHandling.OPTIONAL)
		WebUI.deleteAllCookies()
		Evidencia.registrar(caso, 'Sesión cerrada (sessionStorage y cookies eliminados)')
	}
}
