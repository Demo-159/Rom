# Instalar la ROM "TV" en el Tanix TX3 Mini

Necesitas:
- Un PC con **Windows**.
- Un **cable USB macho a macho (USB-A a USB-A)**.
- Un **palillo de dientes** o un clip.

## 1. Unir y descomprimir
1. Descarga las dos partes, `TV-TX3Mini.zip.001` y `TV-TX3Mini.zip.002`, en la misma carpeta.
2. Instala **7-Zip** (gratis): https://www.7-zip.org
3. Haz clic derecho en `TV-TX3Mini.zip.001` → 7-Zip → **Extraer aquí**. 7-Zip une las dos partes solo.
4. Queda el archivo `TV-TX3Mini.img` (890 MB).

Si quieres comprobar que se descargó bien: en PowerShell, `Get-FileHash TV-TX3Mini.img` debe dar
`EEE54B757AB5C7E38A1212F0C7BD03F2024BBE5A618E4DBBFA02523642AADF3C`.

## 2. Instalar USB Burning Tool
- Descarga **Amlogic USB Burning Tool v2.1.6 o superior** (por ejemplo, desde el foro de la comunidad de Amlogic o el sitio de Tanix) e instálalo **con sus drivers**.

## 3. Grabar la ROM
1. Abre USB Burning Tool → **File → Import image** → elige `TV-TX3Mini.img`.
2. Deja marcados **Erase flash** y **Erase bootloader**. Así no quedan restos de la ROM anterior.
3. Pulsa **Start**.
4. Con la cajita **desenchufada de la corriente**:
   - Mete el palillo en el agujero del **conector de audio (AV)** y mantén presionado el botón que hay dentro.
   - Sin soltar el palillo, conecta el cable USB entre la cajita (puerto USB del lado) y el PC.
5. Cuando USB Burning Tool muestre que reconoció el equipo, suelta el palillo.
6. Espera a que llegue a **100 %**. Pulsa **Stop**, desconecta el USB y conecta la cajita a la corriente y a la TV.

## 4. Primer arranque
- El primero tarda **varios minutos**: Android prepara las apps. Los siguientes son rápidos.
- Pantalla de inicio: **Aerial**. Inicia sesión con tu cuenta de Google en el Play Store.
- **Recomendado:** Ajustes → Google → "Uso y diagnóstico" → **desactivar**.
- Para usar ADB: la TV pide autorizar el computador la primera vez.

## Si algo sale mal
- Repite los pasos 2 y 3 con la **ROM original** (`TX3Mini-17APK-20190110.img`, la de tu Google Drive). La cajita vuelve a quedar como venía.
- Prueba y cuéntame:
  - ¿Funciona el **WiFi**?
  - ¿Abre el **Play Store de TV**?
  - ¿El **micrófono** abre los videos en TizenTube?
  - ¿Qué tan rápido **arranca**?
