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

/*
 * TC01 - Aislamiento de datos académicos entre estudiantes
 * Azure Test Plans #496 | HU 1.2.2 Acceso y privacidad en datos (#198) CA2 y CA3 | HH 1.2.1 (#360) CA2
 * Riesgo RH01 / SQA R03: un estudiante obtiene resultados académicos de otro estudiante (IDOR).
 *
 * El id del Estudiante B se toma de Data Files/TD_TC01_Aislamiento. Con el valor AUTO se obtiene
 * iniciando sesión como Docente y leyendo la lista de estudiantes (API de Reportes).
 *
 * PASA si:  (1) el Estudiante A ve su propio panel académico;
 *           (2) la API responde el código esperado (404 genérico, CA3) al pedir los datos de B con la sesión de A;
 *           (3) al abrir la URL del reporte de B, la interfaz no renderiza ese reporte.
 * FALLA si: la API entrega datos de B (2xx), responde un código distinto del 404 genérico, o se renderiza el reporte de B.
 * ERROR si: falta configuración, falla el login de precondición o no hay un Estudiante B disponible.
 */
final String CASO = 'TC01'
final String CNT_RESULTADOS = 'Privacidad_Datos/Page_ResultadosAcademicos/cnt_ResultadosAcademicos'
final String CNT_DETALLE = 'Privacidad_Datos/Page_DetalleEstudiante/cnt_DetalleEstudiante'
final String MARCADOR_ID = '{idEstudiante}'
final String ID_AUTOMATICO = 'AUTO'
final List<String> APIS_DATOS_ESTUDIANTE = ['API_RESUMEN_ESTUDIANTE', 'API_HISTORIAL_ESTUDIANTE']

TestData datos = findTestData('Data Files/TD_TC01_Aislamiento')
String idEstudianteB = datos.getValue('id_estudiante_b', 1)?.trim()
boolean idAutomatico = ID_AUTOMATICO.equalsIgnoreCase(idEstudianteB)

// ---- 0. Configuración requerida (sin ella el caso termina en ERROR, nunca en PASSED)
List<String> pendientes = []
pendientes += Configuracion.variablesPendientes(['BASE_URL', 'RUTA_RESULTADOS_PROPIOS', 'RUTA_RESULTADOS_ESTUDIANTE'] + APIS_DATOS_ESTUDIANTE)
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS + [CNT_RESULTADOS, CNT_DETALLE])
pendientes += Autenticacion.pendientesDeCuenta('ESTUDIANTE_A')
pendientes += Configuracion.datosPendientes(datos, ['id_estudiante_b', 'codigo_http_esperado'])
if (idAutomatico) {
	pendientes += Autenticacion.pendientesDeCuenta('DOCENTE')
	pendientes += Configuracion.variablesPendientes(['API_ESTUDIANTES_DOCENTE'])
}
(['RUTA_RESULTADOS_ESTUDIANTE'] + APIS_DATOS_ESTUDIANTE).each { variable ->
	Object valor = Configuracion.leerVariable(variable)
	if (!Configuracion.esPendiente(valor) && !valor.toString().contains(MARCADOR_ID)) {
		pendientes << "Perfil > GlobalVariable.$variable debe contener el marcador $MARCADOR_ID"
	}
}
Configuracion.detenerSiHayPendientes(CASO, pendientes)

