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
 * TC16 - Acceso autorizado a los reportes de estudiantes y extracción de datos consolidados en JSON
 * Azure Test Plans #450 (acceso autorizado a reportes por estudiante) y #500 (control de acceso al módulo de
 * reportes generales y extracción JSON) | HU 4.1.x Reportes docentes
 *
 * PASA si:  (1) el Estudiante no ve el módulo de reportes ni el reporte individual de otro estudiante, y la API
 *               de reportes le responde 401/403/404;
 *           (2) el Docente ve el módulo, la API devuelve un JSON consolidado (id, nombre y nivel por estudiante)
 *               cuyo total coincide con la tabla, y puede abrir el reporte individual de un estudiante.
 * FALLA si: un rol no autorizado obtiene datos de reportes o el docente no puede consultarlos.
 */
final String CASO = 'TC16'
final String R = 'Reportes/Page_Reportes/'
final String CNT_REPORTES = R + 'cnt_Reportes'
final String FILAS = R + 'lst_FilasEstudiantes'
final String LNK_REPORTE = R + 'lnk_VerReporte'
final String CNT_DETALLE = 'Reportes/Page_DetalleEstudiante/cnt_DetalleEstudiante'
final List<Integer> DENEGADO = [401, 403, 404]

List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS + [CNT_REPORTES, FILAS, LNK_REPORTE, CNT_DETALLE])
pendientes += Autenticacion.pendientesDeCuenta('ESTUDIANTE') + Autenticacion.pendientesDeCuenta('DOCENTE')
Configuracion.detenerSiHayPendientes(CASO, pendientes)

String idOtroEstudiante = null

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que existen resultados registrados de estudiantes y un docente autorizado consulta los reportes')
	Autenticacion.iniciarSesion('DOCENTE', CASO)
	Pagina.abrir('/teacher/reports', 3)
	boolean moduloVisible = Pagina.visible(CNT_REPORTES, null, Configuracion.timeoutLargo())
	WebUI.waitForElementVisible(findTestObject(FILAS), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)
	int filas = Pagina.contar(FILAS)
	Evidencia.capturar(CASO, '01_docente_modulo_reportes')
	Map<String, Object> api = Api.get('/reports/students?page=0&page_size=100')
	def json = Api.json(api.body.toString())
	List estudiantes = (json?.result?.students ?: []) as List
	Evidencia.registrar(CASO, "Docente - módulo visible: $moduloVisible, filas en la tabla: $filas, API: HTTP ${api.status}, total_students=${json?.result?.total_students}, registros=${estudiantes.size()}")
	if (!moduloVisible || filas == 0) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el docente autorizado no visualiza el reporte de estudiantes (módulo visible: $moduloVisible, filas: $filas). Incumple #450.")
	}
	if (!Api.esExito(api.status as int) || estudiantes.isEmpty()) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: la API de reportes no entrega datos consolidados al docente (${Api.resumen(api)}). Incumple #500.")
	} else {
		List incompletos = estudiantes.findAll { !(it.student_id && it.student_name && it.knowledge_classification) }
		if (incompletos) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: ${incompletos.size()} registros del JSON consolidado no traen id, nombre y nivel del estudiante (#500).")
		}
		if ((json.result.total_students as int) != estudiantes.size() || (filas > 0 && filas != Math.min(estudiantes.size(), filas))) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: el total del JSON (${json.result.total_students}) no coincide con los registros (${estudiantes.size()}) o con la tabla ($filas).")
		}
		idOtroEstudiante = estudiantes[0].student_id
	}

	WebUI.comment('Y el docente abre el reporte individual de un estudiante')
	if (filas > 0) {
		WebUI.click(findTestObject(LNK_REPORTE))
		boolean detalle = Pagina.visible(CNT_DETALLE, null, Configuracion.timeoutLargo())
		WebUI.delay(2)
		Evidencia.capturar(CASO, '02_docente_reporte_individual')
		Evidencia.registrar(CASO, "Docente - reporte individual (${Pagina.rutaActual()}): visible=$detalle")
		if (!detalle) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: el docente no puede abrir el reporte individual del estudiante (#450).")
		}
	}
	Autenticacion.cerrarSesion(CASO)

	WebUI.comment('Cuando un usuario no autorizado (Estudiante) intenta consultar los resultados')
	Autenticacion.iniciarSesion('ESTUDIANTE', CASO)
	['/teacher/reports', "/teacher/students/${idOtroEstudiante ?: 'id'}"].each { ruta ->
		Pagina.abrir(ruta, 3)
		Evidencia.registrar(CASO, "Estudiante - vista $ruta -> ${Pagina.rutaActual()}")
		if (Pagina.rutaActual().startsWith('/teacher') || Pagina.visible(CNT_REPORTES, null, 1) || Pagina.visible(CNT_DETALLE, null, 1)) {
			KeywordUtil.markFailed("[$CASO] DEFECTO DE SEGURIDAD: el estudiante accede a la vista $ruta de reportes docentes. Incumple #450.")
		}
	}
	Evidencia.capturar(CASO, '03_estudiante_vista_denegada')
	['/reports/students?page=0&page_size=5', "/reports/student_summary?id=${idOtroEstudiante ?: 'id'}"].each { ruta ->
		Map<String, Object> r = Api.get(ruta)
		Evidencia.registrar(CASO, "Estudiante - API $ruta -> ${Api.resumen(r)}")
		if (Api.esExito(r.status as int)) {
			KeywordUtil.markFailed("[$CASO] DEFECTO DE SEGURIDAD: la API $ruta entrega datos de reportes al estudiante (HTTP ${r.status}). Incumple #450/#500.")
		} else if (!(r.status in DENEGADO)) {
			KeywordUtil.markWarning("[$CASO] Respuesta no concluyente de $ruta para el estudiante: HTTP ${r.status}.")
		}
	}

	WebUI.comment('Entonces los datos permanecen protegidos y no se exponen a usuarios no autorizados')
	Evidencia.registrar(CASO, 'Verificación de acceso a reportes por rol finalizada')
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
