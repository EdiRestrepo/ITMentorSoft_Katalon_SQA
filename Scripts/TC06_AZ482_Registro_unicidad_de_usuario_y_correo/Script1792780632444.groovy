import static com.kms.katalon.core.testdata.TestDataFactory.findTestData
import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.testdata.TestData as TestData
import com.kms.katalon.core.util.KeywordUtil as KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import internal.GlobalVariable as GlobalVariable
import itmentorsoft.Configuracion
import itmentorsoft.Evidencia
import itmentorsoft.Pagina

/*
 * TC06 - Unicidad de usuario y correo en el registro
 * Azure Test Plans #482 | HU 1.1.1 Registro de usuario
 *
 * Envía el registro con datos de formato válido usando el usuario y el correo de una cuenta que ya existe
 * (Data Files/TD_TC06_Unicidad: usuario existente; el correo es USUARIO_ESTUDIANTE). Ambos datos están
 * duplicados, así que un sistema correcto nunca crea la cuenta: la prueba no deja datos nuevos.
 * PASA si:  el registro se rechaza con un mensaje de dato ya registrado y la vista permanece en /register.
 * FALLA si: se muestra "Cuenta creada" o se redirige al login como registro exitoso.
 * Alcance: el cifrado bcrypt/Argon2 de la contraseña (#482) no es observable desde la interfaz.
 */
final String CASO = 'TC06'
final String RUTA_REGISTRO = '/register'
final String ENVIAR = 'Comun/btn_EnviarFormulario'

TestData datos = findTestData('Data Files/TD_TC06_Unicidad')
List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL', 'USUARIO_ESTUDIANTE'])
pendientes += Configuracion.objetosPendientes(['Comun/txt_Campo', ENVIAR, 'Comun/lbl_ToastTitulo'])
pendientes += Configuracion.datosPendientes(datos, ['usuario_existente', 'nombre', 'password'])
Configuracion.detenerSiHayPendientes(CASO, pendientes)

String usuario = datos.getValue('usuario_existente', 1)
String correo = GlobalVariable.USUARIO_ESTUDIANTE.toString()
String clave = datos.getValue('password', 1)

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que el visitante completa el registro con datos de formato válido y contraseñas coincidentes')
	Pagina.abrir(RUTA_REGISTRO)
	Pagina.escribir('name', datos.getValue('nombre', 1))
	Pagina.escribir('username', usuario)
	Pagina.escribir('email', correo)
	Pagina.escribir('password', clave)
	Pagina.escribir('confirmPassword', clave)
	['name', 'username', 'email', 'password', 'confirmPassword'].each { campo ->
		String error = Pagina.errorDe(campo)
		if (error) {
			KeywordUtil.markErrorAndStop("[$CASO] Precondición no cumplida: el dato del campo '$campo' no es de formato válido ('$error'). Revisar TD_TC06_Unicidad.")
		}
	}
	Evidencia.capturar(CASO, '01_formulario_con_datos_existentes')

	WebUI.comment('Cuando el sistema verifica si el usuario o el correo ya están registrados')
	WebUI.click(findTestObject(ENVIAR))
	String respuesta = Pagina.esperarToast(Configuracion.timeoutLargo())
	WebUI.delay(3)
	Evidencia.capturar(CASO, '02_respuesta_registro_duplicado')
	Evidencia.registrar(CASO, "Registro con usuario '$usuario' y correo existentes -> '$respuesta' (ruta ${Pagina.rutaActual()})")

	WebUI.comment('Entonces rechaza el registro indicando que el dato ya está en uso')
	if (respuesta?.startsWith('Cuenta creada') || Pagina.rutaActual() != RUTA_REGISTRO) {
		KeywordUtil.markFailed("[$CASO] DEFECTO CRÍTICO: el sistema aceptó un registro con usuario y correo ya existentes ('$respuesta'). Incumple #482. Revisar y eliminar la cuenta duplicada.")
	} else if (!respuesta) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el registro duplicado no mostró ningún mensaje de rechazo.")
	} else if (!(respuesta =~ /(?i)already|in use|registrad|existe|en uso/)) {
		KeywordUtil.markWarning("[$CASO] Observación #482: el rechazo no indica claramente que el dato ya está en uso ('$respuesta'). Azure espera '___ already in use'.")
	}
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
