import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.util.KeywordUtil as KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import itmentorsoft.Api
import itmentorsoft.Autenticacion
import itmentorsoft.Configuracion
import itmentorsoft.Evidencia
import itmentorsoft.Pagina

/*
 * TC17 - Representación gráfica de los reportes, consolidación por categoría y filtro en el dashboard docente
 * Azure Test Plans #502 (representación gráfica / vista sin datos), #418 (reporte dada una categoría),
 * #476 (consolidación de estudiantes por categoría y filtros) y #455 (CP-4.1.3 consulta y filtrado del progreso)
 *
 * PASA si:  (1) el dashboard y el módulo de reportes presentan las métricas con componentes visuales (tarjetas,
 *               barras y tortas) y los totales coinciden con la API (/reports/students);
 *           (2) la suma por nivel es igual al total de clasificados y los porcentajes suman 100 %;
 *           (3) al filtrar por cada nivel la tabla muestra solo estudiantes de ese nivel, en la cantidad indicada
 *               por su tarjeta, y al quitar el filtro vuelve a mostrar el 100 % de los estudiantes.
 * FALLA si: los totales no coinciden, el filtro muestra estudiantes de otro nivel o no restablece el total.
 * Observaciones: #476 pide filtro por rango de fechas y #455/#502 el mensaje "sin datos disponibles"; con los datos
 * actuales no hay vista vacía y la interfaz no ofrece filtro por fechas (se registran como advertencias).
 */
final String CASO = 'TC17'
final String R = 'Reportes/Page_Reportes/'
final String D = 'Reportes/Page_DashboardDocente/'
final List<String> NIVELES = ['Principiante', 'Básico', 'Intermedio', 'Avanzado']

List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS + [R + 'cnt_Reportes', R + 'lbl_TotalClasificados', R + 'btn_Nivel', R + 'lst_ValoresNivel',
	R + 'lst_FilasEstudiantes', R + 'lbl_ConteoEstudiantes', R + 'lst_GraficosTorta', D + 'cnt_Dashboard', D + 'lbl_Metrica', D + 'lst_BarrasCategoria'])
pendientes += Autenticacion.pendientesDeCuenta('DOCENTE')
Configuracion.detenerSiHayPendientes(CASO, pendientes)

