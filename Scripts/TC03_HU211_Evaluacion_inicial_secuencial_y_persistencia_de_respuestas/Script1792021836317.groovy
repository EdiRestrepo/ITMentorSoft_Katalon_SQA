import static com.kms.katalon.core.testdata.TestDataFactory.findTestData
import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import org.openqa.selenium.WebElement

import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.testdata.TestData as TestData
import com.kms.katalon.core.util.KeywordUtil as KeywordUtil
import com.kms.katalon.core.webui.common.WebUiCommonHelper as WebUiCommonHelper
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import internal.GlobalVariable as GlobalVariable
import itmentorsoft.Autenticacion
import itmentorsoft.Configuracion
import itmentorsoft.Evidencia

/*
 * TC03 - Evaluación inicial secuencial y persistencia de cada respuesta confirmada
 * Azure Test Plans #434 | HU 2.1.1 Desarrollo de evaluación (#212) CA2 y CA3 | CR-006 (#330) autosave y reanudación
 * Riesgo RH03: pérdida o alteración de respuestas de la evaluación inicial, que es la entrada de la
 * categorización (HU 2.2.1) y de la calificación con LLM (SQA R01).
 *
 * PASA si:  tras confirmar la pregunta 1 se presenta otra pregunta; no hay controles para saltar/retroceder;
 *           "Atrás" del navegador no permite volver a editar la pregunta 1; al reingresar continúa en la pregunta 2.
 * FALLA si: alguna de esas condiciones no se cumple.
 * ERROR si: falta configuración o falla el login de precondición.
 * ATENCIÓN: modifica el estado de USUARIO_ESTUDIANTE_EVAL (usar una cuenta sin evaluación iniciada en cada ejecución).
 */
final String CASO = 'TC03'
final String BTN_INICIAR = 'Evaluacion_Inicial/Page_EvaluacionInicial/btn_IniciarEvaluacion'
final String LBL_ENUNCIADO = 'Evaluacion_Inicial/Page_EvaluacionInicial/lbl_EnunciadoPregunta'
final String TXT_RESPUESTA = 'Evaluacion_Inicial/Page_EvaluacionInicial/txt_Respuesta'
final String BTN_CONFIRMAR = 'Evaluacion_Inicial/Page_EvaluacionInicial/btn_ConfirmarRespuesta'
final String BTN_NAVEGACION_NO_PERMITIDA = 'Evaluacion_Inicial/Page_EvaluacionInicial/btn_NavegacionNoPermitida'

TestData datos = findTestData('Data Files/TD_TC03_Respuestas')

