import static com.kms.katalon.core.testdata.TestDataFactory.findTestData
import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.testdata.TestData as TestData
import com.kms.katalon.core.util.KeywordUtil as KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import internal.GlobalVariable as GlobalVariable
import itmentorsoft.Autenticacion
import itmentorsoft.Configuracion
import itmentorsoft.Evidencia

/*
 * TC03 - Evaluación secuencial y persistencia de cada respuesta confirmada
 * Azure Test Plans #434 | HU 2.1.1 Desarrollo de evaluación (#212) CA2 y CA3 | CR-006 (#330) autosave y reanudación
 * Riesgo RH03: pérdida o alteración de respuestas de la evaluación, que es la entrada de la
 * categorización (HU 2.2.1) y de la calificación con LLM (SQA R01).
 *
 * PASA si:  las preguntas se presentan una a una ("Pregunta 1 de N" -> "Pregunta 2 de N"), "Anterior" está
 *           deshabilitado en la primera pregunta, y al salir y volver al módulo la evaluación en curso se
 *           conserva: "Retomar" continúa en la pregunta 2 y la respuesta 1 sigue guardada.
 * FALLA si: alguna de esas condiciones no se cumple.
 * ERROR si: falta configuración, falla el login de precondición o el sistema no genera la evaluación.
 * La evaluación NO se envía (no se califica ni altera el historial); al final se descarta para dejar la cuenta limpia.
 */
final String CASO = 'TC03'
final String BTN_NUEVA = 'Evaluacion_Inicial/Page_EvaluacionInicial/btn_IniciarEvaluacion'
final String SEL_TEMA = 'Evaluacion_Inicial/Page_EvaluacionInicial/sel_Tema'
final String BTN_COMENZAR = 'Evaluacion_Inicial/Page_EvaluacionInicial/btn_ComenzarEvaluacion'
final String LBL_ENUNCIADO = 'Evaluacion_Inicial/Page_EvaluacionInicial/lbl_EnunciadoPregunta'
final String LBL_CONTADOR = 'Evaluacion_Inicial/Page_EvaluacionInicial/lbl_ContadorPregunta'
final String TXT_RESPUESTA = 'Evaluacion_Inicial/Page_EvaluacionInicial/txt_Respuesta'
final String BTN_CONFIRMAR = 'Evaluacion_Inicial/Page_EvaluacionInicial/btn_ConfirmarRespuesta'
final String BTN_ANTERIOR = 'Evaluacion_Inicial/Page_EvaluacionInicial/btn_Anterior'
final String BTN_NAVEGACION_NO_PERMITIDA = 'Evaluacion_Inicial/Page_EvaluacionInicial/btn_NavegacionNoPermitida'
final String CNT_EN_CURSO = 'Evaluacion_Inicial/Page_EvaluacionInicial/cnt_EvaluacionEnCurso'
final String BTN_RETOMAR = 'Evaluacion_Inicial/Page_EvaluacionInicial/btn_RetomarEvaluacion'
final String BTN_DESCARTAR = 'Evaluacion_Inicial/Page_EvaluacionInicial/btn_DescartarEvaluacion'

TestData datos = findTestData('Data Files/TD_TC03_Respuestas')

