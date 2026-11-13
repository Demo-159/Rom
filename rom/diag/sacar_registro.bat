@echo off
rem ROM "TV": saca el registro de la cajita por la red (versión de prueba, ADB abierto en el puerto 5555).
rem Pon este archivo dentro de la carpeta "platform-tools" y ábrelo con doble clic.
cd /d "%~dp0"
echo.
echo  La IP de la cajita aparece en Aerial: engranaje - Personalizar - debajo del codigo QR
echo  (por ejemplo 192.168.1.50, sin el ":8790").
echo.
set /p IP="Escribe la IP de la cajita y pulsa Enter: "
adb disconnect >nul 2>&1
adb connect %IP%:5555
adb -s %IP%:5555 root >nul 2>&1
timeout /t 3 >nul
adb connect %IP%:5555 >nul 2>&1
echo.
echo  Ahora abre los Ajustes en la TV hasta que se pegue, espera 15 segundos
pause
set OUT=registro-TV
mkdir %OUT% 2>nul
adb -s %IP%:5555 logcat -d -v threadtime > %OUT%\logcat.txt
adb -s %IP%:5555 logcat -d -b crash > %OUT%\crash.txt
adb -s %IP%:5555 shell dmesg > %OUT%\dmesg.txt
adb -s %IP%:5555 shell cat /data/anr/traces.txt > %OUT%\anr.txt
adb -s %IP%:5555 shell getprop > %OUT%\props.txt
adb -s %IP%:5555 shell ps > %OUT%\procesos.txt
adb -s %IP%:5555 shell "dmesg | grep avc" > %OUT%\selinux.txt
echo.
echo  Listo. Manda los archivos de la carpeta %OUT%
explorer %OUT%
pause