// ---- 0. Configuración requerida
List<String> pendientes = []
pendientes += Configuracion.variablesPendientes(['BASE_URL', 'RUTA_EVALUACION_INICIAL'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS + [BTN_INICIAR, LBL_ENUNCIADO, TXT_RESPUESTA, BTN_CONFIRMAR, BTN_NAVEGACION_NO_PERMITIDA])
pendientes += Autenticacion.pendientesDeCuenta('ESTUDIANTE_EVAL')
pendientes += Configuracion.datosPendientes(datos, ['respuesta'])
Configuracion.detenerSiHayPendientes(CASO, pendientes)

String respuesta1 = datos.getValue('respuesta', 1)

/** Entra al módulo y, si existe el botón de iniciar/continuar, lo usa. Devuelve el enunciado visible. */
def abrirEvaluacion = {
	WebUI.navigateToUrl(Configuracion.url(GlobalVariable.RUTA_EVALUACION_INICIAL))
	WebUI.waitForPageLoad(Configuracion.timeoutLargo())
	if (WebUI.waitForElementClickable(findTestObject(BTN_INICIAR), Configuracion.timeoutCorto(), FailureHandling.OPTIONAL)) {
		WebUI.click(findTestObject(BTN_INICIAR))
	}
	if (!WebUI.waitForElementVisible(findTestObject(LBL_ENUNCIADO), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)) {
		return null
	}
	return WebUI.getText(findTestObject(LBL_ENUNCIADO)).trim()
}

/** Espera a que el enunciado cambie respecto al anterior (la SPA puede tardar en presentar la siguiente pregunta). */
def esperarCambioDeEnunciado = { String anterior ->
	long limite = System.currentTimeMillis() + Configuracion.timeoutLargo() * 1000L
	while (System.currentTimeMillis() < limite) {
		if (WebUI.waitForElementVisible(findTestObject(LBL_ENUNCIADO), 1, FailureHandling.OPTIONAL)) {
			String actual = WebUI.getText(findTestObject(LBL_ENUNCIADO), FailureHandling.OPTIONAL)?.trim()
			if (actual && actual != anterior) {
				return actual
			}
		}
		WebUI.delay(1)
	}
	return null
}

def verificarSinNavegacionNoPermitida = { String momento ->
	if (!WebUI.verifyElementNotPresent(findTestObject(BTN_NAVEGACION_NO_PERMITIDA), Configuracion.timeoutCorto(), FailureHandling.OPTIONAL)) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: $momento existe un control para retroceder u omitir preguntas. Incumple HU 2.1.1 CA2.")
	}
}

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que el estudiante ha iniciado sesión e inicia la evaluación inicial')
	Autenticacion.iniciarSesion('ESTUDIANTE_EVAL', CASO)
	String pregunta1 = abrirEvaluacion()
	Evidencia.capturar(CASO, '01_pregunta_1')
	if (!pregunta1) {
		KeywordUtil.markErrorAndStop("[$CASO] Precondición no cumplida: no se presentó ninguna pregunta al iniciar la evaluación. Verificar que la cuenta no tenga la evaluación terminada y los localizadores del módulo.")
	}
	Evidencia.registrar(CASO, "Pregunta 1: $pregunta1")
	verificarSinNavegacionNoPermitida('en la pregunta 1')

	WebUI.comment('Cuando responde y confirma la pregunta 1 (HU 2.1.1 CA3)')
	WebUI.setText(findTestObject(TXT_RESPUESTA), respuesta1)
	WebUI.click(findTestObject(BTN_CONFIRMAR))

	WebUI.comment('Entonces el sistema presenta la siguiente pregunta (HU 2.1.1 CA2)')
	String pregunta2 = esperarCambioDeEnunciado(pregunta1)
	Evidencia.capturar(CASO, '02_pregunta_2')
	if (!pregunta2) {
		KeywordUtil.markFailedAndStop("[$CASO] DEFECTO: tras confirmar la respuesta 1 no se presentó una nueva pregunta en ${Configuracion.timeoutLargo()} s. Incumple HU 2.1.1 CA2/CA3.")
	}
	Evidencia.registrar(CASO, "Pregunta 2: $pregunta2")
	verificarSinNavegacionNoPermitida('en la pregunta 2')

	WebUI.comment('Y al usar "Atrás" del navegador no puede volver a editar la pregunta 1 (HU 2.1.1 CA2)')
	WebUI.back()
	WebUI.waitForPageLoad(Configuracion.timeoutLargo())
	WebUI.delay(1)
	Evidencia.capturar(CASO, '03_despues_de_atras')
	if (WebUI.waitForElementVisible(findTestObject(LBL_ENUNCIADO), Configuracion.timeoutCorto(), FailureHandling.OPTIONAL)
			&& WebUI.getText(findTestObject(LBL_ENUNCIADO), FailureHandling.OPTIONAL)?.trim() == pregunta1
			&& WebUI.waitForElementVisible(findTestObject(TXT_RESPUESTA), 1, FailureHandling.OPTIONAL)) {
		WebElement campo = WebUiCommonHelper.findWebElement(findTestObject(TXT_RESPUESTA), 1)
		if (campo.isEnabled() && campo.getAttribute('readonly') == null) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: con 'Atrás' del navegador se vuelve a la pregunta 1 y su respuesta es editable. Incumple HU 2.1.1 CA2.")
		}
	}

	WebUI.comment('Y al reingresar al módulo continúa en la pregunta 2: la respuesta 1 quedó persistida (HU 2.1.1 CA3, CR-006)')
	String preguntaAlReingresar = abrirEvaluacion()
	Evidencia.capturar(CASO, '04_reingreso')
	Evidencia.registrar(CASO, "Pregunta al reingresar: $preguntaAlReingresar")
	if (preguntaAlReingresar == pregunta1) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: al reingresar se presenta de nuevo la pregunta 1; la respuesta confirmada no se conservó o se permite responderla otra vez. Incumple HU 2.1.1 CA2/CA3 y CR-006.")
	} else if (preguntaAlReingresar != pregunta2) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: al reingresar no se continúa en la pregunta pendiente (esperada: '$pregunta2', obtenida: '$preguntaAlReingresar'). Incumple CR-006.")
	}
	Evidencia.registrar(CASO, 'Verificaciones de flujo secuencial y persistencia finalizadas (ver estado del caso en el reporte)')
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
