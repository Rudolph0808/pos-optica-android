# POS Óptica — app Android nativa

App de punto de venta para tablet Android, sin depender de internet ni de ningún inicio de sesión. Está hecha con Capacitor: la interfaz (catálogo, carrito, recibos) es HTML/CSS/JS normal (carpeta `www/`), empacada dentro de un proyecto Android real (carpeta `android/`) con un complemento nativo propio para hablar directo con impresoras térmicas por USB.

## ¿Por qué necesito GitHub para esto?

Compilar el `.apk` final requiere el Android SDK de Google, algo que normalmente se instala con Android Studio. Como no tienes por qué instalar Android Studio solo para esto, este proyecto incluye un flujo de **GitHub Actions** que compila el APK automáticamente en la nube (gratis) cada vez que subes el código — tú solo descargas el archivo `.apk` ya listo.

## Paso 1: Sube este proyecto a GitHub

1. Entra a [github.com](https://github.com) e inicia sesión (o crea una cuenta gratis si no tienes).
2. Crea un repositorio nuevo: botón verde "New" → ponle un nombre, por ejemplo `pos-optica` → **no** marques "Add a README" → "Create repository".
3. En tu computadora, abre una terminal dentro de esta carpeta del proyecto y ejecuta (reemplazando la URL por la que te muestra GitHub en tu repositorio nuevo):

   ```bash
   git init
   git add .
   git commit -m "Primera versión de la app POS Óptica"
   git branch -M main
   git remote add origin https://github.com/TU-USUARIO/pos-optica.git
   git push -u origin main
   ```

   Si nunca has usado git desde tu computadora, es más sencillo instalar **GitHub Desktop** (https://desktop.github.com), abrir esta carpeta ahí como repositorio local, y usar los botones "Commit" y "Push" en vez de los comandos de arriba.

## Paso 2: Descarga el APK compilado

1. En GitHub, entra a la pestaña **Actions** de tu repositorio.
2. Verás un flujo llamado "Compilar APK Android" corriendo (tarda 3-5 minutos la primera vez).
3. Cuando termine (ícono verde ✓), ábrelo y baja hasta la sección **Artifacts**.
4. Descarga `pos-optica-apk` — es un .zip que contiene `app-debug.apk`.

## Paso 3: Instala el APK en la tablet

1. Pasa el archivo `app-debug.apk` a la tablet (por USB, correo, Google Drive, WhatsApp Web, lo que te sea más fácil).
2. En la tablet, abre el archivo desde el explorador de archivos o notificaciones de descarga.
3. Android pedirá permiso para "instalar apps de origen desconocido" la primera vez — actívalo solo para esa app/origen.
4. Instala. Te va a quedar un ícono de "POS Óptica" en la pantalla de la tablet, como cualquier otra app.

## Paso 4: Conecta la impresora

1. Conecta la impresora térmica a la tablet con un cable USB‑OTG.
2. Abre la app → pestaña **Config** → "Conectar impresora".
3. Android te va a pedir permiso de acceso al dispositivo USB la primera vez — acéptalo. Las siguientes veces se reconecta solo.
4. Usa "Imprimir ticket de prueba" para confirmar que todo funciona.

## Qué incluye la app

- Catálogo de productos y servicios por categoría (editable).
- Carrito de venta con cálculo de subtotal, IVA y total.
- Pago en efectivo (con cambio), tarjeta o transferencia.
- Impresión de recibos directo a la impresora térmica USB, con folio consecutivo.
- Historial de ventas con reimpresión.
- Respaldo de tus datos (productos, ventas, configuración) guardado como archivo en la tablet, compartible a donde quieras.
- Todo funciona sin internet: los datos se guardan solo en la tablet.

## Estructura del proyecto

```
www/                    → la interfaz de la app (HTML/CSS/JS)
android/                → proyecto Android generado por Capacitor
android/app/src/main/java/com/rodo/posoptica/EscposUsbPlugin.java
                        → complemento nativo que habla con la impresora por USB
.github/workflows/android.yml
                        → receta que GitHub usa para compilar el APK automáticamente
```

## Para hacer cambios después

Cualquier cambio a `www/index.html` (por ejemplo agregar productos por defecto, cambiar colores, textos) solo requiere volver a subir el cambio a GitHub (`git add . && git commit -m "..." && git push`) — Actions vuelve a compilar el APK automáticamente y lo descargas de nuevo desde la pestaña Actions.
