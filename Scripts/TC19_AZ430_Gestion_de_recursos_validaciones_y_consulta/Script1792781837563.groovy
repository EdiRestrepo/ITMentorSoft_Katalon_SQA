import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.util.KeywordUtil as KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import itmentorsoft.Autenticacion
import itmentorsoft.Configuracion
import itmentorsoft.Evidencia
import itmentorsoft.Pagina

/*
 * TC19 - Registro, consulta y actualización de recursos educativos (validaciones y asociación a categoría)
 * Azure Test Plans #430 "Verificar registro y actualización de contenido académico" | HU 3.x Recursos
 *
 * PASA si:  (1) el formulario de nuevo recurso muestra los campos obligatorios y, con campos vacíos, longitudes
 *               fuera de rango o URL sin https://, muestra la validación y no guarda;
 *           (2) al filtrar por categoría la tabla muestra solo recursos de esa categoría;
 *           (3) la búsqueda por título encuentra el recurso y el formulario de edición carga sus datos.
 * FALLA si: se guarda un recurso inválido, el filtro mezcla categorías o la edición no carga el recurso.
 * No guarda recursos: el entorno es compartido; el alta y la edición con datos válidos quedan fuera del alcance
 * automatizado para no alterar el catálogo que ven los estudiantes.
 */
final String CASO = 'TC19'
final String P = 'Recursos/Page_Recursos/'

List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS + [P + 'cnt_Recursos', P + 'btn_NuevoRecurso', P + 'lst_FilasRecursos', P + 'lst_CategoriasFila',
	P + 'sel_Categoria', P + 'txt_Buscar', P + 'btn_Buscar', P + 'cnt_Modal', P + 'btn_CerrarModal', P + 'btn_GuardarRecurso', P + 'lbl_ErrorRecurso', P + 'lst_ErroresRecurso', P + 'lnk_Editar'])
pendientes += Autenticacion.pendientesDeCuenta('DOCENTE')
Configuracion.detenerSiHayPendientes(CASO, pendientes)

