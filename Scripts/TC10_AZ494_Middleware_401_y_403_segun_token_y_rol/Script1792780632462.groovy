import static com.kms.katalon.core.testdata.TestDataFactory.findTestData

import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.testdata.TestData as TestData
import com.kms.katalon.core.util.KeywordUtil as KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import internal.GlobalVariable as GlobalVariable
import itmentorsoft.Api
import itmentorsoft.Autenticacion
import itmentorsoft.Configuracion
import itmentorsoft.Evidencia
import itmentorsoft.Pagina

/*
 * TC10 - Interceptación del middleware de autorización: HTTP 401 y HTTP 403 según token y permisos
 * Azure Test Plans #494 | HU 1.2.1 Control de acceso por roles. Complementa #489 (acceso denegado por permisos).
 * Dirigido por datos (Data Files/TD_TC10_Middleware): cada fila es una petición a un endpoint protegido
 * con un tipo de credencial (SIN_TOKEN, TOKEN_INVALIDO, ESTUDIANTE, DOCENTE) y el código esperado.
 *
 * PASA si:  cada petición devuelve el código esperado (401 sin token o con token inválido, 403 con un rol sin
 *           permiso, 2xx en los controles positivos) y las vistas de otro rol redirigen al login.
 * FALLA si: un endpoint protegido responde con datos a quien no tiene permiso, o confunde 401 con 403.
 */
final String CASO = 'TC10'

TestData datos = findTestData('Data Files/TD_TC10_Middleware')
List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL', 'RUTA_LOGIN'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS)
pendientes += Configuracion.datosPendientes(datos, ['credencial', 'endpoint', 'codigo_esperado'])
pendientes += Autenticacion.pendientesDeCuenta('ESTUDIANTE') + Autenticacion.pendientesDeCuenta('DOCENTE')
Configuracion.detenerSiHayPendientes(CASO, pendientes)

Map<String, List<Integer>> filasPorCredencial = [:].withDefault { [] }
(1..datos.getRowNumbers()).each { filasPorCredencial[datos.getValue('credencial', it)] << it }

def verificar = { int fila, Map<String, Object> respuesta ->
	String credencial = datos.getValue('credencial', fila)
	String endpoint = datos.getValue('endpoint', fila)
	String esperado = datos.getValue('codigo_esperado', fila)
	boolean ok = esperado == '2xx' ? Api.esExito(respuesta.status as int) : respuesta.status.toString() == esperado
	Evidencia.registrar(CASO, "[$credencial] GET $endpoint -> ${Api.resumen(respuesta)} (esperado $esperado)")
	if (!ok) {
		String gravedad = Api.esExito(respuesta.status as int) ? 'DEFECTO DE SEGURIDAD' : 'DEFECTO'
		KeywordUtil.markFailed("[$CASO] $gravedad: [$credencial] GET $endpoint respondió HTTP ${respuesta.status} y se esperaba $esperado. ${datos.getValue('descripcion', fila)} (#494).")
	}
}

def verificarVistaDenegada = { String cuenta, String ruta ->
	Pagina.abrir(ruta, 3)
	String final_ = Pagina.rutaActual()
	Evidencia.registrar(CASO, "[$cuenta] vista $ruta -> $final_")
	if (final_.startsWith(ruta.substring(0, ruta.indexOf('/', 1)))) {
		KeywordUtil.markFailed("[$CASO] DEFECTO DE SEGURIDAD: el rol $cuenta accedió a la vista $ruta, reservada a otro rol (#489).")
	}
}

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que se envían solicitudes HTTP a endpoints protegidos sin token o con un token inválido')
	Pagina.abrir(GlobalVariable.RUTA_LOGIN.toString())
	filasPorCredencial['SIN_TOKEN'].each { verificar(it, Api.getSinSesion(datos.getValue('endpoint', it), null)) }
	filasPorCredencial['TOKEN_INVALIDO'].each { verificar(it, Api.getSinSesion(datos.getValue('endpoint', it), 'eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYWRtaW4ifQ.firmaInvalidaQA')) }

	['ESTUDIANTE', 'DOCENTE'].each { cuenta ->
		WebUI.comment("Cuando el middleware valida los permisos del rol $cuenta")
		Autenticacion.iniciarSesion(cuenta, CASO)
		filasPorCredencial[cuenta].each { verificar(it, Api.get(datos.getValue('endpoint', it).replace('{idPropio}', Api.valorDeSesion('auth_user_id') ?: ''))) }
		WebUI.comment("Y el rol $cuenta intenta abrir vistas reservadas a otros roles")
		verificarVistaDenegada(cuenta, '/admin/dashboard')
		verificarVistaDenegada(cuenta, cuenta == 'ESTUDIANTE' ? '/teacher/dashboard' : '/student/dashboard')
		Evidencia.capturar(CASO, "${cuenta}_vista_denegada")
		Autenticacion.cerrarSesion(CASO)
	}
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