int codigoEsperado = datos.getValue('codigo_http_esperado', 1).trim() as int

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	List<String> idsEstudiantes = []
	if (idAutomatico) {
		WebUI.comment('Preparación: el Docente consulta la lista de estudiantes para elegir un Estudiante B')
		Autenticacion.iniciarSesion('DOCENTE', CASO)
		Map<String, Object> lista = Api.get(GlobalVariable.API_ESTUDIANTES_DOCENTE.toString())
		Evidencia.registrar(CASO, "Lista de estudiantes (sesión Docente): HTTP ${lista.status}")
		idsEstudiantes = (Api.json(lista.body.toString())?.result?.students ?: []).collect { it.student_id?.toString() }.findAll { it }
		Autenticacion.cerrarSesion(CASO)
	}

	WebUI.comment('Dado que el Estudiante A ha iniciado sesión')
	Autenticacion.iniciarSesion('ESTUDIANTE_A', CASO)
	String idEstudianteA = Api.valorDeSesion('auth_user_id')
	if (idAutomatico) {
		idEstudianteB = idsEstudiantes.find { it != idEstudianteA }
	}
	if (!idEstudianteB || idEstudianteB == idEstudianteA) {
		KeywordUtil.markErrorAndStop("[$CASO] Precondición no cumplida: no hay un Estudiante B distinto del Estudiante A (id A: $idEstudianteA). " +
				"Registrar otro estudiante o escribir su id en Data Files/TD_TC01_Aislamiento.")
	}
	Evidencia.registrar(CASO, "Estudiante A: $idEstudianteA | Estudiante B: $idEstudianteB")

	WebUI.comment('Cuando consulta sus propios datos académicos (HU 1.2.2 CA2)')
	WebUI.navigateToUrl(Configuracion.url(GlobalVariable.RUTA_RESULTADOS_PROPIOS))
	WebUI.waitForPageLoad(Configuracion.timeoutLargo())
	boolean propiosVisibles = WebUI.waitForElementVisible(findTestObject(CNT_RESULTADOS), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)
	Evidencia.capturar(CASO, '01_resultados_propios')
	if (!propiosVisibles) {
		// Sin este control positivo, las verificaciones negativas posteriores podrían pasar sobre una sesión inválida.
		KeywordUtil.markErrorAndStop("[$CASO] Precondición no cumplida: el Estudiante A no visualiza su panel académico ($CNT_RESULTADOS).")
	}
	Map<String, Object> propio = Api.get(GlobalVariable.API_RESUMEN_ESTUDIANTE.toString().replace(MARCADOR_ID, idEstudianteA))
	Evidencia.registrar(CASO, "Control positivo - datos propios por API: ${Api.resumen(propio)}")
	if (propio.status in [-1, 401]) {
		KeywordUtil.markErrorAndStop("[$CASO] Precondición no cumplida: la API no aceptó la sesión del Estudiante A (${Api.resumen(propio)}). Sin una sesión válida, el resultado negativo no es concluyente.")
	}

	WebUI.comment('Y cuando pide a la API los datos del Estudiante B con su propia sesión (HU 1.2.2 CA3)')
	APIS_DATOS_ESTUDIANTE.each { variable ->
		String ruta = GlobalVariable."$variable".toString().replace(MARCADOR_ID, idEstudianteB)
		Map<String, Object> ajeno = Api.get(ruta)
		Evidencia.registrar(CASO, "Solicitud de datos ajenos $ruta -> ${Api.resumen(ajeno)}")
		if (Api.esExito(ajeno.status)) {
			KeywordUtil.markFailed("[$CASO] DEFECTO DE SEGURIDAD: $ruta entrega datos del Estudiante B a la sesión del Estudiante A (HTTP ${ajeno.status}). Incumple HU 1.2.2 CA2/CA3.")
		} else if (ajeno.status != codigoEsperado) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: $ruta responde HTTP ${ajeno.status} y no el error genérico $codigoEsperado esperado para datos de otro estudiante. Incumple HU 1.2.2 CA3.")
		}
	}

	WebUI.comment('Y cuando abre directamente la URL del reporte del Estudiante B')
	String urlAjena = Configuracion.url(GlobalVariable.RUTA_RESULTADOS_ESTUDIANTE.toString().replace(MARCADOR_ID, idEstudianteB))
	Evidencia.registrar(CASO, "Solicitud de vista ajena: $urlAjena")
	WebUI.navigateToUrl(urlAjena)
	WebUI.waitForPageLoad(Configuracion.timeoutLargo())
	WebUI.delay(2)
	Evidencia.capturar(CASO, '02_solicitud_resultados_estudiante_B')
	Evidencia.registrar(CASO, "URL final tras la solicitud: ${WebUI.getUrl()}")

	WebUI.comment('Entonces no se expone ningún dato del Estudiante B')
	if (!WebUI.verifyElementNotPresent(findTestObject(CNT_DETALLE), Configuracion.timeoutCorto(), FailureHandling.OPTIONAL)) {
		KeywordUtil.markFailed("[$CASO] DEFECTO DE SEGURIDAD: se renderiza el reporte académico del Estudiante B para el Estudiante A. Incumple HU 1.2.2 CA3.")
	}
	Evidencia.registrar(CASO, 'Verificaciones de aislamiento finalizadas (ver estado del caso en el reporte)')
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