def numero = { String t -> ((t ?: '') =~ /\d+/).with { it.find() ? (it.group() as int) : -1 } }

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que existen estudiantes en el sistema y el docente accede al dashboard')
	Autenticacion.iniciarSesion('DOCENTE', CASO)
	def api = Api.json(Api.get('/reports/students?page=0&page_size=100').body.toString())
	List estudiantes = (api?.result?.students ?: []) as List
	Map<String, Integer> porNivelApi = NIVELES.collectEntries { n -> [n, estudiantes.count { it.knowledge_classification?.toString()?.equalsIgnoreCase(n) } as int] }
	Evidencia.registrar(CASO, "API: ${estudiantes.size()} estudiantes, por nivel $porNivelApi")

	Pagina.abrir('/teacher/dashboard', 3)
	Pagina.visible(D + 'cnt_Dashboard', null, Configuracion.timeoutLargo())
	int activos = numero(Pagina.texto(D + 'lbl_Metrica', [nombre: 'Estudiantes activos']))
	List<String> barras = WebUI.executeJavaScript("return [...document.querySelectorAll('app-teacher-dashboard .cc__value')].map(e => e.textContent.trim());", null) as List<String>
	Evidencia.capturar(CASO, '01_dashboard_docente')
	Evidencia.registrar(CASO, "Dashboard: Estudiantes activos=$activos, barras por categoría=$barras")
	if (activos != estudiantes.size()) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el dashboard muestra $activos estudiantes activos y la API reporta ${estudiantes.size()} (#455).")
	}
	if (barras.isEmpty()) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el dashboard no presenta el gráfico de estudiantes por categoría (#455/#502).")
	} else if (barras.sum { it as int } != estudiantes.size()) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: las barras por categoría suman ${barras.sum { it as int }} y hay ${estudiantes.size()} estudiantes (#476).")
	}

	WebUI.comment('Cuando consulta el reporte general consolidado por categoría')
	Pagina.abrir('/teacher/reports', 3)
	Pagina.visible(R + 'cnt_Reportes', null, Configuracion.timeoutLargo())
	WebUI.waitForElementVisible(findTestObject(R + 'lst_FilasEstudiantes'), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)
	int total = numero(Pagina.texto(R + 'lbl_TotalClasificados'))
	List<String> tarjetas = Pagina.textos(R + 'lst_ValoresNivel')
	Map<String, Integer> porNivelUi = [:]
	int sumaPct = 0
	tarjetas.each { t ->
		def m = t =~ /^(\S+)\s+(\d+)\s+(\d+)%/
		if (m.find()) {
			porNivelUi[m.group(1)] = m.group(2) as int
			sumaPct += m.group(3) as int
		}
	}
	int graficos = Pagina.contar(R + 'lst_GraficosTorta')
	Evidencia.capturar(CASO, '02_reporte_por_nivel')
	Evidencia.registrar(CASO, "Reportes: total clasificados=$total, por nivel=$porNivelUi, suma de porcentajes=$sumaPct%, gráficos de torta=$graficos")

	WebUI.comment('Entonces agrupa a los estudiantes por categoría y muestra la distribución con gráficos')
	if (total != estudiantes.size() || porNivelUi.values().sum() != total) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el total de clasificados ($total) no coincide con la suma por nivel (${porNivelUi.values().sum()}) o con la API (${estudiantes.size()}). Incumple #476.")
	}
	if (porNivelUi != porNivelApi) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: la distribución por nivel de la interfaz $porNivelUi no coincide con la API $porNivelApi (#418/#476).")
	}
	if (Math.abs(sumaPct - 100) > NIVELES.size()) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: los porcentajes por nivel suman $sumaPct % (#476).")
	}
	if (graficos == 0) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el módulo de reportes no presenta gráficos (#502).")
	}

	WebUI.comment('Y cuando aplica un filtro por categoría muestra únicamente los estudiantes de esa categoría')
	NIVELES.each { nivel ->
		WebUI.click(findTestObject(R + 'btn_Nivel', [nivel: nivel]))
		WebUI.delay(2)
		List<String> filas = Pagina.textos(R + 'lst_FilasEstudiantes')
		List<String> otros = filas.findAll { !it.contains(nivel) }
		Evidencia.registrar(CASO, "Filtro '$nivel': ${filas.size()} filas (tarjeta: ${porNivelUi[nivel]}), conteo '${Pagina.texto(R + 'lbl_ConteoEstudiantes')}'")
		if (nivel == 'Básico') {
			Evidencia.capturar(CASO, '03_filtro_basico')
		}
		if (otros) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: con el filtro '$nivel' aparecen estudiantes de otro nivel: $otros (#418/#455).")
		}
		if (filas.size() != (porNivelUi[nivel] ?: 0)) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: con el filtro '$nivel' la tabla muestra ${filas.size()} estudiantes y la tarjeta indica ${porNivelUi[nivel]}.")
		}
		WebUI.click(findTestObject(R + 'btn_Nivel', [nivel: nivel]))
		WebUI.delay(1)
	}

	WebUI.comment('Y cuando elimina el filtro muestra nuevamente el 100 % de los datos')
	WebUI.executeJavaScript("var b=[...document.querySelectorAll('app-teacher-reports button, app-teacher-reports a')].find(e=>/Ver todos/.test(e.textContent)); if(b) b.click();", null)
	WebUI.delay(2)
	int sinFiltro = Pagina.contar(R + 'lst_FilasEstudiantes')
	Evidencia.capturar(CASO, '04_sin_filtro')
	Evidencia.registrar(CASO, "Sin filtro: ${sinFiltro} filas; conteo '${Pagina.texto(R + 'lbl_ConteoEstudiantes')}'")
	if (sinFiltro != Math.min(total, 10) && sinFiltro != total) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: al quitar el filtro la tabla muestra $sinFiltro estudiantes y hay $total (#455).")
	}

	boolean filtroFechas = WebUI.executeJavaScript("return !!document.querySelector('app-teacher-reports input[type=date], app-teacher-dashboard input[type=date]');", null)
	if (!filtroFechas) {
		KeywordUtil.markWarning("[$CASO] Observación #476: el dashboard docente no ofrece filtro por rango de fechas para la consolidación por categoría.")
	}
	KeywordUtil.markWarning("[$CASO] Observación #455/#502: no se verificó la vista 'sin datos disponibles' porque el entorno tiene estudiantes clasificados en todos los niveles.")
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
