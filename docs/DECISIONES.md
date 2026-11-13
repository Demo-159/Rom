# ROM personalizada para Tanix TX3 Mini: decisiones

Registro de lo decidido y lo pendiente. Se actualiza en cada paso.

Última actualización: 13 de noviembre de 2026.

**Resumen:** siempre Android (nunca Linux), rendimiento por encima de todo, la ROM se identifica como Android TV y se conservan los servicios de Google.

## 1. Equipo y ROM base

| Dato | Valor |
|---|---|
| Equipo | Tanix TX3 Mini |
| Procesador | Amlogic S905W (familia GXL), placa `p281` |
| ROM base | `TX3Mini-17APK-20190110.img` (fabricante Oranth, 10 de enero de 2019) |
| Android | 7.1.2 (API 25), compilación `userdebug`, firmada con *test-keys* |
| Parche de seguridad | 1 de diciembre de 2017 |
| Arquitectura de apps | 32 bits (`armeabi-v7a`) |
| Formato de la imagen | Amlogic USB Burning Tool, versión 2, 19 elementos |
| Variantes de memoria | El DTB trae dos perfiles: `gxl_p281_1g` y `gxl_p281_2g` |

La ROM original viene con muchas apps preinstaladas para el mercado vietnamita (FPT Play, ClipTV, ZingTV, VTVgo, etc.), además de Netflix, Chrome por duplicado y apps de prueba de fábrica.

## 2. Camino elegido

**Decisión: modificar la ROM original de Android 7.1.2.** No se hace una ROM desde cero.

Motivos:
- Una ROM Android desde cero necesita el kernel y los drivers del equipo: WiFi, GPU Mali, HDMI y control remoto. El fabricante no los publicó.
- Compilar AOSP necesita unos 400 GB de disco y muchas horas.
- Al modificar la original se conservan el kernel, los drivers y el bootloader de fábrica, que ya funcionan en el equipo. Es lo más seguro para no inutilizar la cajita.

**Decisión del usuario: la ROM será siempre Android, nunca Linux.**

Descartado:
- **Linux (CoreELEC / Armbian):** descartado de forma definitiva por decisión del usuario.
- **Subir a Android 9:** solo se podría adaptando la ROM de otra cajita con S905W, como la X96 Mini. Tiene riesgo de quedarse sin WiFi (hay varias revisiones del chip), de que no arranque y de que vaya lenta con 1 GB de RAM. Queda como experimento aparte, después de terminar la versión 7.1.

## 3. Prioridad principal: rendimiento máximo

**Decisión del usuario: el rendimiento es la prioridad número uno.** La ROM tiene que ir muy rápida y consumir los mínimos recursos posibles: RAM, CPU y almacenamiento.

Regla para cualquier decisión: **si una función o un adorno visual cuesta rendimiento, gana el rendimiento.** Consecuencias:
- **Launcher:** se prefiere el más ligero que cumpla con lo visual. Nada de fondos animados ni en video, que gastan CPU y GPU todo el tiempo. Se usa fondo estático.
- **Apps:** limpieza agresiva. Se borra todo lo que no sea necesario para arrancar, navegar, ver contenido y usar el Play Store.
- **Servicios de Google: se conservan** (decisión del usuario). Play Store, Play Services y el inicio de sesión de Google se quedan, aunque gasten RAM. Sí se pueden quitar apps de Google que no sean necesarias, como el sincronizador de Calendario y Contactos, Google Feedback o Google TTS, si no rompen nada.
- **Servicios:** se desactivan los servicios en segundo plano del fabricante (DLNA, Miracast, actualizaciones OTA de Oranth, LEDs, pruebas de fábrica, etc.).
- **Animaciones del sistema:** muy rápidas o desactivadas.

Plan técnico, con valores exactos por definir y probar:
- **Memoria:**
  - Ajustar el *low memory killer* (`minfree`) para cerrar antes las apps en segundo plano.
  - Ajustar `swappiness` y el zram (hoy 500 MB).
  - Revisar el heap de Dalvik según la RAM real.
  - Evaluar `ro.config.low_ram=true` si el equipo es de 1 GB.
