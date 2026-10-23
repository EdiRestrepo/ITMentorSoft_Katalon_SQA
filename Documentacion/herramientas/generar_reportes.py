"""Genera el informe de ejecución (.md) y el reporte HTML a partir de los resultados reales de Katalon.

Fuentes:
  Reports/**/execution0.log      log XML de cada ejecución de Katalon (estado, mensajes, advertencias)
  Evidencias/<TC>/*.png          capturas tomadas por el keyword Evidencia (carpeta no versionada)
  Evidencias/<TC>/bitacora.txt   bitácora de cada caso
  Documentacion/herramientas/ajustes.md   (opcional) ajustes hechos entre ejecuciones

Salidas:
  Documentacion/Evidencias_Ejecucion/<TC>/   copia versionada de las capturas de la última ejecución
  Documentacion/Informe_Ejecucion_Pruebas.md
  Documentacion/Reporte_Pruebas.html          autocontenido (capturas embebidas)

Uso: python -I Documentacion/herramientas/generar_reportes.py [ruta del proyecto]
"""
import base64
import html
import os
import re
import shutil
import sys
from datetime import datetime, timedelta

PROYECTO = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', '..'))
DOC = os.path.join(PROYECTO, 'Documentacion')
SALIDA_EVIDENCIAS = os.path.join(DOC, 'Evidencias_Ejecucion')

CASOS = [
    ('TC01', 'TC01_HU122_Aislamiento_datos_academicos_entre_estudiantes', 'Aislamiento de datos académicos entre estudiantes',
     'Azure #496 · HU 1.2.2 CA2, CA3 · Riesgo RH01 / R03'),
    ('TC02', 'TC02_HU213_Restriccion_acceso_por_rol_a_administracion_de_evaluaciones', 'Restricción de acceso por rol a la administración de evaluaciones',
     'Azure #466 · HU 2.1.3 CA1 · Riesgo RH02 / R03 + R01'),
    ('TC03', 'TC03_HU211_Evaluacion_inicial_secuencial_y_persistencia_de_respuestas', 'Evaluación secuencial y persistencia de respuestas',
     'Azure #434 · HU 2.1.1 CA2, CA3, CR-006 · Riesgo RH03'),
    ('TC04', 'TC04_AZ483_Login_errores_de_formato_y_mensaje_generico', 'Login: errores de formato y mensaje genérico', 'Azure #483 · HU 1.1.2'),
    ('TC05', 'TC05_AZ478_Registro_limites_de_longitud_y_formato', 'Registro: límites de longitud y formato', 'Azure #478 · HU 1.1.1'),
    ('TC06', 'TC06_AZ482_Registro_unicidad_de_usuario_y_correo', 'Registro: unicidad de usuario y correo', 'Azure #482 · HU 1.1.1'),
    ('TC07', 'TC07_AZ485_Login_exitoso_cookie_segura_y_redireccion_por_rol', 'Login exitoso: cookie segura, vigencia y redirección por rol', 'Azure #485 · HU 1.1.2 CA5'),
    ('TC08', 'TC08_AZ486_Recuperacion_mensaje_generico_y_vigencia', 'Recuperación de contraseña: mensaje genérico', 'Azure #486 · HU 1.1.3'),
    ('TC09', 'TC09_AZ488_Restablecimiento_enlace_invalido_y_complejidad', 'Restablecimiento: enlace inválido o vencido y complejidad', 'Azure #488, #486 · HU 1.1.3'),
    ('TC10', 'TC10_AZ494_Middleware_401_y_403_segun_token_y_rol', 'Middleware de autorización: HTTP 401 y 403', 'Azure #494, #489 · HU 1.2.1'),
    ('TC11', 'TC11_AZ422_Ingreso_al_modulo_de_evaluacion_inicial', 'Ingreso al módulo de evaluación inicial', 'Azure #422 · HU 2.1.1'),
    ('TC12', 'TC12_AZ435_AZ457_Finalizacion_y_retroalimentacion_de_la_evaluacion', 'Finalización y retroalimentación de la evaluación', 'Azure #435, #457 · HU 2.1.1, 4.2.1'),
    ('TC13', 'TC13_AZ426_AZ451_AZ475_Progreso_perfil_y_categoria_del_estudiante', 'Progreso, perfil de conocimiento y categoría del estudiante', 'Azure #426, #451, #459, #465, #475'),
    ('TC14', 'TC14_AZ436_AZ446_AZ461_Ruta_de_aprendizaje_y_recomendaciones', 'Ruta de aprendizaje y recomendaciones', 'Azure #436, #446, #461'),
    ('TC15', 'TC15_AZ448_Calificacion_de_recursos_recomendados', 'Calificación de recursos recomendados', 'Azure #448'),
    ('TC16', 'TC16_AZ450_AZ500_Acceso_por_rol_a_reportes_y_datos_consolidados', 'Acceso por rol a reportes y JSON consolidado', 'Azure #450, #500'),
    ('TC17', 'TC17_AZ502_AZ476_AZ455_Reportes_graficos_y_filtro_por_nivel', 'Reportes gráficos, consolidación y filtro por nivel', 'Azure #502, #418, #476, #455'),
    ('TC18', 'TC18_AZ440_AZ444_Reportes_de_recursos_y_banco_de_preguntas', 'Reportes de recursos y del banco de preguntas', 'Azure #440, #444'),
    ('TC19', 'TC19_AZ430_Gestion_de_recursos_validaciones_y_consulta', 'Gestión de recursos: validaciones y consulta', 'Azure #430'),
    ('TC20', 'TC20_AZ468_Banco_de_preguntas_paginacion_filtros_y_versiones', 'Banco de preguntas: paginación, filtros y versiones', 'Azure #468, #499'),
]

