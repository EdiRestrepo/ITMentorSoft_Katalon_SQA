import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.util.KeywordUtil as KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import internal.GlobalVariable as GlobalVariable
import itmentorsoft.Api
import itmentorsoft.Autenticacion
import itmentorsoft.Configuracion
import itmentorsoft.Evidencia
import itmentorsoft.Pagina

/*
 * TC11 - Ingreso al módulo de realización de la evaluación inicial
 * Azure Test Plans #422 | HU 2.1.1 Desarrollo de evaluación (CA1)
 *
 * PASA si:  el estudiante entra al módulo, el selector ofrece los temas publicados (API /assessments/topics),
 *           al comenzar se muestra el formulario con la pregunta 1 de N, el tema (sección) de la evaluación y el
 *           panel con las N preguntas.
 * FALLA si: el módulo no carga, faltan temas publicados o la evaluación no muestra preguntas del tema elegido.
 * Observación: se inicia dos veces la evaluación del mismo tema; si salen las mismas preguntas en el mismo orden
 * se registra una advertencia sobre la aleatoriedad pedida en #422. La evaluación no se envía y se descarta.
 * Además verifica que una evaluación descartada no quede registrada como realizada (historial de la API).
 */
final String CASO = 'TC11'
final String P = 'Evaluacion_Inicial/Page_EvaluacionInicial/'
final String BTN_NUEVA = P + 'btn_IniciarEvaluacion'
final String SEL_TEMA = P + 'sel_Tema'
final String BTN_COMENZAR = P + 'btn_ComenzarEvaluacion'
final String LBL_ENUNCIADO = P + 'lbl_EnunciadoPregunta'
final String LBL_CONTADOR = P + 'lbl_ContadorPregunta'
final String LBL_TEMA = P + 'lbl_TemaPregunta'
final String LST_PANEL = P + 'lst_PanelPreguntas'
final String CNT_EN_CURSO = P + 'cnt_EvaluacionEnCurso'
final String BTN_DESCARTAR = P + 'btn_DescartarEvaluacion'

List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL', 'RUTA_EVALUACION_INICIAL'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS + [BTN_NUEVA, SEL_TEMA, BTN_COMENZAR, LBL_ENUNCIADO, LBL_CONTADOR, LBL_TEMA, LST_PANEL, CNT_EN_CURSO, BTN_DESCARTAR])
pendientes += Autenticacion.pendientesDeCuenta('ESTUDIANTE_EVAL')
Configuracion.detenerSiHayPendientes(CASO, pendientes)

def abrirModulo = {
	Pagina.abrir(GlobalVariable.RUTA_EVALUACION_INICIAL.toString())
	boolean visible = WebUI.waitForElementVisible(findTestObject(BTN_NUEVA), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)
	if (WebUI.waitForElementVisible(findTestObject(CNT_EN_CURSO), 2, FailureHandling.OPTIONAL)) {
		WebUI.click(findTestObject(BTN_DESCARTAR))
		WebUI.waitForElementNotPresent(findTestObject(CNT_EN_CURSO), Configuracion.timeoutCorto(), FailureHandling.OPTIONAL)
	}
	return visible
}

def totalEvaluaciones = { String id ->
	return (Api.json(Api.get("/assessments/summary?student_id=$id&page=0&page_size=1").body.toString())?.total_assessments ?: 0) as int
}

