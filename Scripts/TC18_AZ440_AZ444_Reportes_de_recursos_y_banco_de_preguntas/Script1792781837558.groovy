import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.util.KeywordUtil as KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import itmentorsoft.Autenticacion
import itmentorsoft.Configuracion
import itmentorsoft.Evidencia
import itmentorsoft.Pagina

/*
 * TC18 - Reportes de contenido académico (valoración de recursos) y del banco de preguntas
 * Azure Test Plans #440 (reportes de contenido académico: recursos de menor valoración) y #444 (reportes de
 * debilidades por estudiante/tema)
 *
 * PASA si:  (1) "Recursos por nivel" y "por tema" muestran totales iguales a la suma de sus segmentos;
 *           (2) para cada tema, "Peor valorados" lista hasta 5 recursos ordenados de menor a mayor puntaje y
 *               "Mejor valorados" de mayor a menor (valores entre 0 y 5);
 *           (3) el reporte del banco de preguntas presenta gráficos por dificultad, estado y categoría con
 *               totales consistentes;
 *           (4) el reporte individual de un estudiante presenta su progreso por tema (insumo de debilidades, #444).
 * FALLA si: los totales no cuadran, el orden de valoración es incorrecto o falta un reporte.
 * Observación: #440 marca en rojo los promedios < 3.5 y #444 pide un consolidado de "debilidad común" por tema;
 * se registran como advertencias si la interfaz no los presenta.
 */
final String CASO = 'TC18'
final String R = 'Reportes/Page_Reportes/'

List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS + [R + 'cnt_ReporteRecursos', R + 'lst_MejorValorados', R + 'lst_PeorValorados', R + 'sel_TemaValoracion',
	R + 'cnt_ReportePreguntas', R + 'cnt_GraficoColumnas', R + 'lnk_VerReporte', 'Reportes/Page_DetalleEstudiante/cnt_DetalleEstudiante'])
pendientes += Autenticacion.pendientesDeCuenta('DOCENTE')
Configuracion.detenerSiHayPendientes(CASO, pendientes)

/** Total y segmentos de cada gráfico (torta, columnas, tarjetas) de un contenedor. */
def graficos = { String contenedor ->
	return WebUI.executeJavaScript('''
		return [...document.querySelectorAll(arguments[0] + ' app-pie-chart, ' + arguments[0] + ' app-column-chart, ' + arguments[0] + ' app-stat-cards')].map(g => ({
			titulo: ((c) => c && c.querySelector('h3') ? c.querySelector('h3').textContent.trim() : g.tagName)(g.closest('.qr__card, .rr__card')),
			total: parseInt((g.querySelector('.pc__total-num, .cc__total-num, .sc__total-num') || {}).textContent || '-1'),
			partes: [...g.querySelectorAll('.pc__count, .cc__value, .sc__value')].map(e => parseInt(e.textContent))
		}));''', [contenedor]) as List<Map>
}

