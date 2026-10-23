package itmentorsoft

import org.openqa.selenium.JavascriptExecutor

import com.kms.katalon.core.annotation.Keyword
import com.kms.katalon.core.webui.driver.DriverFactory
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

/**
 * Llamadas a la API de ITMentorSoft desde el navegador, con la sesión del usuario autenticado.
 *
 * El frontend Angular envía el JWT de sessionStorage ('auth_token') como "Authorization: Bearer"
 * y usa la misma URL base que la página. Aquí se hace lo mismo con fetch(), de modo que la petición
 * es exactamente la que podría fabricar un usuario manipulando la aplicación (por ejemplo, cambiando un id).
 * Permite verificar el control de acceso en el backend y no solo en la interfaz.
 */
class Api {

	static final String SCRIPT_GET = '''
		var callback = arguments[arguments.length - 1];
		var token = sessionStorage.getItem('auth_token');
		fetch(arguments[0], {credentials: 'include', headers: token ? {Authorization: 'Bearer ' + token} : {}})
			.then(function (r) { return r.text().then(function (t) { callback({status: r.status, body: t}); }); })
			.catch(function (e) { callback({status: -1, body: String(e)}); });
	'''

	/** GET a una ruta relativa a BASE_URL. Devuelve [status: int, body: String]; status -1 si no hubo respuesta. */
	@Keyword
	static Map<String, Object> get(String ruta) {
		JavascriptExecutor navegador = (JavascriptExecutor) DriverFactory.getWebDriver()
		Map respuesta = navegador.executeAsyncScript(SCRIPT_GET, Configuracion.url(ruta)) as Map
		return [status: (respuesta?.status ?: -1) as int, body: (respuesta?.body ?: '').toString()]
	}

	/** Valor de sessionStorage del frontend (por ejemplo 'auth_user_id'). */
	@Keyword
	static String valorDeSesion(String clave) {
		return WebUI.executeJavaScript('return sessionStorage.getItem(arguments[0])', [clave])?.toString()
	}

	static boolean esExito(int status) {
		return status >= 200 && status < 300
	}

	/** Interpreta un texto JSON con el navegador (devuelve Map/List) o null si no es JSON válido. */
	static Object json(String texto) {
		return WebUI.executeJavaScript('try { return JSON.parse(arguments[0]); } catch (e) { return null; }', [texto])
	}

	/** Recorta el cuerpo de la respuesta para la bitácora. */
	static String resumen(Map<String, Object> respuesta) {
		String body = respuesta.body.toString().replaceAll('\\s+', ' ')
		return "HTTP ${respuesta.status} ${body.length() > 300 ? body.substring(0, 300) + '…' : body}"
	}
}
