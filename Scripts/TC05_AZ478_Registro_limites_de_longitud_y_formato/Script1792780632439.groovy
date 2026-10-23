import static com.kms.katalon.core.testdata.TestDataFactory.findTestData
import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.testdata.TestData as TestData
import com.kms.katalon.core.util.KeywordUtil as KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import itmentorsoft.Configuracion
import itmentorsoft.Evidencia
import itmentorsoft.Pagina

/*
 * TC05 - Límites de longitud y expresiones regulares del formulario de registro
 * Azure Test Plans #478 | HU 1.1.1 Registro de usuario
 * Dirigido por datos (Data Files/TD_TC05_Registro): una fila por regla de validación. {xN} = N caracteres 'x'.
 *
 * PASA si:  cada dato inválido muestra su mensaje de error específico, las reglas de complejidad de la contraseña
 *           se marcan según el valor escrito, la confirmación distinta se rechaza y el formulario inválido no se envía.
 * FALLA si: falta un mensaje, aparece uno distinto, o el registro se envía con datos inválidos.
 * No crea cuentas: el formulario nunca se envía con datos válidos.
 */
final String CASO = 'TC05'
final String RUTA_REGISTRO = '/register'
final String ENVIAR = 'Comun/btn_EnviarFormulario'
final String REGLAS = 'Autenticacion/Page_Registro/lst_ReglasPassword'
final String REGLAS_CUMPLIDAS = 'Autenticacion/Page_Registro/lst_ReglasPasswordCumplidas'

TestData datos = findTestData('Data Files/TD_TC05_Registro')
List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL'])
pendientes += Configuracion.objetosPendientes(['Comun/txt_Campo', 'Comun/lbl_ErrorCampo', ENVIAR, REGLAS, REGLAS_CUMPLIDAS])
pendientes += Configuracion.datosPendientes(datos, ['campo', 'error_esperado'])
Configuracion.detenerSiHayPendientes(CASO, pendientes)

def expandir = { String valor ->
	def m = (valor ?: '') =~ /^\{x(\d+)\}$/
	return m.matches() ? 'x' * (m.group(1) as int) : (valor ?: '')
}

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que un visitante abre el formulario de registro')
	Pagina.abrir(RUTA_REGISTRO)

	WebUI.comment('Cuando ingresa datos que violan las restricciones de longitud o los patrones definidos')
	(1..datos.getRowNumbers()).each { fila ->
		String campo = datos.getValue('campo', fila)
		String valor = expandir(datos.getValue('valor', fila))
		String esperado = datos.getValue('error_esperado', fila)
		Pagina.escribir(campo, valor)
		String obtenido = Pagina.errorDe(campo)
		Evidencia.registrar(CASO, "${datos.getValue('descripcion', fila)}: campo '$campo' (${valor.length()} car.) -> '$obtenido'")
		if (obtenido != esperado) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: ${datos.getValue('descripcion', fila)}: se esperaba '$esperado' y se obtuvo '$obtenido'. Incumple #478.")
		}
	}
	Evidencia.capturar(CASO, '01_errores_por_campo')

	WebUI.comment('Y escribe contraseñas que no cumplen la complejidad exigida')
	[['abc', 1], ['abcdef', 2], ['abcdef1', 3], ['Abcdef1!', 4], ['Abcdefghijk1!xyzw1234', 3]].each { prueba ->
		Pagina.escribir('password', prueba[0])
		int total = Pagina.contar(REGLAS)
		int cumplidas = Pagina.contar(REGLAS_CUMPLIDAS)
		Evidencia.registrar(CASO, "Contraseña de ${prueba[0].length()} caracteres -> reglas cumplidas $cumplidas de $total")
		if (total != 4 || cumplidas != prueba[1]) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: para una contraseña de ${prueba[0].length()} caracteres se esperaban ${prueba[1]} de 4 reglas cumplidas y se obtuvo $cumplidas de $total.")
		}
	}
	Pagina.escribir('password', 'abc')
	Evidencia.capturar(CASO, '02_reglas_contrasena')

	WebUI.comment('Y la confirmación no coincide con la contraseña')
	Pagina.escribir('password', 'Abcdef1!')
	Pagina.escribir('confirmPassword', 'Abcdef1?')
	String mismatch = Pagina.errorDe('confirmPassword')
	if (mismatch != 'Las contraseñas no coinciden') {
		KeywordUtil.markFailed("[$CASO] DEFECTO: la confirmación distinta no muestra 'Las contraseñas no coinciden' (obtenido: '$mismatch').")
	}

	WebUI.comment('Y hace clic en el botón de registro con el formulario inválido')
	Pagina.escribir('name', 'Ab')
	Pagina.escribir('username', 'usuario con espacios')
	Pagina.escribir('email', 'correo@')
	WebUI.click(findTestObject(ENVIAR))
	String toast = Pagina.esperarToast(4)
	Evidencia.capturar(CASO, '03_envio_bloqueado')
	Evidencia.registrar(CASO, "Envío con datos inválidos -> ruta ${Pagina.rutaActual()}, notificación: '$toast'")

	WebUI.comment('Entonces el sistema detiene la transacción y mantiene los mensajes de error por campo')
	if (Pagina.rutaActual() != RUTA_REGISTRO || (toast && toast.startsWith('Cuenta creada'))) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el registro se envió con datos inválidos (ruta ${Pagina.rutaActual()}, notificación '$toast').")
	}
	['name', 'username', 'email', 'confirmPassword'].each { campo ->
		if (!Pagina.errorDe(campo)) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: tras el envío inválido el campo '$campo' no muestra su error.")
		}
	}
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
