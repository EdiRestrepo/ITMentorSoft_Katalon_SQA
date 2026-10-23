import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.util.KeywordUtil as KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import itmentorsoft.Api
import itmentorsoft.Autenticacion
import itmentorsoft.Configuracion
import itmentorsoft.Evidencia
import itmentorsoft.Pagina

/*
 * TC13 - Progreso de aprendizaje, perfil de conocimiento y categoría del estudiante
 * Azure Test Plans #475 (objetivos según la categoría o mensaje de evaluación pendiente), #459 (explicación de la
 * categorización), #451 (identificación de debilidades), #426 (visualización del progreso) y #465 (gráfica comparativa)
 *
 * PASA si:  (1) el nivel mostrado en el panel y en "Mi progreso" es el de la última evaluación calificada (API) y es
 *               una de las 4 categorías; sin categoría, el panel muestra el mensaje pendiente con acceso a la evaluación;
 *           (2) el perfil de conocimiento lista cada tema evaluado con su porcentaje (0-100 %) y los temas por debajo
 *               del 70 % quedan identificados como áreas a reforzar;
 *           (3) el feedback (explicación de la categorización) está visible y coincide con el de la API;
 *           (4) "Progreso por tema" presenta la gráfica y la tabla de datos por evaluación.
 * FALLA si: el nivel no coincide, falta el perfil, el feedback o la gráfica de progreso.
 */
final String CASO = 'TC13'
final String D = 'Estudiante/Page_Dashboard/'
final String P = 'Estudiante/Page_Progreso/'
final List<String> CATEGORIAS = ['principiante', 'básico', 'intermedio', 'avanzado']
final int UMBRAL_DEBILIDAD = 70

List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS + [D + 'cnt_Dashboard', D + 'lbl_Nivel', D + 'lbl_SinCategoria', D + 'lst_PerfilTemas', D + 'lbl_Feedback',
	D + 'lbl_Estadistica', D + 'lbl_Tendencia', P + 'cnt_Progreso', P + 'lbl_Nivel', P + 'lst_PerfilTemas', P + 'lbl_Feedback', P + 'cnt_GraficoProgreso', P + 'lst_FilasProgreso'])
pendientes += Autenticacion.pendientesDeCuenta('ESTUDIANTE')
Configuracion.detenerSiHayPendientes(CASO, pendientes)

/** "Tema 8%" -> [tema: 'Tema', pct: 8] */
def temas = { String ruta ->
	return Pagina.textos(ruta).collect { t ->
		def m = t =~ /^(.*?)\s+(\d+)%$/
		m.find() ? [tema: m.group(1).trim(), pct: m.group(2) as int] : [tema: t, pct: -1]
	}
}

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que el estudiante ha iniciado sesión y tiene evaluaciones registradas')
	Autenticacion.iniciarSesion('ESTUDIANTE', CASO)
	String id = Api.valorDeSesion('auth_user_id')
	List evaluaciones = (Api.json(Api.get("/assessments/summary?student_id=$id&page=0&page_size=50").body.toString())?.assessments ?: []) as List
	Map ultima = evaluaciones.findAll { it.classification }.max { it.date_taken } as Map
	Evidencia.registrar(CASO, "API: ${evaluaciones.size()} evaluaciones; última calificada: ${ultima ? "${ultima.date_taken}, nivel ${ultima.classification}, nota ${ultima.score}" : 'ninguna'}")

	WebUI.comment('Cuando accede a su panel de usuario')
	Pagina.abrir('/student/dashboard', 4)
	Pagina.visible(D + 'cnt_Dashboard', null, Configuracion.timeoutLargo())
	String nivelPanel = Pagina.texto(D + 'lbl_Nivel', null, 3)
	List<Map> perfilPanel = temas(D + 'lst_PerfilTemas')
	String tendencia = Pagina.texto(D + 'lbl_Tendencia', null, 2)
	Evidencia.capturar(CASO, '01_panel_estudiante')
	Evidencia.registrar(CASO, "Panel: nivel '$nivelPanel', perfil $perfilPanel, última evaluación ${Pagina.texto(D + 'lbl_Estadistica', [nombre: 'Última evaluación'])} (comparación: '$tendencia')")

	WebUI.comment('Entonces muestra la categoría asignada o el mensaje de evaluación pendiente')
	if (ultima) {
		if (!nivelPanel || !nivelPanel.equalsIgnoreCase(ultima.classification.toString())) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: el panel muestra el nivel '$nivelPanel' y la última evaluación calificada tiene '${ultima.classification}' (#475/#459).")
		}
		if (!(ultima.classification.toString().toLowerCase() in CATEGORIAS)) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: la categoría asignada '${ultima.classification}' no es una de las 4 categorías definidas (#469).")
		}
	} else if (!Pagina.visible(D + 'lbl_SinCategoria', null, 3)) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: sin evaluaciones calificadas el panel no muestra el mensaje 'Sin categoría asignada aún' (#475).")
	}

	WebUI.comment('Cuando consulta la sección Mi progreso')
	Pagina.abrir('/student/progress', 4)
	Pagina.visible(P + 'cnt_Progreso', null, Configuracion.timeoutLargo())
	String nivelProgreso = Pagina.texto(P + 'lbl_Nivel', null, 3)
	List<Map> perfil = temas(P + 'lst_PerfilTemas')
	String feedback = Pagina.texto(P + 'lbl_Feedback', null, 3)
	boolean grafica = Pagina.visible(P + 'cnt_GraficoProgreso', null, 3)
	List<String> filasProgreso = Pagina.textos(P + 'lst_FilasProgreso')
	Evidencia.capturar(CASO, '02_mi_progreso')
	Evidencia.registrar(CASO, "Mi progreso: nivel '$nivelProgreso', perfil $perfil, gráfica: $grafica, filas de progreso: $filasProgreso")
	Evidencia.registrar(CASO, "Feedback: ${feedback?.take(400)}")
	if (!ultima) {
		KeywordUtil.markWarning("[$CASO] El estudiante no tiene evaluaciones calificadas: solo se verificó la vista sin categoría. Ejecutar antes TC12.")
		return
	}

	WebUI.comment('Entonces muestra su perfil de conocimiento con las áreas de mejora y la explicación de su categorización')
	if (!nivelProgreso?.equalsIgnoreCase(ultima.classification.toString())) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: 'Mi progreso' muestra el nivel '$nivelProgreso' y la API '${ultima.classification}' (#459).")
	}
	if (perfil.isEmpty() || perfil.any { it.pct < 0 || it.pct > 100 }) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el perfil de conocimiento no lista los temas con un porcentaje válido: $perfil (#451).")
	}
	if (perfilPanel*.tema.toSet() != perfil*.tema.toSet()) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el perfil del panel $perfilPanel no coincide con el de 'Mi progreso' $perfil.")
	}
	List<String> debilidades = perfil.findAll { it.pct < UMBRAL_DEBILIDAD }*.tema
	Evidencia.registrar(CASO, "Áreas de mejora (< $UMBRAL_DEBILIDAD %): $debilidades")
	if (!feedback || feedback != ultima.feedback?.toString()?.replaceAll('\\s+', ' ')?.trim()) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el feedback visible no corresponde al de la última evaluación calificada (#459).")
	}
	if (!grafica || filasProgreso.isEmpty()) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: 'Progreso por tema' no presenta la gráfica ni los datos por evaluación (#426/#465).")
	}
	KeywordUtil.markWarning("[$CASO] Observación #451: la interfaz muestra el porcentaje por tema, pero no clasifica las debilidades por severidad (alta < 50 %, media 50-69 %).")
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
