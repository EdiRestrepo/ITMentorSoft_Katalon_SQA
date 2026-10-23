import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.util.KeywordUtil as KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import itmentorsoft.Autenticacion
import itmentorsoft.Configuracion
import itmentorsoft.Evidencia
import itmentorsoft.Pagina

/*
 * TC14 - Ruta de aprendizaje personalizada y recomendaciones de recursos
 * Azure Test Plans #436 (generación y actualización de la ruta), #446 (recepción de la ruta personalizada) y
 * #461 (confirmación de recurso finalizado). Complementa #426 (actualización del progreso sin recargar).
 *
 * PASA si:  (1) la ruta presenta temas y recursos recomendados, sin duplicados, con título, descripción y valoración;
 *           (2) los temas de la ruta corresponden a las áreas de mejora del perfil de conocimiento (< 70 %);
 *           (3) los totales (Temas, Recursos, Vistos, Avance general) coinciden con la lista;
 *           (4) al marcar un recurso como visto se actualizan "Vistos", el avance general y el del tema sin recargar
 *               la página, y al desmarcarlo vuelven a su valor (la prueba deja la ruta como estaba).
 * FALLA si: la ruta no se genera, contiene duplicados, sus totales no cuadran o el avance no se actualiza.
 * Observación: #461 describe una evaluación de seguimiento de 10 preguntas al finalizar un recurso, que la aplicación
 * no ofrece (se registra advertencia).
 */
final String CASO = 'TC14'
final String R = 'Estudiante/Page_Ruta/'
final int UMBRAL_DEBILIDAD = 70

List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS + [R + 'cnt_Ruta', R + 'lbl_Estadistica', R + 'lst_Temas', R + 'lst_NombresTemas', R + 'lst_Recursos',
	R + 'lst_TitulosRecursos', R + 'lst_MarcadosVistos', R + 'btn_MarcarVisto', R + 'lbl_PctTema', 'Estudiante/Page_Progreso/lst_PerfilTemas'])
pendientes += Autenticacion.pendientesDeCuenta('ESTUDIANTE')
Configuracion.detenerSiHayPendientes(CASO, pendientes)

