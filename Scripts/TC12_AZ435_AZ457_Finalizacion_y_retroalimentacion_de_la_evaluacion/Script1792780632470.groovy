import static com.kms.katalon.core.testdata.TestDataFactory.findTestData
import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.testdata.TestData as TestData
import com.kms.katalon.core.util.KeywordUtil as KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import internal.GlobalVariable as GlobalVariable
import itmentorsoft.Api
import itmentorsoft.Autenticacion
import itmentorsoft.Configuracion
import itmentorsoft.Evidencia
import itmentorsoft.Pagina

/*
 * TC12 - Finalización de la evaluación inicial y retroalimentación de resultados
 * Azure Test Plans #435 (Garantizar finalización de evaluación inicial) y #457 (CP-4.2.1 Retroalimentación)
 * HU 2.1.1 Desarrollo de evaluación (CA4) | HU 4.2.1 Retroalimentación
 *
 * PASA si:  (1) al responder todas las preguntas y enviar, el sistema confirma el envío y guarda la evaluación
 *               (el historial de la API aumenta en una evaluación);
 *           (2) tras la calificación, el resultado muestra la nota, el detalle pregunta a pregunta de todas las
 *               preguntas con su puntaje y la retroalimentación (fortalezas / aspectos a reforzar).
 * FALLA si: el envío no se confirma, la evaluación no queda registrada o el resultado no muestra el detalle completo.
 * ERROR si: la calificación no termina en TIMEOUT_CALIFICACION_S (Data Files/TD_TC12_Evaluacion).
 * Efecto: deja una evaluación calificada en el historial del estudiante de pruebas (es el flujo que se valida).
 */
final String CASO = 'TC12'
final String P = 'Evaluacion_Inicial/Page_EvaluacionInicial/'
final String R = 'Evaluacion_Inicial/Page_Resultado/'
final String BTN_NUEVA = P + 'btn_IniciarEvaluacion'
final String SEL_TEMA = P + 'sel_Tema'
final String BTN_COMENZAR = P + 'btn_ComenzarEvaluacion'
final String LBL_ENUNCIADO = P + 'lbl_EnunciadoPregunta'
final String LBL_CONTADOR = P + 'lbl_ContadorPregunta'
final String TXT_RESPUESTA = P + 'txt_Respuesta'
final String BTN_SIGUIENTE = P + 'btn_ConfirmarRespuesta'
final String CNT_EN_CURSO = P + 'cnt_EvaluacionEnCurso'
final String BTN_DESCARTAR = P + 'btn_DescartarEvaluacion'
final String LST_HISTORIAL = P + 'lst_FilasHistorial'

TestData datos = findTestData('Data Files/TD_TC12_Evaluacion')
List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL', 'RUTA_EVALUACION_INICIAL'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS + [BTN_NUEVA, SEL_TEMA, BTN_COMENZAR, LBL_ENUNCIADO, LBL_CONTADOR, TXT_RESPUESTA, BTN_SIGUIENTE,
	LST_HISTORIAL, R + 'cnt_Resultado', R + 'lbl_Nota', R + 'lst_DetallePreguntas'])
pendientes += Autenticacion.pendientesDeCuenta('ESTUDIANTE_EVAL')
pendientes += Configuracion.datosPendientes(datos, ['respuesta'])
if (Configuracion.esPendiente(datos.getValue('tema', 1)) || !(datos.getValue('timeout_calificacion_s', 1) ==~ /\d+/)) {
	pendientes << 'Data Files > TD_TC12_Evaluacion fila 1: columnas tema y timeout_calificacion_s'
}
Configuracion.detenerSiHayPendientes(CASO, pendientes)

String tema = datos.getValue('tema', 1)
int timeoutCalificacion = datos.getValue('timeout_calificacion_s', 1) as int
List<String> respuestas = (1..datos.getRowNumbers()).collect { datos.getValue('respuesta', it) }.findAll { it }