// ---- 0. Configuración requerida
List<String> pendientes = []
pendientes += Configuracion.variablesPendientes(['BASE_URL', 'RUTA_EVALUACION_INICIAL', 'RUTA_RESULTADOS_PROPIOS'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS + [BTN_NUEVA, SEL_TEMA, BTN_COMENZAR, LBL_ENUNCIADO, LBL_CONTADOR, TXT_RESPUESTA,
	BTN_CONFIRMAR, BTN_ANTERIOR, BTN_NAVEGACION_NO_PERMITIDA, CNT_EN_CURSO, BTN_RETOMAR, BTN_DESCARTAR])
pendientes += Autenticacion.pendientesDeCuenta('ESTUDIANTE_EVAL')
pendientes += Configuracion.datosPendientes(datos, ['respuesta'])
Configuracion.detenerSiHayPendientes(CASO, pendientes)

String respuesta1 = datos.getValue('respuesta', 1)

/** Abre el historial "Mis evaluaciones" y espera a que cargue. */
def abrirModulo = {
	WebUI.navigateToUrl(Configuracion.url(GlobalVariable.RUTA_EVALUACION_INICIAL))
	WebUI.waitForPageLoad(Configuracion.timeoutLargo())
	return WebUI.waitForElementVisible(findTestObject(BTN_NUEVA), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)
}

/** Descarta una evaluación en curso, si existe. Devuelve true si había una. */
def descartarEnCurso = {
	if (WebUI.waitForElementVisible(findTestObject(CNT_EN_CURSO), Configuracion.timeoutCorto(), FailureHandling.OPTIONAL)) {
		WebUI.click(findTestObject(BTN_DESCARTAR))
		WebUI.waitForElementNotPresent(findTestObject(CNT_EN_CURSO), Configuracion.timeoutCorto(), FailureHandling.OPTIONAL)
		return true
	}
	return false
}

def enunciado = {
	return WebUI.getText(findTestObject(LBL_ENUNCIADO), FailureHandling.OPTIONAL)?.trim()
}

/** "Pregunta X de N" -> [X, N]; null si no se puede leer. */
def contador = {
	def coincidencia = (WebUI.getText(findTestObject(LBL_CONTADOR), FailureHandling.OPTIONAL) ?: '') =~ /(\d+)\s+de\s+(\d+)/
	return coincidencia.find() ? [coincidencia.group(1) as int, coincidencia.group(2) as int] : null
}

/** Espera a que el enunciado cambie respecto al anterior (la SPA puede tardar en presentar la siguiente pregunta). */
def esperarCambioDeEnunciado = { String anterior ->
	long limite = System.currentTimeMillis() + Configuracion.timeoutLargo() * 1000L
	while (System.currentTimeMillis() < limite) {
		String actual = enunciado()
		if (actual && actual != anterior) {
			return actual
		}
		WebUI.delay(1)
	}
	return null
}

def anteriorHabilitado = {
	return WebUI.getAttribute(findTestObject(BTN_ANTERIOR), 'disabled', FailureHandling.OPTIONAL) == null
}

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que el estudiante ha iniciado sesión y abre el módulo de evaluación')
	Autenticacion.iniciarSesion('ESTUDIANTE_EVAL', CASO)
	if (!abrirModulo()) {
		Evidencia.capturar(CASO, 'precondicion_modulo_no_disponible')
		KeywordUtil.markErrorAndStop("[$CASO] Precondición no cumplida: no cargó el módulo 'Mis evaluaciones' (${GlobalVariable.RUTA_EVALUACION_INICIAL}).")
	}
	if (descartarEnCurso()) {
		Evidencia.registrar(CASO, 'Precondición: se descartó una evaluación en curso de una ejecución anterior')
	}

	WebUI.comment('Cuando inicia una evaluación nueva')
	WebUI.click(findTestObject(BTN_NUEVA))
	WebUI.waitForElementVisible(findTestObject(SEL_TEMA), Configuracion.timeoutLargo())
	Evidencia.registrar(CASO, "Tema: ${WebUI.getAttribute(findTestObject(SEL_TEMA), 'value', FailureHandling.OPTIONAL)}")
	WebUI.click(findTestObject(BTN_COMENZAR))
	if (!WebUI.waitForElementVisible(findTestObject(LBL_ENUNCIADO), Configuracion.timeoutEvaluacion(), FailureHandling.OPTIONAL)) {
		Evidencia.capturar(CASO, 'precondicion_evaluacion_no_generada')
		KeywordUtil.markErrorAndStop("[$CASO] Precondición no cumplida: el sistema no generó las preguntas en ${Configuracion.timeoutEvaluacion()} s.")
	}
	String pregunta1 = enunciado()
	List<Integer> posicion = contador()
	Evidencia.capturar(CASO, '01_pregunta_1')
	Evidencia.registrar(CASO, "Pregunta 1 ($posicion): $pregunta1")
	if (posicion == null || posicion[1] < 2) {
		KeywordUtil.markErrorAndStop("[$CASO] Precondición no cumplida: la evaluación debe tener al menos 2 preguntas (contador leído: $posicion).")
	}
	int total = posicion[1]

	WebUI.comment('Entonces se presenta solo la primera pregunta y no se puede retroceder (HU 2.1.1 CA2)')
	if (posicion[0] != 1) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: la evaluación no empieza en la pregunta 1 (contador: $posicion). Incumple HU 2.1.1 CA2.")
	}
	if (anteriorHabilitado()) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el botón 'Anterior' está habilitado en la primera pregunta. Incumple HU 2.1.1 CA2.")
	}
	if (WebUI.waitForElementPresent(findTestObject(BTN_NAVEGACION_NO_PERMITIDA), 1, FailureHandling.OPTIONAL)) {
		KeywordUtil.markWarning("[$CASO] Observación HU 2.1.1 CA2: la interfaz ofrece 'Anterior' y el panel 'Tus preguntas' para saltar entre preguntas. " +
				"La redacción de Azure dice 'sin permitir saltar entre preguntas ni retroceder'; confirmar con producto cuál es el comportamiento vigente.")
	}

	WebUI.comment('Cuando responde la pregunta 1 y la confirma con "Siguiente" (HU 2.1.1 CA3)')
	WebUI.setText(findTestObject(TXT_RESPUESTA), respuesta1)
	WebUI.click(findTestObject(BTN_CONFIRMAR))

	WebUI.comment('Entonces el sistema presenta la pregunta 2 (HU 2.1.1 CA2)')
	String pregunta2 = esperarCambioDeEnunciado(pregunta1)
	Evidencia.capturar(CASO, '02_pregunta_2')
	if (!pregunta2 || contador() != [2, total]) {
		KeywordUtil.markFailedAndStop("[$CASO] DEFECTO: tras confirmar la respuesta 1 no se presentó la pregunta 2 de $total (contador: ${contador()}). Incumple HU 2.1.1 CA2/CA3.")
	}
	Evidencia.registrar(CASO, "Pregunta 2: $pregunta2")

	WebUI.comment('Y cuando sale del módulo y vuelve a entrar, la evaluación en curso se conserva (CR-006)')
	WebUI.navigateToUrl(Configuracion.url(GlobalVariable.RUTA_RESULTADOS_PROPIOS))
	WebUI.waitForPageLoad(Configuracion.timeoutLargo())
	abrirModulo()
	boolean enCurso = WebUI.waitForElementVisible(findTestObject(CNT_EN_CURSO), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)
	Evidencia.capturar(CASO, '03_evaluacion_en_curso')
	if (!enCurso) {
		KeywordUtil.markFailedAndStop("[$CASO] DEFECTO: al volver al módulo no aparece la evaluación en curso; la respuesta confirmada se perdió. Incumple HU 2.1.1 CA3 y CR-006.")
	}
	String avance = WebUI.getText(findTestObject(CNT_EN_CURSO), FailureHandling.OPTIONAL)?.replaceAll('\\s+', ' ')
	Evidencia.registrar(CASO, "Evaluación en curso: $avance")
	if (!avance?.contains("1 de $total respondidas")) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el aviso de evaluación en curso no refleja 1 de $total preguntas respondidas ('$avance'). Incumple CR-006.")
	}

	WebUI.comment('Y al retomar continúa en la pregunta 2, con la respuesta 1 guardada (HU 2.1.1 CA3, CR-006)')
	WebUI.click(findTestObject(BTN_RETOMAR))
	WebUI.waitForElementVisible(findTestObject(LBL_ENUNCIADO), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)
	String preguntaAlRetomar = enunciado()
	Evidencia.capturar(CASO, '04_reingreso')
	Evidencia.registrar(CASO, "Pregunta al retomar: $preguntaAlRetomar")
	if (preguntaAlRetomar != pregunta2) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: al retomar no se continúa en la pregunta pendiente (esperada: '$pregunta2', obtenida: '$preguntaAlRetomar'). Incumple CR-006.")
	}
	WebUI.click(findTestObject(BTN_ANTERIOR))
	if (esperarCambioDeEnunciado(preguntaAlRetomar) != pregunta1) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: no fue posible consultar la pregunta 1 de la evaluación retomada.")
	} else {
		String respuestaGuardada = WebUI.getAttribute(findTestObject(TXT_RESPUESTA), 'value', FailureHandling.OPTIONAL)
		Evidencia.capturar(CASO, '05_respuesta_1_conservada')
		if (respuestaGuardada?.trim() != respuesta1.trim()) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: la respuesta 1 no se conservó (esperada: '$respuesta1', obtenida: '$respuestaGuardada'). Incumple HU 2.1.1 CA3 y CR-006.")
		}
	}
	Evidencia.registrar(CASO, 'Verificaciones de flujo secuencial y persistencia finalizadas (ver estado del caso en el reporte)')
} finally {
	// Limpieza: la evaluación no se envía; se descarta para que la cuenta quede como estaba.
	try {
		if (abrirModulo() && descartarEnCurso()) {
			Evidencia.registrar(CASO, 'Limpieza: evaluación en curso descartada')
		}
	} catch (Exception e) {
		Evidencia.registrar(CASO, "Limpieza no realizada: ${e.message}")
	}
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