def numero = { String t -> ((t ?: '') =~ /\d+/).with { it.find() ? (it.group() as int) : -1 } }
def estadisticas = { -> ['Temas', 'Recursos', 'Vistos', 'Avance general'].collectEntries { [it, numero(Pagina.texto(R + 'lbl_Estadistica', [nombre: it], 2))] } }

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que el estudiante tiene debilidades identificadas en sus evaluaciones')
	Autenticacion.iniciarSesion('ESTUDIANTE', CASO)
	Pagina.abrir('/student/progress', 4)
	List<String> debiles = Pagina.textos('Estudiante/Page_Progreso/lst_PerfilTemas').findAll { (it =~ /(\d+)%$/).with { m -> m.find() && (m.group(1) as int) < UMBRAL_DEBILIDAD } }
		.collect { it.replaceAll(/\s+\d+%$/, '').trim() }
	Evidencia.registrar(CASO, "Áreas de mejora del perfil (< $UMBRAL_DEBILIDAD %): $debiles")

	WebUI.comment('Cuando el sistema genera la lista personalizada de recursos')
	Pagina.abrir('/student/route', 4)
	if (!WebUI.waitForElementVisible(findTestObject(R + 'lst_Recursos'), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)) {
		Evidencia.capturar(CASO, 'precondicion_ruta_no_disponible')
		KeywordUtil.markErrorAndStop("[$CASO] Precondición no cumplida: la ruta no presenta recursos (${Pagina.texto(R + 'cnt_Ruta')}). Ejecutar antes TC12 para que el estudiante tenga una evaluación calificada.")
	}
	List<String> temas = Pagina.textos(R + 'lst_NombresTemas')
	List<String> titulos = Pagina.textos(R + 'lst_TitulosRecursos').collect { it.replace('↗', '').trim() }
	List<String> items = Pagina.textos(R + 'lst_Recursos')
	Map<String, Integer> stats = estadisticas()
	int vistos = Pagina.contar(R + 'lst_MarcadosVistos')
	Evidencia.capturar(CASO, '01_ruta_personalizada')
	Evidencia.registrar(CASO, "Ruta: temas $temas, ${titulos.size()} recursos, vistos $vistos, estadísticas $stats")
	titulos.each { Evidencia.registrar(CASO, "Recurso recomendado: $it") }

	WebUI.comment('Entonces muestra recursos sin duplicados, asociados a sus debilidades, con totales consistentes')
	if (titulos.size() != titulos.unique(false).size()) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: la ruta contiene recursos duplicados: ${titulos.countBy { it }.findAll { it.value > 1 }.keySet()} (#436).")
	}
	if ([items, titulos].transpose().any { item, titulo -> !(item.contains('★') && item.length() > titulo.length() + 20) }) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: hay recursos de la ruta sin descripción o valoración (#436).")
	}
	if (stats.Temas != temas.size() || stats.Recursos != titulos.size() || stats.Vistos != vistos) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: las estadísticas de la ruta $stats no coinciden con la lista (${temas.size()} temas, ${titulos.size()} recursos, $vistos vistos).")
	}
	if (stats['Avance general'] != Math.round(vistos * 100.0 / Math.max(titulos.size(), 1))) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el avance general (${stats['Avance general']} %) no corresponde a $vistos de ${titulos.size()} recursos vistos (#426).")
	}
	if (debiles && !temas.every { it in debiles }) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: la ruta incluye temas ${temas - debiles} que no son áreas de mejora del estudiante ($debiles) (#436/#446).")
	}

	WebUI.comment('Y cuando marca un recurso como visto (finalizado)')
	String recurso = titulos[0]
	String pctTemaAntes = Pagina.textos(R + 'lbl_PctTema')[0]
	WebUI.click(findTestObject(R + 'btn_MarcarVisto', [titulo: recurso]))
	WebUI.delay(3)
	Map<String, Integer> statsVisto = estadisticas()
	String pctTemaVisto = Pagina.textos(R + 'lbl_PctTema')[0]
	String presionado = WebUI.getAttribute(findTestObject(R + 'btn_MarcarVisto', [titulo: recurso]), 'aria-pressed', FailureHandling.OPTIONAL)
	Evidencia.capturar(CASO, '02_recurso_marcado_visto')
	Evidencia.registrar(CASO, "Recurso '$recurso' marcado como visto: aria-pressed=$presionado, estadísticas $statsVisto, avance del tema $pctTemaAntes -> $pctTemaVisto")

	WebUI.comment('Entonces el sistema actualiza el progreso sin recargar la página')
	if (presionado != 'true' || statsVisto.Vistos != stats.Vistos + 1) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: al marcar el recurso como visto no se actualiza su estado ni el contador de vistos (${stats.Vistos} -> ${statsVisto.Vistos}) (#461/#426).")
	}
	if (statsVisto['Avance general'] != Math.round((stats.Vistos + 1) * 100.0 / titulos.size()) || pctTemaVisto == pctTemaAntes) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el porcentaje de avance no se recalcula al completar un recurso (general ${statsVisto['Avance general']} %, tema $pctTemaAntes -> $pctTemaVisto) (#426).")
	}

	WebUI.comment('Y al desmarcarlo vuelve al estado anterior (limpieza)')
	WebUI.click(findTestObject(R + 'btn_MarcarVisto', [titulo: recurso]))
	WebUI.delay(3)
	Map<String, Integer> statsFinal = estadisticas()
	Evidencia.registrar(CASO, "Recurso '$recurso' desmarcado: estadísticas $statsFinal")
	if (statsFinal.Vistos != stats.Vistos) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: al quitar el recurso de vistos el contador no vuelve a ${stats.Vistos} (queda en ${statsFinal.Vistos}).")
	}
	KeywordUtil.markWarning("[$CASO] Observación #461: al finalizar un recurso la aplicación no ofrece la evaluación de seguimiento de 10 preguntas aleatorias descrita en Azure.")
	KeywordUtil.markWarning("[$CASO] Observación #446: la aplicación no registra intereses del estudiante; las recomendaciones se basan solo en las debilidades.")
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
