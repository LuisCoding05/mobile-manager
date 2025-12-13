# Scrcpy Manager

Aplicación Spring Boot sencilla para lanzar `scrcpy` sobre dispositivos conectados por `adb`.

Características
- Lista dispositivos detectados por `adb devices`.
- Botón para lanzar `scrcpy` por dispositivo.
- Botón para detener (`Desconectar`) una sesión `scrcpy` iniciada desde la app.
- Botón `Refrescar` y opción de `Auto-refrescar cada 5s` para actualizar la lista.

Configuración
- `src/main/resources/application.properties` contiene:
  - `adb.path` : ejecutable `adb` (por defecto `adb` en PATH).
  - `scrcpy.path` : carpeta donde ejecutar `scrcpy` (por defecto `/home/adrian/scrcpy`).

Notas de uso
- Si no hay dispositivos conectados al entrar en `/` verás un mensaje informativo "No hay dispositivos conectados." y podrás pulsar `Refrescar` para reintentar.
- Si conectas un dispositivo después de abrir la página, pulsa `Refrescar` o activa `Auto-refrescar`.

Windows
- En Windows ajusta `scrcpy.path` a la carpeta donde tengas `scrcpy.exe` y, si es necesario, `adb.path` para apuntar al `adb.exe` correcto.

Ejecución

```bash
mvn package
java -jar target/manager-0.0.1-SNAPSHOT.jar
```

Seguridad
- Esta aplicación está pensada para uso local. Si la expones en red, añade autenticación y CSRF protections.
# mobile-manager
Este proyecto es sencillo para portatiles locales para explicar a gente y familia que no sabe de ordenadores a conectar un mobil con scrcpy y conectarse sin usar comandos, ver dispositivos conectados por usb/dba....
Despliegue como servicio `systemd` (opción recomendada — `systemd --user`)
-----------------------------------------------------------------

Esta es la forma preferida para mantener la aplicación siempre disponible en un portátil Linux Mint (o similar). Ejecutar como servicio de usuario permite reinicios automáticos, logs via `journalctl` y acceso a la sesión gráfica si se ejecuta con las variables `DISPLAY` y `XAUTHORITY` apuntando a tu sesión.

1) Compila y copia el jar al directorio donde quieras ejecutarlo (ejemplo `~/manager`):

```bash
mvn -DskipTests package
mkdir -p ~/manager
cp target/manager-0.0.1-SNAPSHOT.jar ~/manager/
```

2) (Opcional pero recomendado) configura límites de memoria para la JVM editando la variable `JAVA_OPTS` en el archivo de unidad o exportándola.

3) Crea el archivo de unidad de systemd para el usuario en `~/.config/systemd/user/manager.service` con este contenido (ajusta rutas y usuario):

```
[Unit]
Description=Scrcpy Manager (Spring Boot)
After=network.target

[Service]
Environment=JAVA_OPTS=-Xms256m -Xmx512m
Environment=DISPLAY=:0
Environment=XAUTHORITY=/home/<tu-usuario>/.Xauthority
WorkingDirectory=/home/<tu-usuario>/manager
ExecStart=/usr/bin/java $JAVA_OPTS -jar /home/<tu-usuario>/manager/manager-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10
TimeoutStopSec=20

[Install]
WantedBy=default.target
```

4) Habilita que los servicios de usuario puedan iniciarse al arranque (linger) y arranca el servicio:

```bash
# permitir que el servicio del usuario pueda ejecutarse sin sesión interactiva
sudo loginctl enable-linger <tu-usuario>

# recargar y arrancar el servicio de usuario
systemctl --user daemon-reload
systemctl --user enable --now manager.service

# comprobar estado y ver logs
systemctl --user status manager.service
journalctl --user-unit manager.service -f
```

5) Variables importantes y notas:
- `DISPLAY` y `XAUTHORITY`: necesarios para que `scrcpy` abra ventanas en tu X session. Ajusta `:0` y la ruta de `.Xauthority` si usas Wayland o una sesión diferente.
- Para conocer los valores correctos desde tu sesión gráfica (ejecuta en la terminal de la sesión gráfica):
```bash
echo $DISPLAY
echo $XAUTHORITY
# Si $XAUTHORITY no está definido normalmente estará en ~/.Xauthority
ls -la ~/.Xauthority
```
- `scrcpy.path` en `application.properties`: debe apuntar a la carpeta donde quieras ejecutar `scrcpy` o donde esté su binario si necesita trabajo desde una carpeta específica.
- `adb` y permisos USB: crea reglas `udev` para permitir acceso a dispositivos sin sudo (por ejemplo `/etc/udev/rules.d/51-android.rules`) y añade tu usuario al grupo `plugdev`.

6) Problemas comunes
- Si `scrcpy` no muestra ventana: revisa `DISPLAY`/`XAUTHORITY` y que el servicio corre como el mismo usuario de la sesión gráfica.
- Si no detecta dispositivos: revisa `adb` (prueba `adb devices` en terminal) y las reglas udev.
- Si el sistema tiene poca RAM: reduce `-Xmx`, considera 1GB de swap si no hay suficiente memoria.

Errores en la app: cómo depurarlos
- Si ves una pantalla de error o redirecciones con `;jsessionid=` en la URL: eso ocurre cuando tu navegador no acepta cookies. Los contenedores web pueden entonces reescribir el ID de sesión en la URL usando `;jsessionid=...`. Evita esto activando cookies o usando un navegador que acepte cookies.
- Si la app devuelve una pantalla de error (500) en vez de mostrar el `flash` de error, revisa los logs del servicio para ver la excepción completa:
  - Para un servicio user (recomendado):
    ```bash
    systemctl --user status manager.service
    journalctl --user-unit manager.service -f
    ```
  - Para un servicio system-wide (si lo ejecutaste así como root):
    ```bash
    sudo systemctl status manager.service
    sudo journalctl -u manager.service -f
    ```
  - Si no ves stacktraces en la página de error pero quieres verlos en la plantilla en entorno local, añade al `application.properties`:
    ```properties
    # Muestra stacktraces en la página de error (solo para depuración local)
    server.error.include-stacktrace=always
    ```
  - Otra opción para ver más logging: ajustar el `logging.level` a `DEBUG` en `application.properties` o exportar `JAVA_OPTS=-Dlogging.level.mobile.personal.manager=DEBUG`.

  Logs de scrcpy y diagnóstico en la UI
  - He añadido una funcionalidad para ver los logs recientes de `scrcpy` por dispositivo directamente en la UI: pulsa `Logs` en la tarjeta del dispositivo y verás un modal con las últimas líneas capturadas (útil cuando `scrcpy` falla por permisos, X o falta de binario).
  - Si `scrcpy` falla al arrancar, la app intentará detectar la salida inicial y mostrar un snippet breve en el `flash` (mensaje de error) al intentar `Conectar`.
  - También existe un endpoint que devuelve logs en texto plano: `GET /logs/{deviceId}`.

7) Seguridad
- El servicio corre como tu usuario; si vas a exponer la app a la red, añade autenticación y limita accesos.

Si quieres, creo el archivo de unidad de ejemplo en `systemd/manager.service` en el repo para que puedas copiarlo fácilmente. ¿Lo añado? 
