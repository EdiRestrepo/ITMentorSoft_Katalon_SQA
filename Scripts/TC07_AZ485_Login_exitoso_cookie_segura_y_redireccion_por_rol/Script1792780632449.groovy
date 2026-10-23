import org.openqa.selenium.Cookie

import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.util.KeywordUtil as KeywordUtil
import com.kms.katalon.core.webui.driver.DriverFactory
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import itmentorsoft.Api
import itmentorsoft.Autenticacion
import itmentorsoft.Configuracion
import itmentorsoft.Evidencia
import itmentorsoft.Pagina

/*
 * TC07 - Inicio de sesión exitoso: token de acceso en cookie segura y redirección según el rol
 * Azure Test Plans #485 | HU 1.1.2 Inicio de sesión (CA5)
 * Hace un login completo (con código OTP) por cada rol, sin reutilizar sesiones guardadas.
 *
 * PASA si:  por cada rol (1) la aplicación redirige a su panel (/student/dashboard, /teacher/dashboard);
 *           (2) el token de acceso viaja en una cookie con HttpOnly y Secure, no legible desde JavaScript;
 *           (3) el JWT vence a los 15 minutos (claim exp, tolerancia de 60 s).
 * FALLA si: la redirección no corresponde al rol, la cookie no es HttpOnly/Secure o la vigencia supera 15 minutos.
 * Observación: si el frontend guarda también una copia del JWT en sessionStorage se registra una advertencia.
 */
final String CASO = 'TC07'
final int VIGENCIA_SEGUNDOS = 15 * 60
final int TOLERANCIA_SEGUNDOS = 60
final List<String> CUENTAS = ['ESTUDIANTE', 'DOCENTE']

List<String> pendientes = Configuracion.variablesPendientes(['BASE_URL', 'RUTA_LOGIN'])
pendientes += Configuracion.objetosPendientes(Autenticacion.OBJETOS)
CUENTAS.each { pendientes += Autenticacion.pendientesDeCuenta(it) }
Configuracion.detenerSiHayPendientes(CASO, pendientes)

CUENTAS.each { cuenta ->
	WebUI.openBrowser('')
	WebUI.maximizeWindow()
	try {
		WebUI.comment("Dado que el usuario con rol $cuenta envía credenciales válidas y completa la verificación")
		Autenticacion.iniciarSesion(cuenta, CASO, true)
		long ahora = (System.currentTimeMillis() / 1000L) as long

		WebUI.comment('Entonces es redirigido a la pantalla de bienvenida de su rol')
		String esperado = Autenticacion.inicioDeCuenta(cuenta)
		Pagina.esperarRuta(esperado)
		Evidencia.capturar(CASO, "${cuenta}_bienvenida")
		if (Pagina.rutaActual() != esperado) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: el rol $cuenta fue redirigido a ${Pagina.rutaActual()} y no a $esperado. Incumple #485 (redirección según rol).")
		}

		WebUI.comment('Y el token de acceso se almacena en una cookie HttpOnly y Secure')
		Cookie acceso = DriverFactory.getWebDriver().manage().getCookieNamed('access_token')
		String cookiesVisibles = WebUI.executeJavaScript('return document.cookie', null)?.toString() ?: ''
		Evidencia.registrar(CASO, "Rol $cuenta - cookie access_token: ${acceso ? "HttpOnly=${acceso.httpOnly}, Secure=${acceso.secure}, dominio=${acceso.domain}" : 'NO EXISTE'}; " +
				"document.cookie expone access_token: ${cookiesVisibles.contains('access_token')}")
		if (!acceso) {
			KeywordUtil.markFailed("[$CASO] DEFECTO: tras el login del rol $cuenta no existe la cookie access_token. Incumple #485.")
		} else if (!acceso.httpOnly || !acceso.secure || cookiesVisibles.contains('access_token')) {
			KeywordUtil.markFailed("[$CASO] DEFECTO DE SEGURIDAD: la cookie access_token del rol $cuenta no es HttpOnly y Secure (HttpOnly=${acceso.httpOnly}, Secure=${acceso.secure}).")
		}

		WebUI.comment('Y el token tiene una vigencia de 15 minutos')
		Long exp = WebUI.executeJavaScript('''
			var t = arguments[0];
			if (!t || t.split('.').length !== 3) { return null; }
			var p = t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
			return JSON.parse(atob(p + '='.repeat((4 - p.length % 4) % 4))).exp || null;''', [acceso?.value]) as Long
		if (exp == null) {
			KeywordUtil.markWarning("[$CASO] No se pudo leer el claim exp del token de acceso del rol $cuenta (no es un JWT legible).")
		} else {
			long vigencia = exp - ahora
			Evidencia.registrar(CASO, "Rol $cuenta - vigencia restante del token: $vigencia s (esperado ≤ $VIGENCIA_SEGUNDOS s)")
			if (vigencia > VIGENCIA_SEGUNDOS + TOLERANCIA_SEGUNDOS || vigencia <= 0) {
				KeywordUtil.markFailed("[$CASO] DEFECTO: el token del rol $cuenta vence en $vigencia s; #485 exige una vigencia de 15 minutos.")
			}
		}
		if (Api.valorDeSesion('auth_token')) {
			KeywordUtil.markWarning("[$CASO] Observación de seguridad #485: además de la cookie HttpOnly, el frontend guarda una copia del JWT en sessionStorage ('auth_token'), accesible desde JavaScript.")
		}
	} finally {
		WebUI.closeBrowser(FailureHandling.OPTIONAL)
	}
}
