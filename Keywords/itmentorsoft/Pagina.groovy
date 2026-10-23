package itmentorsoft

import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import org.openqa.selenium.WebElement

import com.kms.katalon.core.annotation.Keyword
import com.kms.katalon.core.model.FailureHandling
import com.kms.katalon.core.testobject.TestObject
import com.kms.katalon.core.webui.common.WebUiCommonHelper
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

/**
 * Utilidades de lectura de la interfaz para los casos de prueba: listas de elementos, textos,
 * notificaciones (toast) y navegación por rutas relativas a BASE_URL.
 */
class Pagina {

	static final String TOAST_TITULO = 'Comun/lbl_ToastTitulo'
	static final String TOAST_MENSAJE = 'Comun/lbl_ToastMensaje'
	/** Angular copia el id estático también al componente anfitrión (app-input): se busca el control nativo. */
	static final String CAMPO_JS = "var i=arguments[0]; var e=document.querySelector('input#'+i+',textarea#'+i+',select#'+i); "

	static TestObject objeto(String ruta, Map parametros = null) {
		return parametros ? findTestObject(ruta, parametros) : findTestObject(ruta)
	}

	/** Elementos visibles que coinciden con el objeto (lista vacía si no hay). */
	@Keyword
	static List<WebElement> elementos(String ruta, Map parametros = null, int segundos = 0) {
		try {
			return WebUiCommonHelper.findWebElements(objeto(ruta, parametros), segundos).findAll { it.isDisplayed() }
		} catch (Exception ignored) {
			return []
		}
	}

	@Keyword
	static int contar(String ruta, Map parametros = null) {
		return elementos(ruta, parametros).size()
	}

	/** Textos (sin espacios repetidos) de todos los elementos visibles del objeto. */
	@Keyword
	static List<String> textos(String ruta, Map parametros = null) {
		return elementos(ruta, parametros).collect { it.getText().replaceAll('\\s+', ' ').trim() }
	}

	/** Texto del primer elemento visible, o null si no aparece en el tiempo indicado. */
	@Keyword
	static String texto(String ruta, Map parametros = null, int segundos = Configuracion.timeoutCorto()) {
		TestObject o = objeto(ruta, parametros)
		if (!WebUI.waitForElementVisible(o, segundos, FailureHandling.OPTIONAL)) {
			return null
		}
		return WebUI.getText(o, FailureHandling.OPTIONAL)?.replaceAll('\\s+', ' ')?.trim()
	}

	@Keyword
	static boolean visible(String ruta, Map parametros = null, int segundos = Configuracion.timeoutCorto()) {
		return WebUI.waitForElementVisible(objeto(ruta, parametros), segundos, FailureHandling.OPTIONAL)
	}

	/** Espera una notificación emergente y devuelve "título | mensaje", o null si no aparece. */
	@Keyword
	static String esperarToast(int segundos = Configuracion.timeoutLargo()) {
		String titulo = texto(TOAST_TITULO, null, segundos)
		if (titulo == null) {
			return null
		}
		String mensaje = texto(TOAST_MENSAJE, null, 1)
		return mensaje ? "$titulo | $mensaje" : titulo
	}

	/** Navega a una ruta relativa a BASE_URL y espera a que la SPA cargue. */
	@Keyword
	static void abrir(String ruta, int esperaSegundos = 2) {
		WebUI.navigateToUrl(Configuracion.url(ruta))
		WebUI.waitForPageLoad(Configuracion.timeoutLargo())
		WebUI.delay(esperaSegundos)
	}

	/** Ruta actual (sin dominio ni parámetros). */
	@Keyword
	static String rutaActual() {
		return new URI(WebUI.getUrl()).getPath()
	}

	/** Espera a que la ruta actual empiece por el prefijo indicado. */
	@Keyword
	static boolean esperarRuta(String prefijo, int segundos = Configuracion.timeoutLargo()) {
		long limite = System.currentTimeMillis() + segundos * 1000L
		while (System.currentTimeMillis() < limite) {
			if (rutaActual().startsWith(prefijo)) {
				return true
			}
			WebUI.delay(1)
		}
		return false
	}

	/** Escribe en un campo del formulario por su id y lo marca como tocado (blur) para que muestre su validación. */
	@Keyword
	static void escribir(String campo, String valor) {
		TestObject o = findTestObject('Comun/txt_Campo', [campo: campo])
		WebUI.waitForElementVisible(o, Configuracion.timeoutLargo())
		WebUI.clearText(o, FailureHandling.OPTIONAL)
		WebUI.executeJavaScript(CAMPO_JS + "e.value=''; e.dispatchEvent(new Event('input',{bubbles:true}));", [campo])
		if (valor) {
			WebUI.sendKeys(o, valor)
		}
		WebUI.executeJavaScript(CAMPO_JS + "e.dispatchEvent(new Event('blur',{bubbles:true})); e.dispatchEvent(new Event('focusout',{bubbles:true}));", [campo])
	}

	/** Mensaje de validación visible de un campo (componente app-form-field), o null si no hay. */
	@Keyword
	static String errorDe(String campo) {
		return texto('Comun/lbl_ErrorCampo', [campo: campo], 2)
	}
}