/** Inicia una evaluación del tema indicado y devuelve el tema mostrado, el contador y los textos del panel. */
def iniciar = { String tema ->
	WebUI.click(findTestObject(BTN_NUEVA))
	WebUI.waitForElementVisible(findTestObject(SEL_TEMA), Configuracion.timeoutLargo())
	WebUI.selectOptionByLabel(findTestObject(SEL_TEMA), java.util.regex.Pattern.quote(tema), true)
	WebUI.click(findTestObject(BTN_COMENZAR))
	if (!WebUI.waitForElementVisible(findTestObject(LBL_ENUNCIADO), Configuracion.timeoutEvaluacion(), FailureHandling.OPTIONAL)) {
		return null
	}
	return [tema: Pagina.texto(LBL_TEMA), contador: Pagina.texto(LBL_CONTADOR), panel: Pagina.textos(LST_PANEL), enunciado: Pagina.texto(LBL_ENUNCIADO)]
}

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que el estudiante ha ingresado correctamente a la aplicación')
	Autenticacion.iniciarSesion('ESTUDIANTE_EVAL', CASO)
	String idEstudiante = Api.valorDeSesion('auth_user_id')
	int totalAntes = totalEvaluaciones(idEstudiante)
	List<String> temasApi = (Api.json(Api.get('/assessments/topics').body.toString())?.topics ?: []) as List<String>

	WebUI.comment('Cuando ingresa al módulo de evaluación inicial')
	if (!abrirModulo()) {
		Evidencia.capturar(CASO, 'precondicion_modulo_no_disponible')
		KeywordUtil.markErrorAndStop("[$CASO] Precondición no cumplida: no cargó el módulo de evaluaciones.")
	}
	Evidencia.capturar(CASO, '01_modulo_evaluaciones')
	WebUI.click(findTestObject(BTN_NUEVA))
	WebUI.waitForElementVisible(findTestObject(SEL_TEMA), Configuracion.timeoutLargo())
	List<String> temasUi = WebUI.executeJavaScript("return [...document.querySelectorAll('select#topic option')].map(o => o.textContent.trim()).filter(t => t && !/^Elige/.test(t));", null) as List<String>
	Evidencia.capturar(CASO, '02_seleccion_de_tema')
	Evidencia.registrar(CASO, "Temas publicados (API): $temasApi | Temas en el selector: $temasUi")
	if (temasApi.isEmpty() || temasUi.toSet() != temasApi.toSet()) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el selector de temas ($temasUi) no corresponde a los temas publicados ($temasApi). Incumple HU 2.1.1.")
	}
	abrirModulo()
	String tema = temasApi ? temasApi[0] : ''

	WebUI.comment('Entonces el sistema muestra el formulario con las preguntas del tema, organizadas por secciones')
	Map primera = iniciar(tema)
	Evidencia.capturar(CASO, '03_formulario_de_preguntas')
	if (primera == null) {
		KeywordUtil.markErrorAndStop("[$CASO] Precondición no cumplida: el sistema no generó las preguntas del tema '$tema' en ${Configuracion.timeoutEvaluacion()} s.")
	}
	Evidencia.registrar(CASO, "Evaluación 1 -> tema mostrado '${primera.tema}', ${primera.contador}, panel con ${primera.panel.size()} preguntas. Pregunta 1: ${primera.enunciado}")
	def m = (primera.contador ?: '') =~ /Pregunta\s+(\d+)\s+de\s+(\d+)/
	if (!m.find() || m.group(1) != '1') {
		KeywordUtil.markFailed("[$CASO] DEFECTO: la evaluación no inicia en 'Pregunta 1 de N' (contador '${primera.contador}').")
	} else if (primera.panel.size() != (m.group(2) as int)) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el panel muestra ${primera.panel.size()} preguntas y el contador indica ${m.group(2)}.")
	}
	if (primera.tema != tema) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: la evaluación muestra el tema '${primera.tema}' y se eligió '$tema'.")
	}

	WebUI.comment('Y una segunda evaluación del mismo tema presenta una selección aleatoria de preguntas')
	abrirModulo()
	Map segunda = iniciar(tema)
	Evidencia.capturar(CASO, '04_segunda_evaluacion')
	Evidencia.registrar(CASO, "Evaluación 2 -> ${segunda?.contador}. Pregunta 1: ${segunda?.enunciado}")
	if (segunda && segunda.panel == primera.panel) {
		KeywordUtil.markWarning("[$CASO] Observación #422: dos evaluaciones del tema '$tema' presentaron las mismas preguntas en el mismo orden; no se evidencia selección aleatoria.")
	}

	WebUI.comment('Y una evaluación descartada sin enviar no queda registrada como realizada')
	abrirModulo()
	int totalDespues = totalEvaluaciones(idEstudiante)
	Evidencia.capturar(CASO, '05_historial_tras_descartar')
	Evidencia.registrar(CASO, "Evaluaciones en el historial (API): antes $totalAntes, después de iniciar y descartar dos evaluaciones $totalDespues")
	if (totalDespues > totalAntes) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: descartar una evaluación en curso la registra como realizada: el historial pasó de $totalAntes a $totalDespues evaluaciones " +
				"(nota 0.0, 'Sin clasificar'), lo que altera 'Evaluaciones realizadas' y el promedio. Incumple HU 2.1.1 / CR-006.")
	}
} finally {
	try {
		abrirModulo()
		Evidencia.registrar(CASO, 'Limpieza: evaluación en curso descartada')
	} catch (Exception e) {
		Evidencia.registrar(CASO, "Limpieza no realizada: ${e.message}")
	}
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