RE_REGISTRO = re.compile(r'<record>(.*?)</record>', re.S)


def campo(registro, nombre):
    m = re.search(r'<%s>(.*?)</%s>' % (nombre, nombre), registro, re.S)
    if not m:
        return ''
    # Katalon escapa dos veces algunos mensajes (&amp;apos;): se desescapa hasta que el texto no cambie.
    texto, anterior = m.group(1), None
    while texto != anterior:
        anterior, texto = texto, html.unescape(texto)
    return texto.strip()


def fecha(texto):
    texto = re.sub(r'(\.\d{3})\d*', r'\1', texto)
    for formato in ('%Y-%m-%dT%H:%M:%S.%f%z', '%Y-%m-%dT%H:%M:%S%z', '%Y-%m-%dT%H:%M:%S.%f', '%Y-%m-%dT%H:%M:%S'):
        try:
            valor = datetime.strptime(texto, formato)
            # Las capturas usan la hora local en el nombre: las fechas con zona se pasan a hora local.
            return (valor.astimezone() if valor.tzinfo else valor).replace(tzinfo=None)
        except ValueError:
            pass
    return None


def leer_ejecucion(ruta_log, caso, nombre_tc):
    """Devuelve el resumen de una ejecución del caso, o None si el log no es de ese caso."""
    contenido = open(ruta_log, encoding='utf-8', errors='replace').read()
    if nombre_tc not in contenido:
        return None
    registros = []
    for bloque in RE_REGISTRO.findall(contenido):
        registros.append({
            'fecha': fecha(campo(bloque, 'date')) or datetime.fromtimestamp(int(campo(bloque, 'millis') or 0) / 1000),
            'nivel': campo(bloque, 'level'),
            'mensaje': campo(bloque, 'message'),
            'anidamiento': int(campo(bloque, 'nestedLevel') or 0),
        })
    if not registros:
        return None
    marca = '[%s]' % caso
    errores = [r['mensaje'] for r in registros if r['nivel'] == 'ERROR' and (marca in r['mensaje'] or r['anidamiento'] == 0)]
    fallos = [r['mensaje'] for r in registros if r['nivel'] == 'FAILED' and marca in r['mensaje']]
    advertencias = [r['mensaje'] for r in registros if r['nivel'] == 'WARNING' and marca in r['mensaje']]
    estado_tc = [r['nivel'] for r in registros if r['nivel'] in ('PASSED', 'FAILED', 'ERROR') and nombre_tc in r['mensaje']]
    if errores or (estado_tc and estado_tc[-1] == 'ERROR'):
        estado = 'ERROR'
    elif fallos or (estado_tc and estado_tc[-1] == 'FAILED'):
        estado = 'FAILED'
    elif estado_tc and estado_tc[-1] == 'PASSED':
        estado = 'PASSED'
    else:
        estado = 'INCOMPLETO'
    if estado == 'FAILED' and not fallos:
        fallos = [r['mensaje'] for r in registros if r['nivel'] == 'FAILED'][-3:]
    pasos = [r['mensaje'] for r in registros if r['nivel'] == 'INFO' and r['mensaje'].startswith(marca)]
    return {
        'log': os.path.relpath(ruta_log, PROYECTO),
        'inicio': registros[0]['fecha'],
        'fin': registros[-1]['fecha'],
        'estado': estado,
        'errores': list(dict.fromkeys(errores)),
        'fallos': list(dict.fromkeys(fallos)),
        'advertencias': list(dict.fromkeys(advertencias)),
        'pasos': [p[len(marca):].strip() for p in pasos],
    }


