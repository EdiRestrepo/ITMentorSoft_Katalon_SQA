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
 * TC01 - Aislamiento de datos académicos entre estudiantes
 * Azure Test Plans #496 | HU 1.2.2 Acceso y privacidad en datos (#198) CA2 y CA3 | HH 1.2.1 (#360) CA2
 * Riesgo RH01 / SQA R03: un estudiante obtiene resultados académicos de otro estudiante (IDOR).
 *
 * PASA si:  (1) el Estudiante A ve sus propios resultados sin datos de B y
 *           (2) al pedir la URL de resultados de B ve el error 404 genérico y ningún dato de B.
 * FALLA si: se muestra cualquier dato de B, o no aparece el mensaje 404 genérico.
 * ERROR si: falta configuración (URL, rutas, localizadores, datos o cuentas) o falla el login de precondición.
 */
final String CASO = 'TC01'
final String CNT_RESULTADOS = 'Privacidad_Datos/Page_ResultadosAcademicos/cnt_ResultadosAcademicos'
final String MARCADOR_ID = '{idEstudiante}'

TestData datos = findTestData('Data Files/TD_TC01_Aislamiento')

// ---- 0. Configuración requerida (sin ella el caso termina en ERROR, nunca en PASSED)
List<String> pendientes = []
pendientes += Configuracion.variablesPendientes(['BASE_URL', 'RUTA_RESULTADOS_PROPIOS', 'RUTA_RESULTADOS_ESTUDIANTE'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS + [CNT_RESULTADOS])
pendientes += Autenticacion.pendientesDeCuenta('ESTUDIANTE_A')
pendientes += Configuracion.datosPendientes(datos, ['id_estudiante_b', 'marcador_dato_estudiante_b', 'mensaje_404_esperado'])
if (!Configuracion.esPendiente(GlobalVariable.RUTA_RESULTADOS_ESTUDIANTE) && !GlobalVariable.RUTA_RESULTADOS_ESTUDIANTE.toString().contains(MARCADOR_ID)) {
	pendientes << "Perfil > GlobalVariable.RUTA_RESULTADOS_ESTUDIANTE debe contener el marcador $MARCADOR_ID"
}
Configuracion.detenerSiHayPendientes(CASO, pendientes)

String idEstudianteB = datos.getValue('id_estudiante_b', 1)
String marcadorEstudianteB = datos.getValue('marcador_dato_estudiante_b', 1)
String mensaje404 = datos.getValue('mensaje_404_esperado', 1)

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que el Estudiante A ha iniciado sesión')
	Autenticacion.iniciarSesion('ESTUDIANTE_A', CASO)

	WebUI.comment('Cuando consulta sus propios resultados académicos (HU 1.2.2 CA2)')
	WebUI.navigateToUrl(Configuracion.url(GlobalVariable.RUTA_RESULTADOS_PROPIOS))
	WebUI.waitForPageLoad(Configuracion.timeoutLargo())
	boolean propiosVisibles = WebUI.waitForElementVisible(findTestObject(CNT_RESULTADOS), Configuracion.timeoutLargo(), FailureHandling.OPTIONAL)
	Evidencia.capturar(CASO, '01_resultados_propios')
	if (!propiosVisibles) {
		// Sin este control positivo, las verificaciones negativas posteriores podrían pasar sobre una página vacía.
		KeywordUtil.markErrorAndStop("[$CASO] Precondición no cumplida: el Estudiante A no visualiza sus propios resultados. Verificar que tenga al menos una evaluación registrada y el localizador $CNT_RESULTADOS.")
	}

	WebUI.comment('Entonces solo ve sus datos: ningún dato del Estudiante B (HU 1.2.2 CA2)')
	if (!WebUI.verifyTextNotPresent(marcadorEstudianteB, false, FailureHandling.OPTIONAL)) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: la vista de resultados propios del Estudiante A muestra un dato del Estudiante B ('$marcadorEstudianteB'). Incumple HU 1.2.2 CA2.")
	}

	WebUI.comment('Y cuando solicita directamente la URL de resultados del Estudiante B (HU 1.2.2 CA3)')
	String urlAjena = Configuracion.url(GlobalVariable.RUTA_RESULTADOS_ESTUDIANTE.toString().replace(MARCADOR_ID, idEstudianteB))
	Evidencia.registrar(CASO, "Solicitud de datos ajenos: $urlAjena")
	WebUI.navigateToUrl(urlAjena)
	WebUI.waitForPageLoad(Configuracion.timeoutLargo())
	boolean mensajeMostrado = Configuracion.esperarTexto(mensaje404, Configuracion.timeoutLargo())
	Evidencia.capturar(CASO, '02_solicitud_resultados_estudiante_B')

	WebUI.comment('Entonces se muestra el 404 genérico "usuario no existe" y no se expone ningún dato del Estudiante B')
	if (!mensajeMostrado) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: al solicitar los resultados del Estudiante B no se mostró el mensaje 404 genérico esperado ('$mensaje404'). Incumple HU 1.2.2 CA3.")
	}
	if (!WebUI.verifyTextNotPresent(marcadorEstudianteB, false, FailureHandling.OPTIONAL)) {
		KeywordUtil.markFailed("[$CASO] DEFECTO DE SEGURIDAD: se expone un dato del Estudiante B ('$marcadorEstudianteB') a otro estudiante. Incumple HU 1.2.2 CA2/CA3.")
	}
	if (!WebUI.verifyElementNotPresent(findTestObject(CNT_RESULTADOS), Configuracion.timeoutCorto(), FailureHandling.OPTIONAL)) {
		KeywordUtil.markFailed("[$CASO] DEFECTO DE SEGURIDAD: se renderiza el contenedor de resultados académicos para un estudiante ajeno. Incumple HU 1.2.2 CA3.")
	}
	Evidencia.registrar(CASO, 'Verificaciones de aislamiento finalizadas (ver estado del caso en el reporte)')
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