- **Ajuste automático según la RAM:** el usuario no sabe si su cajita tiene 1 GB o 2 GB. La ROM tiene que funcionar bien en las dos.
  - El arranque ya elige solo el perfil de hardware: el DTB trae `gxl_p281_1g` y `gxl_p281_2g`.
  - Se añade un script de arranque, ejecutado desde `init` antes de que arranque Android, que lee la memoria total en `/proc/meminfo` y aplica el perfil que toque:
    - **1 GB:** modo estricto. `minfree` agresivo, zram más grande, heap de Dalvik más chico y posiblemente `ro.config.low_ram=true`.
    - **2 GB:** modo equilibrado, con más apps abiertas en segundo plano.
- **Animaciones:** `window_animation_scale`, `transition_animation_scale` y `animator_duration_scale` en 0,5 o en 0.
- **Registro del sistema:** reducir el tamaño de `logd` y desactivar trazas de depuración que no hagan falta. ADB como root se mantiene.
- **Arranque:** quitar apps que se abren solas al iniciar y animación de arranque corta.
- **Compilación de apps:** las apps del sistema ya vienen precompiladas (62 carpetas `oat`). Hay que mantener la precompilación de las apps que se queden.
- **Almacenamiento:** liberar espacio en `system` borrando apps. Hoy quedan unos 375 MB libres.


## 4. Cambios aprobados en la ROM

### 4.1 Identificarse como Android TV ✅
**Decisión: la ROM se declarará como dispositivo Android TV.**

- **Problema:** la ROM original no declara las funciones `android.software.leanback` ni `android.hardware.type.television`. Así lo comprobé en `/system/etc/permissions`.
- **Consecuencia:** el Play Store oculta las apps marcadas "solo Android TV", como Projectivy Launcher, y esas apps no se instalan desde APK.
- **Cambio:** añadir un archivo de permisos en `/system/etc/permissions/` que declare las dos funciones.
- **Efecto esperado:** aparecen las apps de TV en el Play Store, y algunas apps, como YouTube, cambian a su versión para TV, que se maneja mejor con el control remoto.

### 4.2 Quitar apps que sobran ✅
Lista a confirmar antes de compilar:
- Apps preinstaladas de `/system/fast_preinstall/` (las apps vietnamitas, entre otras) y Netflix de `/system/preinstall/`. **Excepción:** `AndroidApp22.apk` (Google para Android TV, búsqueda por voz) se conserva; ver sección 4.6.
- Chrome duplicado: está en `fast_preinstall` y en `/system/app/Chrome`.
- Apps de prueba o del fabricante: `FactoryTest_oranth`, `Dig`, `LedStatus` y el launcher `Oranth_Launcher_720P`, que se reemplaza (ver sección 5).
- Hay que revisar las demás, una por una, antes de borrarlas.

### 4.3 Root + ADB ✅
- La ROM ya trae ADB como root activado: `ro.debuggable=1`, `service.adb.root=1`, `ro.adb.secure=0` y ADB por red en el puerto 5555.
- El `su` que trae (`/system/xbin/su`) solo lo puede usar ADB, no las apps.
- **Decisión:** root para apps con **Magisk v27.0**, aplicado a `boot.img` desde la PC. El parche se hace replicando su `boot_patch.sh` con `magiskboot` para x86_64. Magisk ya está descargado para hacer pruebas.
- No hay dm-verity en `/system` (comprobado en `fstab.amlogic`), así que modificar `system` no impide el arranque.

### 4.4 Rendimiento ✅ (prioridad máxima)
El detalle está en la sección 3.

### 4.5 Marca: "TV", marca blanca ✅
**Decisión del usuario: la ROM se llama simplemente "TV", sin marca propia (marca blanca).**