def ejecuciones_de(caso, nombre_tc):
    resultado = []
    for raiz, _, archivos in os.walk(os.path.join(PROYECTO, 'Reports')):
        for archivo in archivos:
            if archivo.startswith('execution') and archivo.endswith('.log'):
                ejecucion = leer_ejecucion(os.path.join(raiz, archivo), caso, nombre_tc)
                if ejecucion:
                    resultado.append(ejecucion)
    return sorted(resultado, key=lambda e: e['inicio'])


def capturas_de(caso, ejecucion):
    """Capturas del keyword Evidencia tomadas durante la ejecución (el nombre empieza con yyyyMMdd_HHmmss_SSS)."""
    carpeta = os.path.join(PROYECTO, 'Evidencias', caso)
    if not os.path.isdir(carpeta):
        return []
    desde, hasta = ejecucion['inicio'] - timedelta(seconds=5), ejecucion['fin'] + timedelta(seconds=5)
    capturas = []
    for archivo in sorted(os.listdir(carpeta)):
        m = re.match(r'(\d{8}_\d{6}_\d{3})_(.+)\.png$', archivo)
        if m and desde <= datetime.strptime(m.group(1), '%Y%m%d_%H%M%S_%f') <= hasta:
            capturas.append((os.path.join(carpeta, archivo), m.group(2)))
    return capturas


def copiar_capturas(caso, capturas):
    destino = os.path.join(SALIDA_EVIDENCIAS, caso)
    if os.path.isdir(destino):
        shutil.rmtree(destino)
    os.makedirs(destino)
    copias = []
    for origen, paso in capturas:
        nombre = os.path.basename(origen)
        shutil.copy2(origen, os.path.join(destino, nombre))
        copias.append((os.path.join(destino, nombre), paso))
    return copias