def errorDe = { String campo -> Pagina.texto(P + 'lbl_ErrorRecurso', [campo: campo], 2) }
def escribir = { String campo, String valor ->
	def o = findTestObject('Comun/txt_Campo', [campo: campo])
	WebUI.clearText(o, FailureHandling.OPTIONAL)
	WebUI.executeJavaScript(Pagina.CAMPO_JS + "e.value=''; e.dispatchEvent(new Event('input',{bubbles:true}));", [campo])
	if (valor) {
		WebUI.sendKeys(o, valor)
	}
	WebUI.executeJavaScript(Pagina.CAMPO_JS + "e.dispatchEvent(new Event('blur',{bubbles:true}));", [campo])
}

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que el docente ingresa al módulo de recursos educativos')
	Autenticacion.iniciarSesion('DOCENTE', CASO)
	Pagina.abrir('/teacher/resources', 3)
	WebUI.waitForElementVisible(findTestObject(P + 'lst_FilasRecursos'), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)
	int filasIniciales = Pagina.contar(P + 'lst_FilasRecursos')
	WebUI.click(findTestObject(P + 'btn_NuevoRecurso'))
	Pagina.visible(P + 'cnt_Modal', null, Configuracion.timeoutLargo())
	String formulario = Pagina.texto(P + 'cnt_Modal')
	Evidencia.registrar(CASO, "Formulario de nuevo recurso: $formulario")
	['Título', 'Descripción', 'URL', 'Temas relacionados'].each { campo ->
		if (!formulario?.contains(campo)) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: el formulario de recurso no muestra el campo obligatorio '$campo' (#430).")
		}
	}

	WebUI.comment('Cuando intenta guardar con campos vacíos')
	WebUI.click(findTestObject(P + 'btn_GuardarRecurso'))
	WebUI.delay(1)
	List<String> errores = Pagina.textos(P + 'lst_ErroresRecurso')
	Evidencia.capturar(CASO, '01_campos_vacios')
	Evidencia.registrar(CASO, "Guardar con campos vacíos -> errores: $errores")
	if (errores.size() < 4) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: con campos vacíos se esperaban errores en título, descripción, URL y temas, y se obtuvieron $errores (#430).")
	}

	WebUI.comment('Y con longitudes fuera de rango o una URL inválida')
	[['title', 'Abc', 'Mínimo 5 caracteres'], ['title', 'x' * 151, 'Máximo 150 caracteres'], ['description', 'Corta', 'Mínimo 10 caracteres'],
		['description', 'x' * 301, 'Máximo 300 caracteres'], ['url', 'http://ejemplo.com/recurso', null]].each { prueba ->
		escribir(prueba[0], prueba[1])
		String obtenido = errorDe(prueba[0])
		Evidencia.registrar(CASO, "Campo '${prueba[0]}' con ${prueba[1].length()} caracteres -> '$obtenido'")
		if (prueba[2] ? obtenido != prueba[2] : !(obtenido =~ /(?i)https/)) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: el campo '${prueba[0]}' con valor de ${prueba[1].length()} caracteres muestra '$obtenido' (esperado: ${prueba[2] ?: 'debe empezar con https://'}).")
		}
	}
	WebUI.click(findTestObject(P + 'btn_GuardarRecurso'))
	String toast = Pagina.esperarToast(4)
	Evidencia.capturar(CASO, '02_validaciones_longitud_y_url')
	Evidencia.registrar(CASO, "Guardar con datos inválidos -> modal abierto: ${Pagina.visible(P + 'cnt_Modal', null, 1)}, notificación: '$toast'")

	WebUI.comment('Entonces el sistema muestra la validación y no guarda el recurso')
	if (toast?.contains('Recurso creado') || !Pagina.visible(P + 'cnt_Modal', null, 1)) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: se guardó un recurso con datos inválidos ('$toast'). Incumple #430.")
	}
	WebUI.click(findTestObject(P + 'btn_CerrarModal'))
	WebUI.delay(1)
	if (Pagina.contar(P + 'lst_FilasRecursos') != filasIniciales) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: la tabla de recursos cambió tras un intento de guardado inválido.")
	}

	WebUI.comment('Y consulta los recursos filtrando por categoría')
	List<String> categorias = WebUI.executeJavaScript("return [...document.querySelectorAll('app-resources select.res__filter-select option')].map(o => o.value).filter(v => v !== 'all');", null) as List<String>
	categorias.each { cat ->
		WebUI.selectOptionByValue(findTestObject(P + 'sel_Categoria'), java.util.regex.Pattern.quote(cat), true)
		WebUI.delay(2)
		List<String> cats = Pagina.textos(P + 'lst_CategoriasFila')
		List<String> otras = cats.findAll { !it.equalsIgnoreCase(cat) }
		Evidencia.registrar(CASO, "Filtro de categoría '$cat': ${cats.size()} recursos en la página; categorías distintas: $otras")
		if (otras) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: con el filtro '$cat' aparecen recursos de otra categoría: ${otras.unique()} (#430).")
		}
	}
	Evidencia.capturar(CASO, '03_filtro_por_categoria')
	WebUI.selectOptionByValue(findTestObject(P + 'sel_Categoria'), 'all', false)
	WebUI.delay(2)

	WebUI.comment('Y busca un recurso por su título y abre su edición')
	String titulo = WebUI.executeJavaScript("var e=document.querySelector('app-resources tr.res__row .res__name'); return e ? e.textContent.trim() : null;", null)
	WebUI.setText(findTestObject(P + 'txt_Buscar'), titulo)
	WebUI.click(findTestObject(P + 'btn_Buscar'))
	WebUI.delay(2)
	List<String> encontrados = WebUI.executeJavaScript("return [...document.querySelectorAll('app-resources tr.res__row .res__name')].map(e => e.textContent.trim());", null) as List<String>
	Evidencia.registrar(CASO, "Búsqueda por título '$titulo' -> $encontrados")
	if (!encontrados.contains(titulo)) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: la búsqueda por título no encuentra el recurso '$titulo'.")
	}
	WebUI.click(findTestObject(P + 'lnk_Editar'))
	Pagina.visible(P + 'cnt_Modal', null, Configuracion.timeoutLargo())
	String tituloEditado = WebUI.getAttribute(findTestObject('Comun/txt_Campo', [campo: 'title']), 'value', FailureHandling.OPTIONAL)
	Evidencia.capturar(CASO, '04_edicion_recurso')
	Evidencia.registrar(CASO, "Edición abierta: título cargado '$tituloEditado', botón '${Pagina.texto(P + 'btn_GuardarRecurso')}'")
	if (tituloEditado != titulo) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el formulario de edición no carga los datos del recurso (título '$tituloEditado', esperado '$titulo').")
	}
	WebUI.click(findTestObject(P + 'btn_CerrarModal'))
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
