# Cómo continuar el proyecto (traspaso)

Guía para que otra sesión (u otra persona) siga desde donde quedó. Las decisiones y su porqué están en
`docs/DECISIONES.md`; la instalación en el TV box, en `docs/INSTALAR.md`.

## Qué es

ROM "TV" para el **Tanix TX3 Mini** (Amlogic S905W, placa p281, 2 GB de RAM) del usuario.

- **Base actual:** *Aidan's ROM v9* (Android TV 9). Hay que modificarla, no crear una ROM desde cero.
- **Script actual:** `rom/build_rom_atv9.py`.
- **Base anterior, abandonada:** el firmware de fábrica con Android 7.1 (`rom/build_rom.py`). Queda en el repositorio porque el script nuevo reutiliza sus funciones.

## Preferencias del usuario (respetarlas)

- Escribe en español de Chile y prefiere respuestas cortas y directas.
- **No puede hacer pruebas en el TV box** más allá de instalar la ROM y contar qué pasa. Tampoco puede usar ADB.
- Prioridad absoluta: **rendimiento, poca RAM y fluidez**, sin romper nada.
- Mantener los servicios de Google y que el equipo se identifique como Android TV.
- **Marca blanca "TV":** sin logo de arranque (negro) y con la animación de puntos (`rom/tools/make_bootanim.py`).
- **Launcher:** Aerial modificado (`launcher/`) y ningún otro como respaldo. El clima por defecto es de Paillaco, Chile.
- **YouTube:** TizenTube Cobalt aparece como "YouTube", con el banner oficial. El puente de voz es `rom/bridge`.
- **Sin root:** ya no lo quiere. Magisk impedía arrancar.
- **Explorador de archivos:** se mantiene el de la base ("Gestor de archivos").
- **Control remoto:** es **USB** (receptor 2,4 GHz con micrófono). El equipo no debe entrar en suspensión profunda, porque sin energía en los puertos USB el control no lo puede volver a encender.
- **HDMI-CEC:** dejarlo como viene en la base.
- **Entrega:** un único archivo zip subido a Gofile, con el enlace. El usuario aprobó usar Gofile:
  `curl -F file=@TV-TX3Mini-ATV9.zip https://upload.gofile.io/uploadfile`
- **"TV en vivo"** (`tvlive/`, reproductor m3u8): **no** va en la ROM, porque el usuario lo pidió así. Se entrega como APK aparte.

## Estado (2026-11-14)

- **Última ROM entregada:** https://gofile.io/d/ZMGnZR0g (MD5 del zip `7e65113cba0d4feabe7217ccdb9b8225`). No se ha probado en el equipo.
  - Arreglo del control USB: wakelock del kernel y `KeepAwake` de Aerial como respaldo.
  - Dominio SELinux `tvtune` con los ajustes de CPU, memoria y eMMC.
  - zram con LZ4 y `sys.use_fifo_ui`.
  - Bloqueo de telemetría en `hosts`, ajustes de privacidad y sin Android TV Remote Service.
- **Última ROM que el usuario confirmó que funciona "casi de maravilla":** https://gofile.io/d/B8vBCL5q. Si la nueva no arranca, volver a esa.
- **Pendiente de confirmar por el usuario:**
  - Que la ROM nueva arranque. Si no arranca, el sospechoso principal es la política SELinux: `rom/atv9/sepolicy/tvtune.cil`. Para descartarlo, compilar sin `add_tuning`.
  - Que el control USB vuelva a encender el equipo.
  - Que se sienta más rápido.

## Cómo reconstruir

Corre en Linux como root (monta imágenes por loop).

1. **Herramientas:** `simg2img`, `e2fsprogs`, `zerofree`, `secilc`, `unrar`, `abootimg`, Python 3 con Pillow, JDK 17 y el Android SDK (`build-tools` 36.0.0, `platform` 36) en `/home/user/sdk`.
2. **Imagen base (no está en el repositorio, pesa 1,3 GB):**
   - Descargar de MediaFire: https://www.mediafire.com/file/6v21t5bsbunlvh3/
   - Comprobar el MD5 del `.rar`: `381357d804ec789e58b3b40f3825c48c`.
   - Extraer con `unrar`. `7z` y `unar` fallan con este RAR5.
3. **Compilar Aerial:** `cd launcher && ./gradlew assembleStandardRelease`.
4. **Armar la ROM:**
   `sudo python3 rom/build_rom_atv9.py AIDAN.img OUT.img`
   El script hace todo esto:
   - Quita lo que lista `rom/atv9/debloat.txt`.
   - Instala Aerial, TizenTube (lo descarga de GitHub) y YouTubeBridge.
   - Cambia las propiedades de `rom/atv9/build.prop.txt`.
   - Lleva zram a 512 MB.
   - Agrega `tvtune` y **compila la política SELinux igual que el equipo**. Si no compila, no genera la imagen.
   - Pone el logo en negro y recalcula los VERIFY.
5. **Verificar:** sumas VERIFY, CRC de la imagen, etiquetas SELinux y que solo cambien `system`, `vendor` y `logo` (ver los historiales de commits). `tools/aml_image_unpack.py` rearma la imagen de Aidan idéntica byte a byte.

## Lo que solo existía en la máquina de esta sesión

- **Clave de firma de Aerial, YouTubeBridge y TV en vivo:** es la clave de depuración de esa máquina, `~/.android/debug.keystore`. Una sesión nueva genera otra clave.
  - **Lo que afecta:** una ROM nueva instala bien, pero no se puede actualizar un Aerial ya instalado con un APK firmado con otra clave.
  - **Si importa:** pedirle al usuario que guarde esa clave, o firmar desde ahora con una clave fija guardada fuera del repositorio.
- **Claves de prueba de AOSP** (`platform`, `testkey`): se vuelven a bajar de android.googlesource.com (rama nougat-mr2-release, `build/target/product/security`). Solo las usaba la base 7.1.
- **Imágenes base:**
  - La de Aidan: ver el paso 2.
  - La de fábrica 7.1: https://drive.google.com/file/d/1li5i9HpIWogXbDkBvArNjVJCBB6QOsOL/view
- **Emulador Android TV 7.1 x86 (`tv25`):** sirve para probar apps sueltas. Es lento, no tiene KVM y su decodificador de video por software falla. Tampoco confía en el certificado del proxy de la sesión, así que las pruebas de red usan HTTP local en `10.0.2.2`.

## Datos técnicos útiles

- **Base Aidan:**
  - Android 9, compilación "user", SELinux activo, kernel 4.9.113.
  - System-as-root: la carpeta `system/` está dentro de la partición `system`.
  - Las imágenes `system` y `vendor` son ext4 sin formato sparse.
  - `vbmeta` tiene la verificación desactivada (flags 2) y el fstab no usa verity.
- **SELinux en la base:**
  - Init compila la política en cada arranque: no hay `precompiled_sepolicy`, y compila con `-N`.
  - Init **no** puede escribir en `/sys` genérico, en la CPU, en `swappiness` ni en `wake_lock`. Para eso está el dominio `tvtune`.
- **Pantalla:** la interfaz se dibuja a la resolución de salida (`realoutputmode`). La salida se limita a 1080p con `ro.vendor.platform.support.4k=false`. `vendor.ui_mode` solo acepta "1080".
- **Ajustes del primer arranque** (animaciones, ubicación, país del Wi-Fi, apps en caché): los aplica Aerial en `launcher/.../system/RomSetup.kt`.
