import java.nio.file.Paths

import com.kms.katalon.core.configuration.RunConfiguration
import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

import internal.GlobalVariable as GlobalVariable
import itmentorsoft.Api
import itmentorsoft.Autenticacion
import itmentorsoft.Configuracion
import itmentorsoft.Evidencia

/*
 * TC00 - Herramienta de apoyo (no forma parte de las suites).
 * Recorre las páginas del rol indicado en la variable 'cuenta' y guarda en Evidencias/dom/<cuenta>/
 * el HTML renderizado, una captura y las respuestas de las APIs principales. Sirve para diseñar y
 * mantener los localizadores del Object Repository cuando cambia el frontend.
 */
final String CASO = 'TC00'
File carpeta = Paths.get(RunConfiguration.getProjectDir(), 'Evidencias', 'dom', cuenta).toFile()
carpeta.mkdirs()

def guardarPagina = { String nombre, String ruta ->
	if (ruta) {
		WebUI.navigateToUrl(Configuracion.url(ruta))
	}
	WebUI.waitForPageLoad(Configuracion.timeoutLargo())
	WebUI.delay(4)
	new File(carpeta, "${nombre}.html").setText(WebUI.executeJavaScript('return document.documentElement.outerHTML', null).toString(), 'UTF-8')
	WebUI.takeScreenshot(new File(carpeta, "${nombre}.png").absolutePath, FailureHandling.OPTIONAL)
	Evidencia.registrar(CASO, "Página $nombre guardada (${WebUI.getUrl()})")
}

def guardarApi = { String nombre, String ruta ->
	Map<String, Object> r = Api.get(ruta)
	String cuerpo = r.body.toString()
	new File(carpeta, "api_${nombre}.json").setText("HTTP ${r.status} $ruta\n${cuerpo.length() > 30000 ? cuerpo.substring(0, 30000) : cuerpo}", 'UTF-8')
}

WebUI.openBrowser('')
WebUI.maximizeWindow()
try {
	if (cuenta == 'PUBLICO') {
		[login: '/login', registro: '/register', recuperar: '/recover-password', restablecer: '/reset-password', restablecer_token: '/reset-password?token=abc&trx=def'].each { n, r -> guardarPagina(n, r) }
		return
	}
	Autenticacion.iniciarSesion(cuenta, CASO)
	String id = Api.valorDeSesion('auth_user_id')
	guardarApi('perfil', '/users/profile')
	if (cuenta.startsWith('ESTUDIANTE')) {
		[dashboard: '/student/dashboard', evaluaciones: '/student/assessments', ruta: '/student/route', explorar: '/student/explore',
			progreso: '/student/progress', perfil: '/student/profile'].each { n, r -> guardarPagina(n, r) }
		guardarApi('resumen', "/assessments/summary?student_id=$id&page=0&page_size=5")
		guardarApi('estado_calificacion', "/assessments/qualification-status?student_id=$id")
		guardarApi('rutas', "/content/recommended/learning-paths?student_id=$id")
		guardarApi('progreso_ruta', "/content/learning-path/progress?student_id=$id")
		guardarApi('temas', '/assessments/topics')
		guardarApi('categorias', '/assessments/categories')
		WebUI.navigateToUrl(Configuracion.url('/student/assessments'))
		WebUI.delay(4)
		if (WebUI.waitForElementPresent(com.kms.katalon.core.testobject.ObjectRepository.findTestObject('Evaluacion_Inicial/Page_EvaluacionInicial/btn_IniciarEvaluacion'), 5, FailureHandling.OPTIONAL)) {
			WebUI.executeJavaScript("var b=[...document.querySelectorAll('a,button')].find(e=>/Ver resultado/.test(e.textContent)); if(b) b.click();", null)
			guardarPagina('resultado', null)
		}
	} else {
		[dashboard: '/teacher/dashboard', reportes: '/teacher/reports', preguntas: '/teacher/questions', pregunta_nueva: '/teacher/questions/new',
			recursos: '/teacher/resources', perfil: '/teacher/profile'].each { n, r -> guardarPagina(n, r) }
		WebUI.navigateToUrl(Configuracion.url('/teacher/resources'))
		WebUI.delay(4)
		WebUI.executeJavaScript("var b=[...document.querySelectorAll('button')].find(e=>/Nuevo recurso/.test(e.textContent)); if(b) b.click();", null)
		guardarPagina('recurso_nuevo', null)
		guardarApi('estudiantes', '/reports/students?page=0&page_size=5')
		guardarApi('por_categoria', '/reports/students-by-category')
		guardarApi('resumen_categoria', '/reports/category_summary')
		guardarApi('preguntas', '/assessments/all-questions?page=0&page_size=25')
		guardarApi('calificaciones', '/content/ratings/all')
		guardarApi('temas', '/assessments/topics')
		guardarApi('categorias', '/assessments/categories')
		guardarApi('admin_usuarios', '/users/')
	}
} finally {
	WebUI.closeBrowser(FailureHandling.OPTIONAL)
}
