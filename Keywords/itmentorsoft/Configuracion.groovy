package itmentorsoft

import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import com.kms.katalon.core.annotation.Keyword
import com.kms.katalon.core.model.FailureHandling
import com.kms.katalon.core.testdata.TestData
import com.kms.katalon.core.testobject.TestObject
import com.kms.katalon.core.util.KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import internal.GlobalVariable

/**
 * Detecta la configuración que falta para ejecutar una prueba (URL, rutas, localizadores,
 * datos, cuentas) y detiene el caso con estado ERROR en lugar de reportar un éxito falso.
 *
 * Convención: cualquier valor vacío o que contenga "TODO" se considera pendiente.
 */
class Configuracion {

	static final String MARCA_PENDIENTE = 'TODO'

	static boolean esPendiente(Object valor) {
		return valor == null || valor.toString().trim().isEmpty() || valor.toString().contains(MARCA_PENDIENTE)
	}

	static Object leerVariable(String nombre) {
		try {
			return GlobalVariable."$nombre"
		} catch (MissingPropertyException ignored) {
			return null
		}
	}

	/** Variables globales del perfil de ejecución que siguen vacías o con TODO. */
	@Keyword
	static List<String> variablesPendientes(List<String> nombres) {
		return nombres.findAll { esPendiente(leerVariable(it)) }.collect { "Perfil > GlobalVariable.$it" }
	}

	/** Objetos del Object Repository inexistentes o con localizador provisional TODO. */
	@Keyword
	static List<String> objetosPendientes(List<String> rutas) {
		List<String> pendientes = []
		rutas.each { ruta ->
			TestObject objeto = findTestObject(ruta)
			if (objeto == null) {
				pendientes << "Object Repository > $ruta (no existe)"
				return
			}
			List<String> valores = []
			valores.addAll(objeto.getSelectorCollection()?.values()?.findAll { it } ?: [])
			valores.addAll(objeto.getProperties()?.collect { it.getValue() }?.findAll { it } ?: [])
			if (valores.isEmpty() || valores.any { it.contains(MARCA_PENDIENTE) }) {
				pendientes << "Object Repository > $ruta (localizador provisional)"
			}
		}
		return pendientes
	}

	/** Celdas de un Data File con TODO. Las columnas opcionales pueden quedar vacías. */
	@Keyword
	static List<String> datosPendientes(TestData datos, List<String> columnasObligatorias) {
		List<String> pendientes = []
		(1..datos.getRowNumbers()).each { fila ->
			columnasObligatorias.each { columna ->
				if (esPendiente(datos.getValue(columna, fila))) {
					pendientes << "Data Files > ${datos.getSourceUrl()} fila $fila columna '$columna'"
				}
			}
		}
		return pendientes
	}

	/**
	 * Si hay pendientes, los registra (log + archivo de evidencia) y detiene el caso con ERROR.
	 * ERROR significa "prueba no ejecutada por configuración incompleta", nunca "defecto del sistema".
	 */
	@Keyword
	static void detenerSiHayPendientes(String caso, List<String> pendientes) {
		if (pendientes.isEmpty()) {
			KeywordUtil.logInfo("[$caso] Configuración completa. Perfil: ${com.kms.katalon.core.configuration.RunConfiguration.getExecutionProfile()}")
			return
		}
		String detalle = pendientes.unique().collect { " - $it" }.join('\n')
		Evidencia.registrar(caso, "CONFIGURACION PENDIENTE (la prueba funcional NO se ejecutó):\n$detalle")
		KeywordUtil.markErrorAndStop("[$caso] CONFIGURACIÓN PENDIENTE: la prueba funcional NO se ejecutó y no hay resultado funcional. " +
				"Completar (ver Documentacion/Pendientes_Integracion.md):\n$detalle")
	}

	/** URL absoluta a partir de BASE_URL y una ruta relativa del perfil. */
	@Keyword
	static String url(Object ruta) {
		String base = GlobalVariable.BASE_URL.toString().replaceAll('/+$', '')
		String texto = ruta.toString().trim()
		String relativa = texto.startsWith('/') ? texto : '/' + texto
		return base + relativa
	}

	/** Espera hasta que el texto aparezca en la página (útil en SPA donde el contenido llega después de la carga). */
	@Keyword
	static boolean esperarTexto(String texto, int segundos) {
		long limite = System.currentTimeMillis() + segundos * 1000L
		while (System.currentTimeMillis() < limite) {
			if (WebUI.verifyTextPresent(texto, false, FailureHandling.OPTIONAL)) {
				return true
			}
			WebUI.delay(1)
		}
		return false
	}

	static int timeoutCorto() {
		return (GlobalVariable.TIMEOUT_CORTO ?: 5) as int
	}

	static int timeoutLargo() {
		return (GlobalVariable.TIMEOUT_LARGO ?: 20) as int
	}
}
