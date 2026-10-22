package itmentorsoft

import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.kms.katalon.core.annotation.Keyword
import com.kms.katalon.core.configuration.RunConfiguration
import com.kms.katalon.core.model.FailureHandling
import com.kms.katalon.core.util.KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

/**
 * Evidencias propias de cada caso, además del reporte estándar de Katalon (carpeta Reports/):
 *   Evidencias/<caso>/<fecha>_<paso>.png  capturas de pantalla por paso
 *   Evidencias/<caso>/bitacora.txt         registro de pasos, verificaciones y configuración pendiente
 */
class Evidencia {

	static final String CARPETA = 'Evidencias'

	static File carpetaDe(String caso) {
		File carpeta = Paths.get(RunConfiguration.getProjectDir(), CARPETA, caso).toFile()
		carpeta.mkdirs()
		return carpeta
	}

	static String marcaDeTiempo() {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern('yyyyMMdd_HHmmss_SSS'))
	}

	@Keyword
	static String capturar(String caso, String paso) {
		String archivo = new File(carpetaDe(caso), "${marcaDeTiempo()}_${paso}.png").getAbsolutePath()
		WebUI.takeScreenshot(archivo, FailureHandling.OPTIONAL)
		registrar(caso, "Captura: $archivo")
		return archivo
	}

	@Keyword
	static void registrar(String caso, String texto) {
		KeywordUtil.logInfo("[$caso] $texto")
		new File(carpetaDe(caso), 'bitacora.txt').append("${marcaDeTiempo()} | $texto\n", 'UTF-8')
	}
}
