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
 * TC02 - Restricción de acceso por rol al módulo de administración de evaluaciones técnicas
 * Azure Test Plans #466 | HU 2.1.3 Creación de rúbricas (#224) CA1 | HU 1.2.1 (#193) CA4 | HH 1.2.1 (#360) CA1 | CR-008
 * Riesgo RH02 / SQA R03 + R01: un Estudiante accede al banco de preguntas/rúbricas que alimentan la calificación con LLM.
 *
 * En ITMentorSoft el módulo es "Preguntas" del Docente (RUTA_ADMIN_EVALUACIONES) y su API (API_BANCO_PREGUNTAS).
 * Dirigido por datos (Data Files/TD_TC02_Roles): una iteración por rol.
 * PASA si:  ESTUDIANTE -> la interfaz no muestra el módulo y la API rechaza la petición (401/403/404);
 *           DOCENTE -> lista de preguntas visible (control positivo).
 * FALLA si: un rol no autorizado ve el módulo o recibe datos de la API, un rol autorizado es bloqueado,
 *           o (si se configuró mensaje_denegacion_esperado) no aparece el mensaje de autorización.
 * ERROR si: falta configuración o falla el login de precondición.
 */
final String CASO = 'TC02'
final String CNT_MODULO = 'Administracion_Evaluaciones/Page_AdminEvaluaciones/cnt_ModuloPreguntas'
final String TBL_TEMAS = 'Administracion_Evaluaciones/Page_AdminEvaluaciones/tbl_ListaTemas'
final List<Integer> CODIGOS_DENEGACION = [401, 403, 404]

TestData datos = findTestData('Data Files/TD_TC02_Roles')

// ---- 0. Configuración requerida
List<String> pendientes = []
pendientes += Configuracion.variablesPendientes(['BASE_URL', 'RUTA_ADMIN_EVALUACIONES', 'API_BANCO_PREGUNTAS'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS + [CNT_MODULO, TBL_TEMAS])
pendientes += Configuracion.datosPendientes(datos, ['rol', 'acceso_esperado'])
(1..datos.getRowNumbers()).each { fila ->
	pendientes += Autenticacion.pendientesDeCuenta(datos.getValue('rol', fila))
}
Configuracion.detenerSiHayPendientes(CASO, pendientes)

String rutaModulo = GlobalVariable.RUTA_ADMIN_EVALUACIONES.toString()

(1..datos.getRowNumbers()).each { fila ->
	String rol = datos.getValue('rol', fila)
	String accesoEsperado = datos.getValue('acceso_esperado', fila)
	String mensajeDenegacion = datos.getValue('mensaje_denegacion_esperado', fila)?.trim()

	WebUI.openBrowser('')
	WebUI.maximizeWindow()
	try {
		WebUI.comment("Iteración $fila - Dado un usuario autenticado con rol $rol")
		Autenticacion.iniciarSesion(rol, CASO)

		WebUI.comment('Cuando accede directamente a la URL del módulo de administración de evaluaciones técnicas')
		WebUI.navigateToUrl(Configuracion.url(rutaModulo))
		WebUI.waitForPageLoad(Configuracion.timeoutLargo())

		WebUI.comment('Y cuando consulta directamente la API del banco de preguntas')
		Map<String, Object> api = Api.get(GlobalVariable.API_BANCO_PREGUNTAS.toString())
		Evidencia.registrar(CASO, "Rol $rol - API banco de preguntas: ${Api.resumen(api)}")

		if (accesoEsperado == 'PERMITIDO') {
			WebUI.comment("Entonces el rol $rol visualiza la lista de preguntas (HU 2.1.3 CA1)")
			boolean listaVisible = WebUI.waitForElementVisible(findTestObject(TBL_TEMAS), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)
			Evidencia.capturar(CASO, "${fila}_${rol}_acceso_permitido")
			if (!listaVisible) {
				KeywordUtil.markFailed("[$CASO] DEFECTO: el rol $rol, autorizado por HU 2.1.3 CA1, no visualiza la lista de preguntas del módulo de administración de evaluaciones (URL: ${WebUI.getUrl()}).")
			}
			if (!Api.esExito(api.status as int)) {
				KeywordUtil.markWarning("[$CASO] Control positivo de API no concluyente: el rol $rol recibió HTTP ${api.status} de API_BANCO_PREGUNTAS. Revisar la ruta antes de interpretar el resultado del rol no autorizado.")
			}
		} else {
			WebUI.comment("Entonces al rol $rol se le deniega el acceso (HU 2.1.3 CA1, HU 1.2.1 CA4)")
			boolean mensajeMostrado = mensajeDenegacion ? Configuracion.esperarTexto(mensajeDenegacion, Configuracion.timeoutLargo()) : true
			// Se deja tiempo a la redirección del guard de Angular antes de evaluar la ausencia del módulo.
			WebUI.delay(2)
			Evidencia.capturar(CASO, "${fila}_${rol}_acceso_denegado")
			String urlFinal = WebUI.getUrl()
			Evidencia.registrar(CASO, "Rol $rol - URL final al pedir el módulo: $urlFinal")

			if (!WebUI.verifyElementNotPresent(findTestObject(CNT_MODULO), Configuracion.timeoutCorto(), FailureHandling.OPTIONAL)) {
				KeywordUtil.markFailed("[$CASO] DEFECTO DE SEGURIDAD: el rol $rol accede al módulo de administración de evaluaciones. Incumple HU 2.1.3 CA1 y HH 1.2.1.")
			}
			if (Api.esExito(api.status as int)) {
				KeywordUtil.markFailed("[$CASO] DEFECTO DE SEGURIDAD: la API del banco de preguntas entrega datos al rol $rol (HTTP ${api.status}). El control de acceso solo existe en la interfaz. Incumple HU 2.1.3 CA1 y CR-008.")
			} else if (!(api.status in CODIGOS_DENEGACION)) {
				KeywordUtil.markWarning("[$CASO] Resultado de API no concluyente para el rol $rol: HTTP ${api.status} (se esperaba 401, 403 o 404).")
			}
			if (!mensajeMostrado) {
				KeywordUtil.markFailed("[$CASO] DEFECTO: al rol $rol no se le mostró el mensaje de autorización esperado ('$mensajeDenegacion').")
			} else if (!mensajeDenegacion) {
				KeywordUtil.markWarning("[$CASO] Observación HU 2.1.3 CA1: no se verificó el mensaje de autorización (columna mensaje_denegacion_esperado vacía). " +
						"La aplicación redirige sin mensaje; URL final: $urlFinal.")
			}
		}
		Autenticacion.cerrarSesion(CASO)
	} finally {
		WebUI.closeBrowser(FailureHandling.OPTIONAL)
	}
}
Evidencia.registrar(CASO, 'Iteraciones por rol finalizadas (ver estado del caso en el reporte)')