- **Nombre visible:** "TV" en *Acerca de*, en el nombre del dispositivo en la red y en Bluetooth, y en el launcher. Se cambian `ro.product.model=TV` y el nombre del dispositivo.
- **Lo que no se toca:** `ro.build.fingerprint`, `ro.product.name` y `ro.product.device` (`p281`) se quedan como en la original. El Play Store y los servicios de Google usan esos datos; cambiarlos puede romper el inicio de sesión o la compatibilidad de apps.
- **Logo de arranque** (partición `logo`, imagen `bootup`). **Decisión del usuario: sin logo.**
  - El bootloader (u-boot) siempre muestra la imagen `bootup`, así que se reemplaza por una **pantalla negra**. Visualmente no hay logo.
  - Si una pantalla totalmente negra diera problemas (por ejemplo, que la TV no detecte la señal y parezca apagada), se usa el robot de Android, que tiene licencia libre (CC BY 3.0).
- **Animación de arranque** (`/system/media/bootanimation.zip`). **Decisión del usuario: que el arranque se parezca al de Google TV.**
  - Se hace una animación **original con el mismo estilo**: fondo negro, formas simples de colores que aparecen con suavidad y transición limpia al launcher.
  - **No se usan el logo ni la animación de Google TV**, que son marcas registradas de Google. Tampoco encajan en una ROM de marca blanca.
  - Por rendimiento: resolución 720p, pocos fotogramas, imágenes comprimidas y duración corta. El tiempo real de arranque no depende de la animación, pero así no consume recursos mientras carga.
  - Si no convence, el respaldo es la animación estándar de AOSP (el texto "android" con brillo). Viene dentro del sistema y no pesa nada.
- Se quitan las referencias visibles a Oranth y Tanix en lo que vea el usuario.

### 4.6 Asistente de Google (solicitado, con límites)
**Lo que pide el usuario:** un asistente de Google bueno, rápido y moderno.

**Lo que es posible en este equipo:**
- **Gemini y el Asistente de Google moderno no funcionan en esta cajita.**
  - Google está reemplazando el Asistente por Gemini desde septiembre de 2026, también en TVs.
  - Según Google (marzo de 2025), Gemini pide Android 10 o superior y al menos 2 GB de RAM. Esta cajita tiene Android 7.1 y quizá 1 GB.
- **Lo viable es la búsqueda por voz de Google para Android TV** (la app "Google para Android TV", paquete `com.google.android.katniss`). Permite buscar por voz en YouTube y en otras apps, y abrir apps. No es el asistente conversacional moderno.
  - **Pendiente comprobar** qué versión instala y funciona hoy en Android 7.1. No hay fuentes recientes sobre esto.
  - Se ve beneficiada por el cambio 4.1: con la ROM identificada como Android TV, aparece en el Play Store.
- **Control con micrófono: el usuario ya tiene uno.** Es un mando con receptor USB y micrófono, y funciona con la ROM original. Para que siga funcionando, la ROM conserva sin cambios:
  - El kernel original, con los drivers USB HID y de audio USB.
  - Los módulos de audio: `audio.usb.default.so` (la ROM ya declara entrada de micrófono USB en `audio_policy.conf`) y los módulos de mandos de voz `audio.dia_remote.default.so` y `audio.huitong.default.so`, con sus archivos `*_audio_policy_configuration.xml`.
  - Todos los mapas de teclas de `/system/usr/keylayout/`.
  - **Resuelto: el botón del micrófono abre la búsqueda por voz de Google.** El usuario cree que es así, y la ROM lo confirma. El archivo `/system/fast_preinstall/AndroidApp22.apk` (28 MB) es la app **Google para Android TV** (`com.google.android.katniss`), que trae `VoiceInputActivity` y `HotwordService`. El launcher original también la llama.
  - **Decisión:** esta app **se conserva** y no entra en la limpieza de `fast_preinstall`. Se le pone un nombre de archivo claro (por ejemplo `GoogleTV_Voz.apk`). Se queda como app instalable, así el Play Store la puede actualizar.
  - **Pendiente:** revisar si hay una versión más nueva de esta app que funcione en Android 7.1, y comprobar que el launcher nuevo abra la búsqueda por voz con el botón.
