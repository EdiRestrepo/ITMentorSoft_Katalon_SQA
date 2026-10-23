import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.util.KeywordUtil as KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import itmentorsoft.Autenticacion
import itmentorsoft.Configuracion
import itmentorsoft.Evidencia
import itmentorsoft.Pagina

/*
 * TC20 - Paginación y operaciones del banco de preguntas (cuestionarios)
 * Azure Test Plans #468 "Validar paginación exacta de 20 preguntas por tema y persistencia de operaciones CRUD"
 * Complementa #499 (preservación de versiones): consulta el historial de versiones de una pregunta.
 *
 * PASA si:  (1) la tabla pagina de forma consistente: cada página muestra el tamaño de página (salvo la última),
 *               "Página X de N" cumple N = techo(total / tamaño) y "Siguiente" presenta preguntas distintas;
 *           (2) los filtros por dificultad y por categoría muestran solo preguntas del valor elegido;
 *           (3) el formulario de nueva pregunta valida los campos obligatorios y no registra una pregunta inválida;
 *           (4) el detalle de una pregunta permite consultar su historial de versiones.
 * FALLA si: la paginación es inconsistente, un filtro mezcla valores o se registra una pregunta inválida.
 * Observación: #468 exige 20 preguntas por página; si la aplicación usa otro tamaño se registra una advertencia.
 * No crea, edita ni desactiva preguntas (el banco alimenta las evaluaciones reales).
 */
final String CASO = 'TC20'
final String B = 'Administracion_Evaluaciones/Page_AdminEvaluaciones/'
final String F = 'Administracion_Evaluaciones/Page_FormularioPregunta/'
final int TAMANO_REQUERIDO = 20

List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS + [B + 'lst_FilasPreguntas', B + 'lbl_TotalPreguntas', B + 'lbl_Paginacion', B + 'btn_PaginaSiguiente',
	B + 'btn_Pestana', B + 'sel_ValorFiltro', B + 'btn_Filtrar', B + 'lst_DificultadFila', B + 'btn_VerPrimera', B + 'cnt_DetallePregunta', B + 'btn_HistorialVersiones',
	B + 'cnt_Versiones', F + 'cnt_Formulario', F + 'btn_CrearPregunta', F + 'lst_Errores'])
pendientes += Autenticacion.pendientesDeCuenta('DOCENTE')
Configuracion.detenerSiHayPendientes(CASO, pendientes)

def numero = { String t -> ((t ?: '') =~ /\d+/).with { it.find() ? (it.group() as int) : -1 } }
def ids = { -> WebUI.executeJavaScript("return [...document.querySelectorAll('app-question-bank tr.cuest__row .cuest__id')].map(e => e.textContent.trim());", null) as List<String> }