def duracion(e):
    segundos = int((e['fin'] - e['inicio']).total_seconds())
    return '%d min %02d s' % (segundos // 60, segundos % 60)


ICONO = {'PASSED': '✅ PASSED', 'FAILED': '❌ FAILED', 'ERROR': '⚠️ ERROR', 'INCOMPLETO': '⏸ INCOMPLETO', 'NO EJECUTADO': '— NO EJECUTADO'}


def main():
    datos = []
    for caso, nombre_tc, titulo, origen in CASOS:
        historial = ejecuciones_de(caso, nombre_tc)
        ultima = historial[-1] if historial else None
        capturas = copiar_capturas(caso, capturas_de(caso, ultima)) if ultima else []
        datos.append(dict(caso=caso, nombre=nombre_tc, titulo=titulo, origen=origen, historial=historial, ultima=ultima, capturas=capturas))

    ajustes_ruta = os.path.join(DOC, 'herramientas', 'ajustes.md')
    ajustes = open(ajustes_ruta, encoding='utf-8').read().strip() if os.path.exists(ajustes_ruta) else ''
    generado = datetime.now().strftime('%Y-%m-%d %H:%M')
    escribir_md(datos, ajustes, generado)
    escribir_html(datos, ajustes, generado)
    for d in datos:
        print(d['caso'], d['ultima']['estado'] if d['ultima'] else 'NO EJECUTADO', len(d['historial']), 'ejecuciones', len(d['capturas']), 'capturas')


def escribir_md(datos, ajustes, generado):
    l = ['# Informe de ejecución de pruebas – ITMentorSoft', '',
         f'Generado: {generado} · Aplicación: https://itmentorsoft.com · Herramienta: Katalon Studio 11.6 · Perfil: `default` · Navegador: Chrome', '',
         'Fuente: logs de Katalon en `Reports/` y evidencias del keyword `Evidencia`. Las capturas de la última ejecución de cada caso se copiaron a `Documentacion/Evidencias_Ejecucion/`. El reporte visual está en `Documentacion/Reporte_Pruebas.html`. La clasificación de los 43 casos de Azure DevOps y su trazabilidad con estos casos está en `Documentacion/Inventario_Casos_Azure.md`; los defectos y observaciones, al final de este informe.', '',
         '## Resumen', '', '| Caso | Descripción | Origen | Resultado | Fecha | Duración | Intentos |', '|---|---|---|---|---|---|---|']
    for d in datos:
        u = d['ultima']
        l.append(f"| {d['caso']} | {d['titulo']} | {d['origen']} | {ICONO[u['estado'] if u else 'NO EJECUTADO']} | "
                 f"{u['inicio'].strftime('%Y-%m-%d %H:%M') if u else '—'} | {duracion(u) if u else '—'} | {len(d['historial'])} |")
    l += ['', 'Convención: **PASSED** = se cumplieron todas las verificaciones (las advertencias son observaciones sobre el requisito); **FAILED** = defecto funcional; **ERROR** = la prueba no se pudo completar (configuración, login/OTP o precondición).', '']
    for d in datos:
        u = d['ultima']
        l += [f"## {d['caso']} – {d['titulo']}", '', f"`{d['nombre']}` · {d['origen']}", '']
        if not u:
            l += ['No hay ejecuciones registradas en `Reports/`.', '']
            continue
        l += [f"**Resultado de la última ejecución:** {ICONO[u['estado']]} · {u['inicio'].strftime('%Y-%m-%d %H:%M:%S')} · {duracion(u)} · log `{u['log']}`", '']
        for titulo, mensajes in (('Errores', u['errores']), ('Defectos (FAILED)', u['fallos']), ('Advertencias', u['advertencias'])):
            if mensajes:
                l += [f'**{titulo}:**', ''] + [f'- {m}' for m in mensajes] + ['']
        if u['pasos']:
            l += ['**Bitácora de la ejecución:**', ''] + [f'{i}. {p}' for i, p in enumerate(u['pasos'], 1)] + ['']
        if d['capturas']:
            l += ['**Evidencias:**', '']
            for ruta, paso in d['capturas']:
                rel = os.path.relpath(ruta, DOC).replace(os.sep, '/')
                l += [f'*{paso}*', '', f'![{paso}]({rel.replace(" ", "%20")})', '']
        if len(d['historial']) > 1:
            l += ['**Historial de intentos:**', '', '| # | Fecha | Resultado | Motivo |', '|---|---|---|---|']
            for i, e in enumerate(d['historial'], 1):
                motivo = (e['errores'] or e['fallos'] or [''])[0].replace('|', '/').replace('\n', ' ')
                l.append(f"| {i} | {e['inicio'].strftime('%Y-%m-%d %H:%M')} | {ICONO[e['estado']]} | {motivo[:220]} |")
            l.append('')
    if ajustes:
        l += ['## Ajustes realizados entre ejecuciones', '', ajustes, '']
    open(os.path.join(DOC, 'Informe_Ejecucion_Pruebas.md'), 'w', encoding='utf-8', newline='\n').write('\n'.join(l))


def md_a_html_simple(texto):
    """Convierte el Markdown sencillo de ajustes.md (párrafos, listas y `código`)."""
    salida, en_lista = [], False
    for linea in texto.splitlines():
        contenido = html.escape(linea.strip())
        contenido = re.sub(r'`([^`]+)`', r'<code>\1</code>', contenido)
        contenido = re.sub(r'\*\*([^*]+)\*\*', r'<strong>\1</strong>', contenido)
        if linea.strip().startswith(('- ', '* ')):
            if not en_lista:
                salida.append('<ul>')
                en_lista = True
            salida.append(f'<li>{contenido[2:]}</li>')
            continue
        if en_lista:
            salida.append('</ul>')
            en_lista = False
        if linea.startswith('### '):
            salida.append(f'<h4>{contenido[4:]}</h4>')
        elif contenido:
            salida.append(f'<p>{contenido}</p>')
    if en_lista:
        salida.append('</ul>')
    return '\n'.join(salida)


def escribir_html(datos, ajustes, generado):
    e = html.escape
    clase = {'PASSED': 'ok', 'FAILED': 'fail', 'ERROR': 'err', 'INCOMPLETO': 'err', 'NO EJECUTADO': 'none'}
    total = len(datos)
    conteo = {k: sum(1 for d in datos if (d['ultima']['estado'] if d['ultima'] else 'NO EJECUTADO') == k) for k in clase}
    filas = ''.join(
        f"<tr><td><a href='#{d['caso']}'>{d['caso']}</a></td><td>{e(d['titulo'])}</td><td>{e(d['origen'])}</td>"
        f"<td><span class='badge {clase[d['ultima']['estado'] if d['ultima'] else 'NO EJECUTADO']}'>{d['ultima']['estado'] if d['ultima'] else 'NO EJECUTADO'}</span></td>"
        f"<td>{d['ultima']['inicio'].strftime('%Y-%m-%d %H:%M') if d['ultima'] else '—'}</td><td>{duracion(d['ultima']) if d['ultima'] else '—'}</td><td>{len(d['historial'])}</td></tr>"
        for d in datos)
    secciones = []
    for d in datos:
        u = d['ultima']
        partes = [f"<section id='{d['caso']}'><h2>{d['caso']} – {e(d['titulo'])}</h2><p class='meta'><code>{e(d['nombre'])}</code> · {e(d['origen'])}</p>"]
        if not u:
            partes.append('<p>No hay ejecuciones registradas.</p></section>')
            secciones.append(''.join(partes))
            continue
        partes.append(f"<p><span class='badge {clase[u['estado']]}'>{u['estado']}</span> {u['inicio'].strftime('%Y-%m-%d %H:%M:%S')} · {duracion(u)} · <code>{e(u['log'])}</code></p>")
        for titulo, mensajes, css in (('Errores', u['errores'], 'err'), ('Defectos', u['fallos'], 'fail'), ('Advertencias', u['advertencias'], 'warn')):
            if mensajes:
                partes.append(f"<div class='box {css}'><strong>{titulo}</strong><ul>{''.join(f'<li>{e(m)}</li>' for m in mensajes)}</ul></div>")
        if u['pasos']:
            partes.append(f"<details open><summary>Bitácora ({len(u['pasos'])} registros)</summary><ol class='log'>{''.join(f'<li>{e(p)}</li>' for p in u['pasos'])}</ol></details>")
        if d['capturas']:
            figuras = []
            for ruta, paso in d['capturas']:
                datos_img = base64.b64encode(open(ruta, 'rb').read()).decode()
                figuras.append(f"<figure><a href='data:image/png;base64,{datos_img}' target='_blank'><img loading='lazy' alt='{e(paso)}' src='data:image/png;base64,{datos_img}'></a><figcaption>{e(paso)}</figcaption></figure>")
            partes.append(f"<h3>Evidencias</h3><div class='grid'>{''.join(figuras)}</div>")
        if len(d['historial']) > 1:
            intentos = ''.join(
                f"<tr><td>{i}</td><td>{x['inicio'].strftime('%Y-%m-%d %H:%M')}</td><td><span class='badge {clase[x['estado']]}'>{x['estado']}</span></td><td>{e((x['errores'] or x['fallos'] or [''])[0][:260])}</td></tr>"
                for i, x in enumerate(d['historial'], 1))
            partes.append(f"<h3>Historial de intentos</h3><div class='scroll'><table><tr><th>#</th><th>Fecha</th><th>Resultado</th><th>Motivo</th></tr>{intentos}</table></div>")
        partes.append('</section>')
        secciones.append(''.join(partes))
    bloque_ajustes = f"<section><h2>Ajustes realizados entre ejecuciones</h2>{md_a_html_simple(ajustes)}</section>" if ajustes else ''
    documento = f"""<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Reporte de Pruebas ITMentorSoft</title>
<style>
:root {{ --bg:#f6f7f9; --card:#fff; --text:#1d2433; --muted:#5b6475; --line:#dde1e8; --ok:#1f7a4d; --fail:#b42318; --err:#9a6700; --warn:#8a5a00; --none:#5b6475; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg:#14171c; --card:#1c2027; --text:#e6e9ef; --muted:#9aa3b2; --line:#2e3440; --ok:#4cc38a; --fail:#ff6b5e; --err:#e5b13a; --warn:#e5b13a; --none:#9aa3b2; }} }}
* {{ box-sizing:border-box; }} body {{ margin:0; background:var(--bg); color:var(--text); font:15px/1.5 system-ui,Segoe UI,Roboto,sans-serif; }}
main {{ max-width:1100px; margin:0 auto; padding:24px 16px 48px; }} h1 {{ margin:0 0 4px; font-size:1.6rem; }} h2 {{ margin:0 0 6px; font-size:1.25rem; }}
.meta, .sub {{ color:var(--muted); }} section {{ background:var(--card); border:1px solid var(--line); border-radius:10px; padding:18px; margin-top:18px; }}
.kpis {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(140px,1fr)); gap:12px; margin-top:16px; }}
.kpi {{ background:var(--card); border:1px solid var(--line); border-radius:10px; padding:12px 14px; }} .kpi b {{ display:block; font-size:1.6rem; }}
.scroll {{ overflow-x:auto; }} table {{ width:100%; border-collapse:collapse; }} th, td {{ text-align:left; padding:8px; border-bottom:1px solid var(--line); vertical-align:top; }}
.badge {{ display:inline-block; padding:2px 8px; border-radius:999px; font-weight:700; font-size:.8rem; color:#fff; }}
.badge.ok {{ background:var(--ok); }} .badge.fail {{ background:var(--fail); }} .badge.err {{ background:var(--err); }} .badge.none {{ background:var(--none); }}
.box {{ border-left:4px solid; padding:8px 12px; margin:10px 0; background:color-mix(in srgb, var(--card) 85%, var(--line)); border-radius:6px; }}
.box.err {{ border-color:var(--err); }} .box.fail {{ border-color:var(--fail); }} .box.warn {{ border-color:var(--warn); }} .box ul {{ margin:6px 0 0; padding-left:18px; }}
.grid {{ display:grid; grid-template-columns:repeat(auto-fill,minmax(240px,1fr)); gap:12px; }} figure {{ margin:0; }} img {{ width:100%; border:1px solid var(--line); border-radius:6px; }}
figcaption {{ color:var(--muted); font-size:.85rem; }} code {{ font-size:.85em; word-break:break-all; }} .log li {{ margin:2px 0; word-break:break-word; }}
</style></head><body><main>
<h1>Reporte de pruebas – ITMentorSoft</h1>
<p class="sub">Generado {generado} · https://itmentorsoft.com · Katalon Studio 11.6 · Perfil default · Chrome · Fuente: logs de Katalon (<code>Reports/</code>) y evidencias del proyecto · Trazabilidad con Azure: <code>Documentacion/Inventario_Casos_Azure.md</code> · Defectos y observaciones al final</p>
<div class="kpis"><div class="kpi"><b>{total}</b>Casos</div><div class="kpi"><b style="color:var(--ok)">{conteo['PASSED']}</b>PASSED</div><div class="kpi"><b style="color:var(--fail)">{conteo['FAILED']}</b>FAILED</div><div class="kpi"><b style="color:var(--err)">{conteo['ERROR'] + conteo['INCOMPLETO']}</b>ERROR</div><div class="kpi"><b>{conteo['NO EJECUTADO']}</b>No ejecutados</div></div>
<section><h2>Resumen</h2><div class="scroll"><table><tr><th>Caso</th><th>Descripción</th><th>Origen</th><th>Resultado</th><th>Fecha</th><th>Duración</th><th>Intentos</th></tr>{filas}</table></div>
<p class="sub">PASSED = verificaciones cumplidas (las advertencias son observaciones sobre el requisito) · FAILED = defecto funcional · ERROR = no se pudo completar la prueba.</p></section>
{''.join(secciones)}
{bloque_ajustes}
</main></body></html>"""
    open(os.path.join(DOC, 'Reporte_Pruebas.html'), 'w', encoding='utf-8', newline='\n').write(documento)


if __name__ == '__main__':
    main()
