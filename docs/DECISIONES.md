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
- Apps preinstaladas de `/system/fast_preinstall/` (las apps vietnamitas, entre otras) y Netflix de `/system/preinstall/`.
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

### 4.5 Marca propia ✅ (datos pendientes)
- Nombre del modelo y de la versión en `build.prop`.
- Logo de arranque (partición `logo`, formato `AML_RES`, imagen `bootup`).
- Animación de arranque (`/system/media/bootanimation.zip`).
- **Pendiente:** nombre de la ROM y logo. Si el usuario no trae un logo, se diseña uno sencillo con el nombre.

### 4.6 Asistente de Google (solicitado, con límites)
**Lo que pide el usuario:** un asistente de Google bueno, rápido y moderno.

**Lo que es posible en este equipo:**
- **Gemini y el Asistente de Google moderno no funcionan en esta cajita.**
  - Google está reemplazando el Asistente por Gemini desde septiembre de 2026, también en TVs.
  - Según Google (marzo de 2025), Gemini pide Android 10 o superior y al menos 2 GB de RAM. Esta cajita tiene Android 7.1 y quizá 1 GB.
- **Lo viable es la búsqueda por voz de Google para Android TV** (la app "Google para Android TV", paquete `com.google.android.katniss`). Permite buscar por voz en YouTube y en otras apps, y abrir apps. No es el asistente conversacional moderno.
  - **Pendiente comprobar** qué versión instala y funciona hoy en Android 7.1. No hay fuentes recientes sobre esto.
  - Se ve beneficiada por el cambio 4.1: con la ROM identificada como Android TV, aparece en el Play Store.
- **Hace falta un control con micrófono.** El control infrarrojo que trae el TX3 Mini no tiene micrófono. Sirve un control Bluetooth o 2.4 GHz con micrófono, como los "air mouse" G10S o G20S.
- **Choque con el rendimiento:** la app de Google mantiene servicios en segundo plano y consume RAM. Se instala, pero se ajusta para que no se active sola más de lo necesario.

## 5. Launcher (pantalla de inicio)

**Estado: todavía no se ha elegido.** El launcher del fabricante (`Oranth_Launcher_720P`) se va a reemplazar porque se ve feo.

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

**Pendiente:** cómo se entrega. Opciones:
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

- [ ] Comprobar qué versión de la búsqueda por voz de Google para Android TV funciona en 7.1 (sección 4.6).
- [ ] Confirmar si el usuario tiene, o va a comprar, un control con micrófono.

- [ ] Elegir launcher, priorizando el más ligero.
- [x] ~~Decidir si se quitan los servicios de Google~~ → **Se conservan.**
- [ ] Nombre de la ROM y logo.
- [ ] Forma de entrega.
- [ ] Lista final de apps a borrar.
- [ ] Valores de rendimiento.
- [ ] Decidir qué hacer con la configuración del sistema.
- [x] ~~Confirmar la RAM de la cajita~~ → **No se sabe.** La ROM detecta la RAM sola y se ajusta (sección 3).
