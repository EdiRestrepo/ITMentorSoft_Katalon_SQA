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
 * TC15 - Calificación de recursos recomendados
 * Azure Test Plans #448 "Verificar calificación de recursos recomendados"
 *
 * PASA si:  (1) cada recurso recomendado ofrece calificación de 1 a 5;
 *           (2) al confirmar una calificación el sistema la guarda (confirmación y estrellas marcadas);
 *           (3) al modificarla actualiza el valor sin crear un registro duplicado (API de calificaciones del usuario)
 *               y la calificación persiste al recargar la página.
 * FALLA si: no se puede calificar, la calificación no persiste o se duplican registros.
 * Usa un recurso con promedio 5.0 y deja como calificación final 5, para no alterar el promedio publicado.
 */
final String CASO = 'TC15'
final String R = 'Estudiante/Page_Ruta/'

List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS + [R + 'lst_Recursos', R + 'lst_TitulosRecursos', R + 'btn_Estrella', R + 'lst_EstrellasMarcadas', R + 'lbl_Promedio'])
pendientes += Autenticacion.pendientesDeCuenta('ESTUDIANTE')
Configuracion.detenerSiHayPendientes(CASO, pendientes)

/** Primera lista que aparezca en el JSON (la API envuelve las calificaciones en un objeto). */
def primeraLista
primeraLista = { Object nodo ->
	if (nodo instanceof List) {
		return nodo
	}
	if (nodo instanceof Map) {
		for (Object valor : nodo.values()) {
			def l = primeraLista(valor)
			if (l != null) {
				return l
			}
		}
	}
	return null
}

def calificaciones = { String id ->
	def r = Api.get("/content/ratings/all?user_id=$id")
	List lista = Api.esExito(r.status as int) ? (primeraLista(Api.json(r.body.toString())) ?: []) : []
	return [status: r.status, lista: lista, cuerpo: Api.resumen(r)]
}

def calificar = { String titulo, int estrellas ->
	WebUI.click(findTestObject(R + 'btn_Estrella', [titulo: titulo, n: estrellas]))
	String toast = Pagina.esperarToast(Configuracion.timeoutLargo())
	WebUI.delay(2)
	return [toast: toast, marcadas: Pagina.contar(R + 'lst_EstrellasMarcadas', [titulo: titulo])]
}

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que el estudiante accede a un recurso recomendado de su ruta')
	Autenticacion.iniciarSesion('ESTUDIANTE', CASO)
	String id = Api.valorDeSesion('auth_user_id')
	Pagina.abrir('/student/route', 4)
	if (!WebUI.waitForElementVisible(findTestObject(R + 'lst_Recursos'), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)) {
		KeywordUtil.markErrorAndStop("[$CASO] Precondición no cumplida: la ruta no presenta recursos. Ejecutar antes TC12.")
	}
	List<String> titulos = Pagina.textos(R + 'lst_TitulosRecursos').collect { it.replace('↗', '').trim() }
	String titulo = titulos.find { Pagina.texto(R + 'lbl_Promedio', [titulo: it], 1)?.contains('5.0') } ?: titulos[0]
	int opciones = WebUI.executeJavaScript("return document.querySelectorAll('app-student-route .route__item:first-child .route__star').length;", null) as int
	Map antes = calificaciones(id)
	Evidencia.registrar(CASO, "Recurso elegido: '$titulo' (promedio ${Pagina.texto(R + 'lbl_Promedio', [titulo: titulo])}); opciones de calificación: $opciones; calificaciones del usuario antes: ${antes.lista.size()} (${antes.cuerpo.take(200)})")
	if (opciones != 5) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el recurso ofrece $opciones opciones de calificación y se esperaban 5 (1 a 5) (#448).")
	}

	WebUI.comment('Cuando selecciona y confirma una calificación válida')
	Map primera = calificar(titulo, 4)
	Evidencia.capturar(CASO, '01_calificacion_4')
	Evidencia.registrar(CASO, "Calificar con 4 -> '${primera.toast}', estrellas marcadas ${primera.marcadas}")
	if (!(primera.toast =~ /(?i)gracias por calificar|calificación actualizada/) || primera.marcadas != 4) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: la calificación 4 no se confirmó ('${primera.toast}', ${primera.marcadas} estrellas marcadas) (#448).")
	}

	WebUI.comment('Y modifica la calificación existente')
	Map segunda = calificar(titulo, 5)
	Evidencia.registrar(CASO, "Calificar con 5 -> '${segunda.toast}', estrellas marcadas ${segunda.marcadas}")
	if (!(segunda.toast =~ /(?i)calificación actualizada/) || segunda.marcadas != 5) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: la modificación de la calificación no se reconoce como actualización ('${segunda.toast}', ${segunda.marcadas} estrellas) (#448).")
	}

	WebUI.comment('Entonces el sistema actualiza el valor sin duplicar registros y la calificación persiste')
	Pagina.abrir('/student/route', 4)
	WebUI.waitForElementVisible(findTestObject(R + 'lst_Recursos'), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)
	int persistidas = Pagina.contar(R + 'lst_EstrellasMarcadas', [titulo: titulo])
	Map despues = calificaciones(id)
	Evidencia.capturar(CASO, '02_calificacion_persistida')
	Evidencia.registrar(CASO, "Tras recargar: $persistidas estrellas marcadas; calificaciones del usuario después: ${despues.lista.size()} (${despues.cuerpo.take(400)}); promedio ${Pagina.texto(R + 'lbl_Promedio', [titulo: titulo])}")
	if (Api.esExito(despues.status as int) && despues.lista.isEmpty()) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: después de calificar, la API de calificaciones del usuario no devuelve ninguna calificación (${despues.cuerpo.take(200)}).")
	}
	if (persistidas != 5) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: tras recargar, la calificación del recurso muestra $persistidas estrellas y se guardó 5 (#448).")
	}
	if (Api.esExito(despues.status as int) && despues.lista.size() > antes.lista.size() + 1) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: calificar y modificar la calificación generó ${despues.lista.size() - antes.lista.size()} registros nuevos; se esperaba como máximo 1 (#448).")
	} else if (!Api.esExito(despues.status as int)) {
		KeywordUtil.markWarning("[$CASO] No se pudo verificar en la API la ausencia de duplicados (${despues.cuerpo.take(200)}).")
	}
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