- **Choque con el rendimiento:** la app de Google mantiene servicios en segundo plano y consume RAM. Se instala, pero se ajusta para que no se active sola más de lo necesario.

### 4.7 YouTube: TizenTube Cobalt en lugar de la app oficial
**Decisión del usuario:** se usa **TizenTube Cobalt** (`io.gh.reisxd.tizentube.cobalt`, https://github.com/reisxd/TizenTubeCobalt) como app de YouTube.

- **En el launcher** se llama **"YouTube"** y usa la imagen con el aspecto oficial: logo rojo con texto sobre fondo blanco, la variante "tvOS" de la biblioteca de Aerial.
- **Búsqueda por voz:** lo ideal es que los videos de YouTube encontrados por voz se abran en TizenTube y no en la app original.
  - TizenTube Cobalt declara que abre los enlaces `youtube.com` / `youtu.be` (`VIEW`) y las búsquedas para reproducir (`MEDIA_PLAY_FROM_SEARCH`). Está comprobado en su `AndroidManifest.xml.jinja2`.
  - **Plan A:** quitar de la ROM el YouTube original (`fast_preinstall/com.google.android.youtube.tv_2.02.08.apk`), así esos enlaces llegan a TizenTube.
  - **Pendiente probar en la cajita** si la búsqueda por voz de Google manda los resultados a cualquier app que abra `youtube.com` o solo al paquete `com.google.android.youtube.tv`.
  - **Plan B**, si solo funciona con el paquete oficial: una mini app "puente" con el nombre de paquete `com.google.android.youtube.tv`, sin interfaz, que reenvíe cada video o búsqueda a TizenTube. Hay que vigilar que el Play Store no intente "actualizarla".

## 5. Launcher (pantalla de inicio)

**Decisión: Aerial, modificado a medida e incluido dentro de la ROM.** Reemplaza a GlassTV, que se probó en el emulador y no convenció tanto.

- Repositorio original: https://github.com/pengchujin/aerial-tv (paquete `app.orchard.tv`, versión 1.96).
- **Licencia MIT:** permite modificarlo y distribuirlo dentro de la ROM, siempre que se conserve el aviso de copyright.
- **Estilo:** parecido al Apple TV. Reloj grande, fondo a pantalla completa, fila de apps favoritas sobre una repisa de vidrio y todas las apps debajo. Trae carpetas, centro de control y salvapantallas.
- **Compatibilidad:** pide Android 5.0+ (`minSdk 21`) y trae librerías `armeabi-v7a`. Se instala en el emulador de Android TV 7.1.
- **Mantenimiento:** muy activo; tuvo cambios el 13 de noviembre de 2026.
- **Cambios necesarios:**
  - **Traducción al español:** solo trae inglés, chino, ruso, ucraniano y kazajo.
  - **Recortar permisos:** pide ubicación, cuentas, sincronización, ventanas superpuestas, ajustes seguros, etc.; se dejan solo los imprescindibles.
  - **Rendimiento:** fondo estático por defecto. Sus salvapantallas en video aéreo gastan recursos y red; se dejan opcionales o se cambian por el de reloj.
  - Hay que revisar si trae funciones pensadas para TVs chinas (por ejemplo, servicios o pesos chinos para el clima) y quitar lo que no sirva.
  - El botón del micrófono tiene que abrir la búsqueda por voz de Google (sección 4.6).
- **Lite:** el repositorio trae una versión `lite` (`minSdk 14`). Se evalúa si conviene por rendimiento.

El launcher del fabricante (`Oranth_Launcher_720P`) se reemplaza.

### Optimización de RAM del launcher (aplicada)
Medido en el emulador de Android TV 7.1: Aerial usa **~45 MB** (PSS) en el inicio, frente a ~30 MB del launcher de Android TV. Cambios hechos sin alterar lo que se ve:
1. **Mosaicos al tamaño real:** las imágenes de cada app se guardan al tamaño en píxeles del mosaico seleccionado (235 px de ancho en 720p) en vez de un mínimo fijo de 352 px. Son ~55 % menos de memoria por app.
2. **Miniaturas de Personalizar:** se liberan al cerrar Personalizar.
3. **Funciones quitadas** (`launcher/app/src/main/java/app/orchard/tv/RomFlags.kt`; se reactivan cambiando la constante):
   - `AERIAL_FILMS = false`: sin videos aéreos de Apple como fondo ni como salvapantallas. Las fotos los reemplazan.
   - `KIDS = false`: sin modo niños y mayores.
   - `GUARD = false`: sin guardián de inicio, escucha de notificaciones ni cuenta de sincronización. En la ROM Aerial es la pantalla de inicio del sistema y no los necesita. Además van desactivados en el manifiesto.


### Requisitos
- **Consumir lo mínimo de RAM y CPU** (sección 3). Con fondo estático, sin animaciones de fondo.
- Funcionar en **Android 7.1** (API 25). Varios launchers modernos piden Android 8 o 9.
- Para meterlo dentro de la ROM, tiene que ser de código abierto con licencia que lo permita. Si no lo es, el usuario lo instala él mismo desde el Play Store.
- Estilo preferido: **tipo Google TV, sin banner**. Barra superior con buscador y pestañas, fila de "Recomendaciones" en tarjetas, fila de "Tus aplicaciones" en íconos redondos y fondo con olas de luz, ahora **estático** por rendimiento. Es la maqueta D: https://claude.ai/artifact/X1kyuUFDTHFeStmKi3WTAN
- El usuario prefiere **un launcher ya hecho** antes que uno programado desde cero.

### Candidatos que funcionan en Android 7.1
| Launcher | Licencia | ¿Se puede meter en la ROM? | Notas |
|---|---|---|---|
| Lite TV Launcher | GPL-3.0 | Sí | **El más ligero:** unos 44 KB de APK y unos 22 MB de RAM, según su autor. Pide Android 5+. Muy simple visualmente. |
| Luncher | MIT | Sí | Ligero: no pide permisos y arranca al instante. Organizado por categorías; no está en español. |
| GlassTV | MIT | Sí | Ligero (tiene versión para Android 4.4), reloj grande, en español. |
| Projectivy Launcher | Cerrada (gratis, con funciones de pago) | No: se instala desde el Play Store | Pide Android 6+. Muy personalizable, pero más pesado que los anteriores. Necesita el cambio 4.1 para instalarse. |
| Arc Launcher | GPL-3.0 | Sí | Estilo Apple TV, en español. Se puede dejar preconfigurado con el estilo D (fondo y color de acento). Hecho con Flutter: más pesado; usar con fondo estático. |
| LTvLauncher / FLauncher | GPL-3.0 | Sí | Más clásicos. Hechos con Flutter, más pesados que los nativos. |
| Aura TV | MIT | Sí | Efecto de vidrio esmerilado y fotos en movimiento: **choca con la prioridad de rendimiento.** En 7.1 pierde "seguir viendo" y las vistas previas. |

Comparativa con capturas: https://claude.ai/artifact/Pkr9ptFZymWPrada1nSWQd

### Descartados
- **No funcionan en Android 7.1:**
  - Monet Launcher: pide Android 8 y además es de código cerrado.
  - CleanYourTV, Koolest, Home Deck y CleanFrame: piden Android 8.
  - Lounge, Flix y TV Town: piden Android 9 u 11.
  - BestTVLauncher: pide Android 10.
- **Licencia:** Wolf Launcher es de código cerrado.
- **Launcher programado desde cero:** se hicieron maquetas, pero el usuario prefiere no arriesgar el resultado visual.

Se deja siempre un launcher sencillo de respaldo por si el principal falla.

## 6. Configuración del sistema

Al usuario no le gusta el apartado de configuración. La ROM trae tres apps distintas: `Settings` (Android de celular), `Settings_oranth` y `iTvSettings` (Amlogic).

Como la ROM está firmada con *test-keys* públicas, se pueden modificar y volver a firmar las apps del sistema.

**Pendiente:** decidir entre retocar la configuración actual o apoyarse en el centro de control del launcher elegido.

## 7. Entrega de la ROM

La ROM final pesa más de 1 GB. GitHub no acepta archivos de más de 100 MB, así que el repositorio guarda solo herramientas, scripts y documentación.

**Decisión del usuario: descarga temporal.** La ROM terminada se comprime y se sube a un servicio de descarga temporal, y el usuario recibe el enlace.
- Antes de subir se confirma con el usuario el servicio concreto, porque subir es publicar el archivo en un servicio externo.
- Se acompaña de su suma SHA-256 para comprobar que la descarga llegó completa.

Opciones que se evaluaron:
1. Enlace de descarga temporal. Requiere permiso para subirla a un servicio externo.
2. Partes de menos de 100 MB dentro del repositorio.
3. Script para generarla en la PC del usuario.

## 8. Datos técnicos comprobados

- **Desempaquetar y reempaquetar:** `tools/aml_image_unpack.py` desempaqueta y vuelve a empaquetar la imagen **byte a byte idéntica** a la original (comprobado con `cmp`).
  - Cabecera: 0x40 bytes. Cada elemento: 0x240 bytes. Alineación: 4 bytes.
  - CRC de la cabecera: CRC32 de todo el archivo desde el byte 4, **sin la inversión final** (`crc32 ^ 0xFFFFFFFF`).
  - Elementos de respaldo (`is_backup`): comparten los datos de otro elemento. Por ejemplo, `meson1.dtb` usa los de `_aml_dtb`.
- **Archivos `*.VERIFY`:** contienen `sha1sum <hash>` de la partición. Hay que regenerarlos cada vez que cambia una partición.
- **Partición `system`:** ext4 en formato *sparse* (`file_type 254`), de 2 GB, con unos 375 MB libres. Se convierte con `simg2img` / `img2simg` y se monta con loop. Las etiquetas SELinux se conservan.
- **Particiones `boot` y `recovery`:** formato Android boot v0, con kernel de 7,4 MB.
- **Tabla de particiones (en el DTB):**
  - logo, recovery, crypt, misc y boot: 32 MB cada una.
  - rsv y tee: 8 MB cada una.
  - system: 2 GB.
  - cache: 512 MB.
  - data: el resto.

## 9. Pendientes (resumen)

- [x] ~~Qué búsqueda por voz trae la ROM~~ → **Google para Android TV** (`AndroidApp22.apk`). Se conserva. Falta ver si hay una versión más nueva que funcione en 7.1.
- [x] ~~Control con micrófono~~ → **Tiene uno USB que ya funciona** y abre la búsqueda por voz de Google.

- [x] ~~Elegir launcher~~ → **Aerial modificado** (antes GlassTV). Traducirlo al español, recortar permisos y ponerle fondo estático.
- [x] ~~Decidir si se quitan los servicios de Google~~ → **Se conservan.**
- [x] ~~Nombre de la ROM y logo~~ → **"TV", marca blanca.** Sin logo de arranque (pantalla negra) y animación original al estilo Google TV.
- [x] ~~Forma de entrega~~ → **Descarga temporal.** Falta elegir el servicio al momento de subir.
- [ ] Lista final de apps a borrar.
- [ ] Valores de rendimiento.
- [ ] Decidir qué hacer con la configuración del sistema.
- [x] ~~Confirmar la RAM de la cajita~~ → **No se sabe.** La ROM detecta la RAM sola y se ajusta (sección 3).