def puntajes = { String ruta ->
	return Pagina.elementos(ruta).collect { it.findElement(org.openqa.selenium.By.cssSelector('.tc__score')).getText().trim() as BigDecimal }
}

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que existen calificaciones de recursos y evaluaciones registradas por estudiantes')
	Autenticacion.iniciarSesion('DOCENTE', CASO)
	Pagina.abrir('/teacher/reports', 4)
	WebUI.scrollToElement(findTestObject(R + 'cnt_ReporteRecursos'), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)

	WebUI.comment('Cuando el docente consulta el reporte de recursos')
	List<Map> recursos = graficos('app-resources-report')
	Evidencia.capturar(CASO, '01_reporte_recursos')
	recursos.each { g ->
		Evidencia.registrar(CASO, "Recursos - ${g.titulo}: total ${g.total}, segmentos ${g.partes}")
		if (g.partes.isEmpty() || g.partes.sum() != g.total) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: en '${g.titulo}' el total (${g.total}) no es la suma de sus segmentos (${g.partes}) (#440).")
		}
	}
	if (recursos.size() < 2) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el reporte de recursos no presenta los gráficos por nivel y por tema (${recursos.size()} encontrados).")
	}

	WebUI.comment('Entonces muestra los recursos peor y mejor valorados de cada tema, priorizados por puntaje')
	List<String> temas = WebUI.executeJavaScript("return [...document.querySelectorAll('app-top-content .tc__filter select option')].map(o => o.value);", null) as List<String>
	boolean marcaBaja = false
	temas.each { tema ->
		WebUI.selectOptionByValue(findTestObject(R + 'sel_TemaValoracion'), java.util.regex.Pattern.quote(tema), true)
		WebUI.delay(2)
		List<BigDecimal> peores = puntajes(R + 'lst_PeorValorados')
		List<BigDecimal> mejores = puntajes(R + 'lst_MejorValorados')
		Evidencia.registrar(CASO, "Tema '$tema' - peor valorados: $peores | mejor valorados: $mejores")
		if (peores.size() > 5 || mejores.size() > 5) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: el tema '$tema' lista más de 5 recursos en un ranking (#440).")
		}
		if (peores != peores.sort(false)) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: 'Peor valorados' del tema '$tema' no está ordenado de menor a mayor: $peores (#440).")
		}
		if (mejores != mejores.sort(false).reverse()) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: 'Mejor valorados' del tema '$tema' no está ordenado de mayor a menor: $mejores.")
		}
		if ((peores + mejores).any { it < 0 || it > 5 }) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: hay puntajes de valoración fuera del rango 0-5 en el tema '$tema'.")
		}
		marcaBaja = marcaBaja || WebUI.executeJavaScript("return !!document.querySelector('app-top-content [class*=\"low\"], app-top-content [class*=\"warn\"], app-top-content [class*=\"bad\"]');", null)
	}
	Evidencia.capturar(CASO, '02_mejor_y_peor_valorados')
	if (temas.isEmpty()) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el reporte de valoración no ofrece temas para consultar (#440).")
	}
	if (!marcaBaja) {
		KeywordUtil.markWarning("[$CASO] Observación #440: la interfaz no resalta los recursos con promedio menor a 3.5 ni muestra el porcentaje de recursos con baja valoración.")
	}

	WebUI.comment('Y presenta el reporte del banco de preguntas con gráficos consistentes')
	WebUI.scrollToElement(findTestObject(R + 'cnt_ReportePreguntas'), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)
	List<Map> preguntas = graficos('app-questions-report')
	Evidencia.capturar(CASO, '03_reporte_banco_preguntas')
	preguntas.each { g ->
		Evidencia.registrar(CASO, "Preguntas - ${g.titulo}: total ${g.total}, segmentos ${g.partes}")
		if (g.partes.isEmpty() || g.partes.sum() != g.total) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: en '${g.titulo}' el total (${g.total}) no es la suma de sus segmentos (${g.partes}).")
		}
	}
	if (preguntas.size() < 3 || !Pagina.visible(R + 'cnt_GraficoColumnas')) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el reporte del banco de preguntas no presenta los gráficos por dificultad, estado y categoría (${preguntas.size()} encontrados).")
	}

	WebUI.comment('Y el reporte individual muestra el progreso por tema del estudiante (insumo de debilidades)')
	Pagina.abrir('/teacher/reports', 3)
	WebUI.waitForElementVisible(findTestObject(R + 'lnk_VerReporte'), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)
	WebUI.click(findTestObject(R + 'lnk_VerReporte'))
	Pagina.visible('Reportes/Page_DetalleEstudiante/cnt_DetalleEstudiante', null, Configuracion.timeoutLargo())
	WebUI.delay(3)
	String detalle = Pagina.texto('Reportes/Page_DetalleEstudiante/cnt_DetalleEstudiante') ?: ''
	Evidencia.capturar(CASO, '04_reporte_individual_por_tema')
	Evidencia.registrar(CASO, "Reporte individual: ${detalle.take(500)}")
	if (!(detalle.contains('Progreso por tema') || detalle.contains('Sin progreso registrado'))) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el reporte individual no presenta la sección de progreso por tema (#444).")
	}
	KeywordUtil.markWarning("[$CASO] Observación #444: no existe un reporte consolidado de debilidades por tema (promedio < 70 % y 'debilidad común' con ≥ 30 % de estudiantes); el docente solo ve el progreso por tema de cada estudiante.")
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
