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
 * TC02 - Restricción de acceso por rol al módulo de administración de evaluaciones técnicas
 * Azure Test Plans #466 | HU 2.1.3 Creación de rúbricas (#224) CA1 | HU 1.2.1 (#193) CA4 | HH 1.2.1 (#360) CA1 | CR-008
 * Riesgo RH02 / SQA R03 + R01: un Estudiante accede al banco de preguntas/rúbricas que alimentan la calificación con LLM.
 *
 * Dirigido por datos (Data Files/TD_TC02_Roles): una iteración por rol.
 * PASA si:  ESTUDIANTE -> acceso denegado con mensaje de autorización y sin lista de temas;
 *           DOCENTE y ADMINISTRADOR -> lista de temas visible (controles positivos).
 * FALLA si: un rol no autorizado ve la lista de temas, no aparece el mensaje, o un rol autorizado es bloqueado.
 * ERROR si: falta configuración o falla el login de precondición.
 */
final String CASO = 'TC02'
final String TBL_TEMAS = 'Administracion_Evaluaciones/Page_AdminEvaluaciones/tbl_ListaTemas'

TestData datos = findTestData('Data Files/TD_TC02_Roles')

// ---- 0. Configuración requerida
List<String> pendientes = []
pendientes += Configuracion.variablesPendientes(['BASE_URL', 'RUTA_ADMIN_EVALUACIONES'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS + [TBL_TEMAS])
pendientes += Configuracion.datosPendientes(datos, ['rol', 'acceso_esperado'])
(1..datos.getRowNumbers()).each { fila ->
	pendientes += Autenticacion.pendientesDeCuenta(datos.getValue('rol', fila))
	if (datos.getValue('acceso_esperado', fila) == 'DENEGADO' && Configuracion.esPendiente(datos.getValue('mensaje_denegacion_esperado', fila))) {
		pendientes << "Data Files > TD_TC02_Roles fila $fila columna 'mensaje_denegacion_esperado'"
	}
}
Configuracion.detenerSiHayPendientes(CASO, pendientes)

(1..datos.getRowNumbers()).each { fila ->
	String rol = datos.getValue('rol', fila)
	String accesoEsperado = datos.getValue('acceso_esperado', fila)
	String mensajeDenegacion = datos.getValue('mensaje_denegacion_esperado', fila)

	WebUI.openBrowser('')
	WebUI.maximizeWindow()
	try {
		WebUI.comment("Iteración $fila - Dado un usuario autenticado con rol $rol")
		Autenticacion.iniciarSesion(rol, CASO)

		WebUI.comment('Cuando accede directamente a la URL del módulo de administración de evaluaciones técnicas')
		WebUI.navigateToUrl(Configuracion.url(GlobalVariable.RUTA_ADMIN_EVALUACIONES))
		WebUI.waitForPageLoad(Configuracion.timeoutLargo())

		if (accesoEsperado == 'PERMITIDO') {
			WebUI.comment("Entonces el rol $rol visualiza la lista de temas (HU 2.1.3 CA1)")
			boolean listaVisible = WebUI.waitForElementVisible(findTestObject(TBL_TEMAS), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)
			Evidencia.capturar(CASO, "${fila}_${rol}_acceso_permitido")
			if (!listaVisible) {
				KeywordUtil.markFailed("[$CASO] DEFECTO: el rol $rol, autorizado por HU 2.1.3 CA1, no visualiza la lista de temas del módulo de administración de evaluaciones.")
			}
		} else {
			WebUI.comment("Entonces al rol $rol se le deniega el acceso con mensaje de autorización (HU 2.1.3 CA1, HU 1.2.1 CA4)")
			// Primero se espera la señal positiva de denegación; así la ausencia de la lista no se evalúa sobre una página a medio cargar.
			boolean mensajeMostrado = Configuracion.esperarTexto(mensajeDenegacion, Configuracion.timeoutLargo())
			Evidencia.capturar(CASO, "${fila}_${rol}_acceso_denegado")
			if (!mensajeMostrado) {
				KeywordUtil.markFailed("[$CASO] DEFECTO: al rol $rol no se le mostró el mensaje de autorización esperado ('$mensajeDenegacion').")
			}
			if (!WebUI.verifyElementNotPresent(findTestObject(TBL_TEMAS), Configuracion.timeoutCorto(), FailureHandling.OPTIONAL)) {
				KeywordUtil.markFailed("[$CASO] DEFECTO DE SEGURIDAD: el rol $rol accede a la lista de temas del módulo de administración de evaluaciones. Incumple HU 2.1.3 CA1 y HH 1.2.1.")
			}
		}
		Autenticacion.cerrarSesion(CASO)
	} finally {
		WebUI.closeBrowser(FailureHandling.OPTIONAL)
	}
}
Evidencia.registrar(CASO, 'Iteraciones por rol finalizadas (ver estado del caso en el reporte)')
