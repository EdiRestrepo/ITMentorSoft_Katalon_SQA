package itmentorsoft

import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import org.openqa.selenium.WebElement

import com.kms.katalon.core.annotation.Keyword
import com.kms.katalon.core.model.FailureHandling
import com.kms.katalon.core.util.KeywordUtil
import com.kms.katalon.core.webui.common.WebUiCommonHelper
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

/**
 * Inicio de sesión según HU 1.1.2 (Azure #181): botón "Login" (CA1), email + password,
 * botón "Enviar" (CA2) y redirección a la pantalla de bienvenida del rol (CA5).
 *
 * Las contraseñas NUNCA se guardan en el proyecto: se leen de variables de entorno
 * y se escriben directamente en el campo para que no queden en el log de Katalon.
 */
class Autenticacion {

	static final String BTN_LOGIN = 'Autenticacion/Page_Inicio/btn_Login'
	static final String TXT_EMAIL = 'Autenticacion/Page_Login/txt_Email'
	static final String TXT_PASSWORD = 'Autenticacion/Page_Login/txt_Password'
	static final String BTN_ENVIAR = 'Autenticacion/Page_Login/btn_Enviar'
	static final String CNT_BIENVENIDA = 'Autenticacion/Page_Bienvenida/cnt_Bienvenida'

	static final List<String> OBJETOS = [BTN_LOGIN, TXT_EMAIL, TXT_PASSWORD, BTN_ENVIAR, CNT_BIENVENIDA]

	/** Cuenta lógica -> [variable del perfil con el email, variable de entorno con la contraseña]. */
	static final Map<String, Map<String, String>> CUENTAS = [
		'ESTUDIANTE_A'   : [usuario: 'USUARIO_ESTUDIANTE_A', entorno: 'ITM_PWD_ESTUDIANTE_A'],
		'ESTUDIANTE'     : [usuario: 'USUARIO_ESTUDIANTE_A', entorno: 'ITM_PWD_ESTUDIANTE_A'],
		'ESTUDIANTE_EVAL': [usuario: 'USUARIO_ESTUDIANTE_EVAL', entorno: 'ITM_PWD_ESTUDIANTE_EVAL'],
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
		List<String> pendientes = Configuracion.variablesPendientes([datos.usuario])
		if (Configuracion.esPendiente(System.getenv(datos.entorno))) {
			pendientes << "Variable de entorno del sistema ${datos.entorno} (contraseña de la cuenta $cuenta)"
		}
		return pendientes
	}

	/**
	 * Inicia sesión con la cuenta indicada. Si no logra llegar a la pantalla de bienvenida,
	 * detiene el caso con ERROR: es una precondición no cumplida, no el resultado del caso.
	 */
	@Keyword
	static void iniciarSesion(String cuenta, String caso) {
		Map<String, String> datos = CUENTAS[cuenta]
		String usuario = Configuracion.leerVariable(datos.usuario).toString()
		String password = System.getenv(datos.entorno)

		WebUI.navigateToUrl(Configuracion.url('/'))
		WebUI.waitForPageLoad(Configuracion.timeoutLargo())
		WebUI.waitForElementClickable(findTestObject(BTN_LOGIN), Configuracion.timeoutLargo())
		WebUI.click(findTestObject(BTN_LOGIN))

		WebUI.waitForElementVisible(findTestObject(TXT_EMAIL), Configuracion.timeoutCorto())
		WebUI.setText(findTestObject(TXT_EMAIL), usuario)
		WebElement campoPassword = WebUiCommonHelper.findWebElement(findTestObject(TXT_PASSWORD), Configuracion.timeoutCorto())
		campoPassword.clear()
		campoPassword.sendKeys(password)
		WebUI.click(findTestObject(BTN_ENVIAR))

		if (!WebUI.waitForElementVisible(findTestObject(CNT_BIENVENIDA), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)) {
			Evidencia.capturar(caso, "precondicion_login_${cuenta}_fallido")
			KeywordUtil.markErrorAndStop("[$caso] Precondición no cumplida: no fue posible iniciar sesión con la cuenta $cuenta " +
					"(usuario configurado en ${datos.usuario}). Revisar la cuenta de prueba, la contraseña en ${datos.entorno} y los localizadores de login.")
		}
		Evidencia.registrar(caso, "Sesión iniciada con la cuenta $cuenta")
	}

	/** Cierra la sesión eliminando las cookies del dominio (el JWT viaja en cookie HttpOnly según HU 1.1.2 CA4). */
	@Keyword
	static void cerrarSesion(String caso) {
		WebUI.deleteAllCookies()
		Evidencia.registrar(caso, 'Sesión cerrada (cookies eliminadas)')
	}
}
