<?xml version="1.0" encoding="UTF-8"?>
<WebElementEntity>
   <description>Localizador GENERICO (no depende del HTML real): cualquier boton o enlace para retroceder, saltar u omitir preguntas. HU 2.1.1 CA2 exige que no exista. REVISAR si la UI usa iconos sin texto o un indicador de preguntas clicable.</description>
   <name>btn_NavegacionNoPermitida</name>
   <tag>REVISAR</tag>
   <elementGuidId>0e019558-e83f-5059-bbfb-0a9556b0701e</elementGuidId>
   <selectorCollection>
      <entry>
         <key>XPATH</key>
         <value>//*[(self::button or self::a or @role='button') and (contains(translate(normalize-space(.),'ANTERIORÁTRSLOMI','anterioratrslomi'),'anterior') or contains(translate(normalize-space(.),'ANTERIORÁTRSLOMI','anterioratrslomi'),'atrás') or contains(translate(normalize-space(.),'ANTERIORÁTRSLOMI','anterioratrslomi'),'atras') or contains(translate(normalize-space(.),'ANTERIORÁTRSLOMI','anterioratrslomi'),'saltar') or contains(translate(normalize-space(.),'ANTERIORÁTRSLOMI','anterioratrslomi'),'omitir'))]</value>
      </entry>
   </selectorCollection>
   <selectorMethod>XPATH</selectorMethod>
   <smartLocatorEnabled>false</smartLocatorEnabled>
   <useRalativeImagePath>true</useRalativeImagePath>
</WebElementEntity>
