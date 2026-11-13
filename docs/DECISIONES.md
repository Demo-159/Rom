# ROM personalizada para Tanix TX3 Mini: decisiones

Registro de lo decidido y lo pendiente. Se actualiza en cada paso.

Última actualización: 13 de noviembre de 2026.

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

Descartado por ahora:
- **Linux desde cero (CoreELEC / Armbian):** es viable, porque el TX3 Mini está soportado en Linux oficial, pero no es Android y no permite usar apps de Android.
- **Subir a Android 9:** solo se podría adaptando la ROM de otra cajita con S905W, como la X96 Mini. Tiene riesgo de quedarse sin WiFi (hay varias revisiones del chip), de que no arranque y de que vaya lenta con 1 GB de RAM. Queda como experimento aparte, después de terminar la versión 7.1.

## 3. Cambios aprobados en la ROM

### 3.1 Identificarse como Android TV ✅
**Decisión: la ROM se declarará como dispositivo Android TV.**

- **Problema:** la ROM original no declara las funciones `android.software.leanback` ni `android.hardware.type.television`. Así lo comprobé en `/system/etc/permissions`.
- **Consecuencia:** el Play Store oculta las apps marcadas "solo Android TV", como Projectivy Launcher, y esas apps no se instalan desde APK.
- **Cambio:** añadir un archivo de permisos en `/system/etc/permissions/` que declare las dos funciones.
- **Efecto esperado:** aparecen las apps de TV en el Play Store, y algunas apps, como YouTube, cambian a su versión para TV, que se maneja mejor con el control remoto.

### 3.2 Quitar apps que sobran ✅
Lista a confirmar antes de compilar:
- Apps preinstaladas de `/system/fast_preinstall/` (las apps vietnamitas, entre otras) y Netflix de `/system/preinstall/`.
- Chrome duplicado: está en `fast_preinstall` y en `/system/app/Chrome`.
- Apps de prueba o del fabricante: `FactoryTest_oranth`, `Dig`, `LedStatus` y el launcher `Oranth_Launcher_720P`, que se reemplaza (ver 3.5).
- Hay que revisar las demás, una por una, antes de borrarlas.

### 3.3 Root + ADB ✅
- La ROM ya trae ADB como root activado: `ro.debuggable=1`, `service.adb.root=1`, `ro.adb.secure=0` y ADB por red en el puerto 5555.
- El `su` que trae (`/system/xbin/su`) solo lo puede usar ADB, no las apps.
- **Decisión:** root para apps con **Magisk v27.0**, aplicado a `boot.img` desde la PC. El parche se hace replicando su `boot_patch.sh` con `magiskboot` para x86_64. Magisk ya está descargado para hacer pruebas.
- No hay dm-verity en `/system` (comprobado en `fstab.amlogic`), así que modificar `system` no impide el arranque.

### 3.4 Rendimiento ✅
- Animaciones más rápidas.
- Ajustes de memoria pensados para equipos de 1 a 2 GB de RAM.
- Ajustes en `build.prop`.

Los valores exactos están pendientes de definir.

### 3.5 Marca propia ✅ (datos pendientes)
- Nombre del modelo y de la versión en `build.prop`.
- Logo de arranque (partición `logo`, formato `AML_RES`, imagen `bootup`).
- Animación de arranque (`/system/media/bootanimation.zip`).
- **Pendiente:** nombre de la ROM y logo. Si el usuario no trae un logo, se diseña uno sencillo con el nombre.

## 4. Launcher (pantalla de inicio)

**Estado: todavía no se ha elegido.** El launcher del fabricante (`Oranth_Launcher_720P`) se va a reemplazar porque se ve feo.

### Requisitos
- Funcionar en **Android 7.1** (API 25). Varios launchers modernos piden Android 8 o 9.
- Para meterlo dentro de la ROM, tiene que ser de código abierto con licencia que lo permita. Si no lo es, el usuario lo instala él mismo desde el Play Store.
- Estilo preferido: **tipo Google TV, sin banner**. Barra superior con buscador y pestañas, fila de "Recomendaciones" en tarjetas, fila de "Tus aplicaciones" en íconos redondos y fondo con olas de luz animadas. Es la maqueta D: https://claude.ai/artifact/X1kyuUFDTHFeStmKi3WTAN
- El usuario prefiere **un launcher ya hecho** antes que uno programado desde cero.

### Candidatos que funcionan en Android 7.1
| Launcher | Licencia | ¿Se puede meter en la ROM? | Notas |
|---|---|---|---|
| Projectivy Launcher | Cerrada (gratis, con funciones de pago) | No: se instala desde el Play Store | Pide Android 6+. Muy personalizable. Necesita el cambio 3.1 para instalarse. |
| Arc Launcher | GPL-3.0 | Sí | Estilo Apple TV, en español. Se puede dejar preconfigurado con el estilo D (fondo y color de acento). |
| Aura TV | MIT | Sí | Efecto de vidrio esmerilado y centro de control. En 7.1 pierde "seguir viendo" y las vistas previas. |
| GlassTV | MIT | Sí | Muy ligero (tiene versión para Android 4.4), reloj grande, en español. |
| Luncher | MIT | Sí | Organizado por categorías; no está en español. |
| LTvLauncher / FLauncher | GPL-3.0 | Sí | Más clásicos. |

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

## 5. Configuración del sistema

Al usuario no le gusta el apartado de configuración. La ROM trae tres apps distintas: `Settings` (Android de celular), `Settings_oranth` y `iTvSettings` (Amlogic).

Como la ROM está firmada con *test-keys* públicas, se pueden modificar y volver a firmar las apps del sistema.

**Pendiente:** decidir entre retocar la configuración actual o apoyarse en el centro de control del launcher elegido.

## 6. Entrega de la ROM

La ROM final pesa más de 1 GB. GitHub no acepta archivos de más de 100 MB, así que el repositorio guarda solo herramientas, scripts y documentación.

**Pendiente:** cómo se entrega. Opciones:
1. Enlace de descarga temporal. Requiere permiso para subirla a un servicio externo.
2. Partes de menos de 100 MB dentro del repositorio.
3. Script para generarla en la PC del usuario.

## 7. Datos técnicos comprobados

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

## 8. Pendientes (resumen)

- [ ] Elegir launcher.
- [ ] Nombre de la ROM y logo.
- [ ] Forma de entrega.
- [ ] Lista final de apps a borrar.
- [ ] Valores de rendimiento.
- [ ] Decidir qué hacer con la configuración del sistema.
- [ ] Confirmar si la cajita del usuario tiene 1 GB o 2 GB de RAM.