/** Aplica un filtro de la pestaña indicada con cada valor disponible y verifica la columna correspondiente. */
def verificarFiltro = { String pestana, String columna ->
	WebUI.click(findTestObject(B + 'btn_Pestana', [nombre: pestana]))
	WebUI.delay(2)
	List<String> valores = WebUI.executeJavaScript("return [...document.querySelectorAll('app-question-bank select.cuest__select option')].map(o => o.value).filter(v => v);", null) as List<String>
	valores.each { v ->
		WebUI.selectOptionByValue(findTestObject(B + 'sel_ValorFiltro'), java.util.regex.Pattern.quote(v), true)
		if (WebUI.waitForElementClickable(findTestObject(B + 'btn_Filtrar'), 2, FailureHandling.OPTIONAL)) {
			WebUI.click(findTestObject(B + 'btn_Filtrar'))
		}
		WebUI.delay(2)
		List<String> celdas = WebUI.executeJavaScript("return [...document.querySelectorAll(\"app-question-bank tr.cuest__row td[data-label='\" + arguments[0] + \"']\")].map(e => e.textContent.trim());", [columna]) as List<String>
		List<String> otras = celdas.findAll { !it.equalsIgnoreCase(v) }
		Evidencia.registrar(CASO, "Filtro $pestana = '$v': ${celdas.size()} preguntas en la página; valores distintos: ${otras.unique()}")
		if (celdas.isEmpty() || otras) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: el filtro $pestana = '$v' muestra ${celdas.size()} preguntas, con valores distintos: ${otras.unique()} (#468).")
		}
	}
	return valores
}

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que el docente accede al banco de preguntas')
	Autenticacion.iniciarSesion('DOCENTE', CASO)
	Pagina.abrir('/teacher/questions', 3)
	WebUI.waitForElementVisible(findTestObject(B + 'lst_FilasPreguntas'), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)

	WebUI.comment('Cuando el sistema carga la lista de preguntas')
	int total = numero(Pagina.texto(B + 'lbl_TotalPreguntas'))
	int filas = Pagina.contar(B + 'lst_FilasPreguntas')
	def pag = (Pagina.texto(B + 'lbl_Paginacion') ?: '') =~ /(\d+)\s+de\s+(\d+)/
	int paginas = pag.find() ? (pag.group(2) as int) : -1
	List<String> pagina1 = ids()
	Evidencia.capturar(CASO, '01_banco_pagina_1')
	Evidencia.registrar(CASO, "Banco: $total preguntas, $filas filas en la página 1, $paginas páginas")

	WebUI.comment('Entonces despliega el listado paginado de forma consistente')
	if (total <= 0 || filas <= 0 || paginas != Math.ceil(total / (double) filas) as int) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: paginación inconsistente: $total preguntas, $filas por página y $paginas páginas (#468).")
	}
	if (filas != TAMANO_REQUERIDO && total >= TAMANO_REQUERIDO) {
		KeywordUtil.markWarning("[$CASO] Observación #468: el banco pagina de a $filas preguntas; Azure exige exactamente $TAMANO_REQUERIDO por página.")
	}
	WebUI.click(findTestObject(B + 'btn_PaginaSiguiente'))
	WebUI.delay(2)
	List<String> pagina2 = ids()
	Evidencia.registrar(CASO, "Página 2: ${pagina2.size()} preguntas; ${Pagina.texto(B + 'lbl_Paginacion')}")
	if (pagina2.isEmpty() || !pagina2.disjoint(pagina1)) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: la página 2 repite preguntas de la página 1 o está vacía (#468).")
	}

	WebUI.comment('Y cuando filtra las preguntas por dificultad y por categoría')
	verificarFiltro('Por dificultad', 'Dificultad')
	Evidencia.capturar(CASO, '02_filtro_dificultad')
	verificarFiltro('Por categoría', 'Categoría')
	Evidencia.capturar(CASO, '03_filtro_categoria')
	WebUI.click(findTestObject(B + 'btn_Pestana', [nombre: 'Todas']))
	WebUI.delay(2)

	WebUI.comment('Y consulta el detalle y el historial de versiones de una pregunta')
	WebUI.click(findTestObject(B + 'btn_VerPrimera'))
	boolean detalle = Pagina.visible(B + 'cnt_DetallePregunta', null, Configuracion.timeoutLargo())
	WebUI.delay(1)
	boolean versiones = false
	if (WebUI.waitForElementClickable(findTestObject(B + 'btn_HistorialVersiones'), 5, FailureHandling.OPTIONAL)) {
		WebUI.click(findTestObject(B + 'btn_HistorialVersiones'))
		versiones = Pagina.visible(B + 'cnt_Versiones', null, Configuracion.timeoutLargo())
		WebUI.delay(2)
	}
	String textoVersiones = Pagina.texto(B + 'cnt_Versiones', null, 2)
	Evidencia.capturar(CASO, '04_detalle_y_versiones')
	Evidencia.registrar(CASO, "Detalle visible: $detalle; historial de versiones visible: $versiones (${textoVersiones?.take(300)})")
	if (!detalle) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: no se puede consultar el detalle de una pregunta del banco.")
	}
	if (!versiones) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el detalle de la pregunta no permite consultar su historial de versiones (#499).")
	}

	WebUI.comment('Y cuando intenta registrar una pregunta con datos inválidos')
	Pagina.abrir('/teacher/questions/new', 3)
	Pagina.visible(F + 'cnt_Formulario', null, Configuracion.timeoutLargo())
	WebUI.setText(findTestObject('Comun/txt_Campo', [campo: 'text']), 'Pregunta corta')
	WebUI.click(findTestObject(F + 'btn_CrearPregunta'))
	WebUI.delay(1)
	List<String> errores = Pagina.textos(F + 'lst_Errores')
	String toast = Pagina.esperarToast(3)
	Evidencia.capturar(CASO, '05_formulario_pregunta_invalida')
	Evidencia.registrar(CASO, "Crear pregunta inválida -> ${errores.size()} errores (${errores.unique().take(6)}), notificación '$toast'")
	if (toast?.contains('Pregunta creada')) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: se registró una pregunta con datos inválidos (#468).")
	}
	if (errores.isEmpty()) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el formulario de nueva pregunta no muestra validaciones con campos obligatorios vacíos (#468).")
	}
	KeywordUtil.markWarning("[$CASO] Alcance #468: la persistencia del CRUD (crear, editar, desactivar) no se automatizó para no alterar el banco que usan las evaluaciones reales.")
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
