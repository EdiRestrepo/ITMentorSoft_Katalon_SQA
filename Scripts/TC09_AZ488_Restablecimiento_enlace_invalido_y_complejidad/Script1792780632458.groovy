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
 * TC09 - Validación de enlace vencido o inválido y requisitos de contraseña segura en el restablecimiento
 * Azure Test Plans #488 | HU 1.1.3 Recuperación de contraseña
 *
 * PASA si:  (1) el enlace sin token muestra "Enlace inválido" y no permite cambiar la contraseña;
 *           (2) la nueva contraseña exige 6-20 caracteres, letra, número y carácter especial, y la confirmación igual;
 *           (3) con un token falso, y con un enlace real vencido (columna enlace_vencido, opcional), el backend
 *               rechaza el cambio y no muestra "Contraseña actualizada".
 * FALLA si: el enlace inválido permite el cambio, falta una validación de complejidad o un token inválido/vencido se acepta.
 * Para el enlace real se envía la contraseña actual (ITM_PWD_ESTUDIANTE): aunque el token siguiera vigente no cambia nada.
 */
final String CASO = 'TC09'
final String RUTA = '/reset-password'
final String ENVIAR = 'Comun/btn_EnviarFormulario'
final String TITULO = 'Autenticacion/Page_Restablecer/lbl_Titulo'
final String MENSAJE = 'Autenticacion/Page_Restablecer/lbl_Mensaje'
final int VIGENCIA_ENLACE_S = 5 * 60

TestData datos = findTestData('Data Files/TD_TC09_Restablecer')
List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL'])
pendientes += Configuracion.objetosPendientes(['Comun/txt_Campo', 'Comun/lbl_ErrorCampo', ENVIAR, TITULO, MENSAJE])
Configuracion.detenerSiHayPendientes(CASO, pendientes)

/** Escribe la nueva contraseña y su confirmación, envía y devuelve la respuesta mostrada. */
def enviarCambio = { String clave, String paso ->
	Pagina.escribir('newPassword', clave)
	Pagina.escribir('confirmPassword', clave)
	WebUI.click(findTestObject(ENVIAR))
	String toast = Pagina.esperarToast(Configuracion.timeoutLargo())
	WebUI.delay(1)
	String titulo = Pagina.texto(TITULO, null, 2)
	Evidencia.capturar(CASO, paso)
	Evidencia.registrar(CASO, "$paso -> notificación '$toast', título '$titulo'")
	return [toast: toast, titulo: titulo]
}

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	WebUI.comment('Dado que el usuario accede a un enlace de recuperación sin token')
	Pagina.abrir(RUTA)
	String tituloSinToken = Pagina.texto(TITULO)
	Evidencia.capturar(CASO, '01_enlace_sin_token')
	Evidencia.registrar(CASO, "Enlace sin token -> '$tituloSinToken': ${Pagina.texto(MENSAJE)}")
	if (tituloSinToken != 'Enlace inválido' || Pagina.visible('Comun/txt_Campo', [campo: 'newPassword'], 2)) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: un enlace sin token no se rechaza (título '$tituloSinToken'). Incumple #488.")
	}

	WebUI.comment('Cuando abre un enlace con un token que el sistema no emitió')
	Pagina.abrir("$RUTA?token=tokeninvalidoqa${System.currentTimeMillis()}&trx=trxinvalidoqa")
	if (!Pagina.visible('Comun/txt_Campo', [campo: 'newPassword'], Configuracion.timeoutLargo())) {
		KeywordUtil.markErrorAndStop("[$CASO] Precondición no cumplida: con token y trx en la URL no aparece el formulario de nueva contraseña.")
	}

	WebUI.comment('Y escribe contraseñas que no cumplen la complejidad exigida')
	[['abc12!', null], ['ab1!', 'Debe tener al menos 6 caracteres'], ['abcdefg1!abcdefg1!abc', 'No puede superar los 20 caracteres'],
		['abcdefgh!', 'Debe incluir al menos un número'], ['12345678!', 'Debe incluir al menos una letra'], ['abcdefg12', null]].each { prueba ->
		Pagina.escribir('newPassword', prueba[0])
		String obtenido = Pagina.errorDe('newPassword')
		Evidencia.registrar(CASO, "Nueva contraseña de ${prueba[0].length()} caracteres -> '$obtenido'")
		if (prueba[1] && obtenido != prueba[1]) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: se esperaba '${prueba[1]}' para una contraseña de ${prueba[0].length()} caracteres y se obtuvo '$obtenido'.")
		}
		if (prueba[0] == 'abcdefg12' && !obtenido) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: una contraseña sin carácter especial se acepta como válida.")
		}
	}
	Pagina.escribir('newPassword', 'Katalon2026*')
	Pagina.escribir('confirmPassword', 'Katalon2026?')
	String mismatch = Pagina.errorDe('confirmPassword')
	Evidencia.capturar(CASO, '02_validaciones_de_complejidad')
	if (mismatch != 'Las contraseñas no coinciden') {
		KeywordUtil.markFailed("[$CASO] DEFECTO: la confirmación distinta no muestra 'Las contraseñas no coinciden' (obtenido: '$mismatch').")
	}

	WebUI.comment('Y envía una contraseña válida con el token inválido')
	Map falso = enviarCambio('Katalon2026*', '03_token_invalido')

	WebUI.comment('Entonces el sistema rechaza el cambio')
	if (falso.titulo == 'Contraseña actualizada' || falso.toast?.startsWith('Contraseña actualizada')) {
		KeywordUtil.markFailed("[$CASO] DEFECTO DE SEGURIDAD: el backend aceptó el cambio de contraseña con un token inválido. Incumple #488.")
	} else if (!falso.toast) {
		KeywordUtil.markFailed("[$CASO] DEFECTO: el cambio con token inválido no muestra ningún mensaje de error.")
	}

	String enlaceVencido = datos.getValue('enlace_vencido', 1)?.trim()
	if (enlaceVencido) {
		WebUI.comment('Y con un enlace real de recuperación que superó los 5 minutos de vigencia')
		// El oráculo solo es válido si el enlace tiene más de 5 minutos: si aún no los cumple, se espera.
		Date emitido = Date.parse('yyyy-MM-dd HH:mm:ss', datos.getValue('emitido', 1).trim())
		long faltan = emitido.time + (VIGENCIA_ENLACE_S + 30) * 1000L - System.currentTimeMillis()
		if (faltan > 0) {
			Evidencia.registrar(CASO, "El enlace tiene menos de ${VIGENCIA_ENLACE_S + 30} s de emitido: se esperan ${(faltan / 1000) as int} s antes de usarlo")
			WebUI.delay(((faltan / 1000) as int) + 1)
		}
		Pagina.abrir(enlaceVencido.replaceFirst('^https?://[^/]+', ''))
		Map vencido = enviarCambio(System.getenv('ITM_PWD_ESTUDIANTE'), '04_enlace_real_vencido')
		Evidencia.registrar(CASO, "Enlace real emitido a las ${datos.getValue('emitido', 1)} y usado a las ${new Date().format('HH:mm:ss')} " +
				"(${((System.currentTimeMillis() - emitido.time) / 1000) as int} s después de emitido)")
		if (vencido.titulo == 'Contraseña actualizada' || vencido.toast?.startsWith('Contraseña actualizada')) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: un enlace de recuperación con más de 5 minutos de emitido sigue siendo válido. Incumple #486/#488.")
		}
	} else {
		KeywordUtil.markWarning("[$CASO] No se verificó un enlace real vencido: la columna enlace_vencido de TD_TC09_Restablecer está vacía.")
	}
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