def resumen = { String id ->
	return Api.json(Api.get("/assessments/summary?student_id=$id&page=0&page_size=50").body.toString())
}

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que el estudiante inicia la evaluación inicial')
	Autenticacion.iniciarSesion('ESTUDIANTE_EVAL', CASO)
	String id = Api.valorDeSesion('auth_user_id')
	int totalAntes = (resumen(id)?.total_assessments ?: 0) as int
	Pagina.abrir(GlobalVariable.RUTA_EVALUACION_INICIAL.toString())
	WebUI.waitForElementVisible(findTestObject(BTN_NUEVA), Configuracion.timeoutLargo())
	if (WebUI.waitForElementVisible(findTestObject(CNT_EN_CURSO), 2, FailureHandling.OPTIONAL)) {
		WebUI.click(findTestObject(BTN_DESCARTAR))
		WebUI.delay(2)
	}
	WebUI.click(findTestObject(BTN_NUEVA))
	WebUI.waitForElementVisible(findTestObject(SEL_TEMA), Configuracion.timeoutLargo())
	WebUI.selectOptionByLabel(findTestObject(SEL_TEMA), java.util.regex.Pattern.quote(tema), true)
	WebUI.click(findTestObject(BTN_COMENZAR))
	if (!WebUI.waitForElementVisible(findTestObject(LBL_ENUNCIADO), Configuracion.timeoutEvaluacion(), FailureHandling.OPTIONAL)) {
		KeywordUtil.markErrorAndStop("[$CASO] Precondición no cumplida: el sistema no generó las preguntas del tema '$tema'.")
	}
	def m = (Pagina.texto(LBL_CONTADOR) ?: '') =~ /(\d+)\s+de\s+(\d+)/
	int total = m.find() ? (m.group(2) as int) : 0
	Evidencia.registrar(CASO, "Evaluación del tema '$tema' con $total preguntas (historial antes: $totalAntes)")

	WebUI.comment('Cuando responde todas las preguntas y presiona finalizar la evaluación')
	(1..total).each { n ->
		String enunciado = Pagina.texto(LBL_ENUNCIADO)
		WebUI.setText(findTestObject(TXT_RESPUESTA), respuestas[(n - 1) % respuestas.size()])
		Evidencia.registrar(CASO, "Pregunta $n de $total respondida: $enunciado")
		if (n == total) {
			Evidencia.capturar(CASO, '01_ultima_pregunta_respondida')
		}
		WebUI.click(findTestObject(BTN_SIGUIENTE))
		if (n < total) {
			long limite = System.currentTimeMillis() + Configuracion.timeoutLargo() * 1000L
			while (Pagina.texto(LBL_ENUNCIADO, null, 1) == enunciado && System.currentTimeMillis() < limite) {
				WebUI.delay(1)
			}
		}
	}
	String confirmacion = Pagina.esperarToast(Configuracion.timeoutLargo())
	WebUI.delay(2)
	Evidencia.capturar(CASO, '02_evaluacion_enviada')
	Evidencia.registrar(CASO, "Confirmación de envío: '$confirmacion' (ruta ${Pagina.rutaActual()})")
	if (!confirmacion || !(confirmacion =~ /(?i)enviada|calificando/)) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: al finalizar la evaluación no se confirmó el envío (notificación '$confirmacion'). Incumple #435.")
	}

	WebUI.comment('Entonces el sistema guarda las respuestas y califica la evaluación')
	Map ultima = null
	long limite = System.currentTimeMillis() + timeoutCalificacion * 1000L
	while (System.currentTimeMillis() < limite) {
		def r = resumen(id)
		if (((r?.total_assessments ?: 0) as int) > totalAntes) {
			ultima = (r.assessments as List<Map>).max { it.date_taken }
			if (ultima?.classification || ultima?.feedback) {
				break
			}
		}
		WebUI.delay(15)
	}
	Evidencia.registrar(CASO, "Evaluación registrada en la API: $ultima")
	if (ultima == null) {
		KeywordUtil.markFailedAndStop("[$CASO] DEFECTO: después de enviar, la evaluación no aparece en el historial del estudiante en ${timeoutCalificacion} s. Incumple #435 (guardar respuestas).")
	}
	if (!(ultima.classification || ultima.feedback)) {
		KeywordUtil.markErrorAndStop("[$CASO] La evaluación quedó registrada pero la calificación no terminó en ${timeoutCalificacion} s (sin clasificación ni feedback). Reintentar más tarde.")
	}

	WebUI.comment('Y cuando el estudiante consulta los resultados, ve la retroalimentación pregunta a pregunta')
	Pagina.abrir(GlobalVariable.RUTA_EVALUACION_INICIAL.toString(), 3)
	WebUI.executeJavaScript("var s=document.querySelector('.hst__sort select'); if(s){s.value='recent'; s.dispatchEvent(new Event('change'));}", null)
	WebUI.delay(1)
	Pagina.elementos(LST_HISTORIAL, null, Configuracion.timeoutLargo())[0].click()
	if (!Pagina.visible(R + 'cnt_Resultado', null, Configuracion.timeoutLargo())) {
		KeywordUtil.markFailedAndStop("[$CASO] DEFECTO: no se pudo abrir el resultado de la evaluación recién calificada.")
	}
	WebUI.delay(2)
	WebUI.executeJavaScript("var b=[...document.querySelectorAll('app-assessment-result button')].find(e=>/Desplegar todas/.test(e.textContent)); if(b) b.click();", null)
	WebUI.delay(1)
	String nota = Pagina.texto(R + 'lbl_Nota')
	String preguntas = Pagina.texto(R + 'lbl_EstadisticaResultado', [nombre: 'Preguntas'])
	List<String> detalle = Pagina.textos(R + 'lst_DetallePreguntas')
	String retro = Pagina.texto(R + 'cnt_Retroalimentacion', null, 2)
	Evidencia.capturar(CASO, '03_resultado_retroalimentacion')
	Evidencia.registrar(CASO, "Resultado: nota $nota, preguntas $preguntas, detalle ${detalle.size()} ítems, retroalimentación: ${retro?.take(300)}")
	detalle.eachWithIndex { d, i -> Evidencia.registrar(CASO, "Detalle ${i + 1}: ${d.take(400)}") }
	if (!nota) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el resultado no muestra la nota de la evaluación.")
	}
	if (preguntas != total.toString() || detalle.size() != total) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el resultado no muestra el detalle de las $total preguntas (estadística '$preguntas', ítems ${detalle.size()}). Incumple #457.")
	}
	if (detalle.any { it.length() < 40 }) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: hay preguntas del resultado sin retroalimentación/explicación. Incumple #457.")
	}
	if (!retro) {
		KeywordUtil.markWarning("[$CASO] Observación #457: el resultado no muestra un bloque de retroalimentación general (conceptos que domina / a reforzar).")
	}
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
