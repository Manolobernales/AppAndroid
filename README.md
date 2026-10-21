# FirmaPDF para Android

![FirmaPDF](logo.svg)

**Firma PDF desde tu teléfono Android, con firma manuscrita y envío a otras aplicaciones.**

FirmaPDF permite abrir un PDF, dibujar una firma con el dedo, colocarla sobre la página, imprimir el nombre del firmante y generar una copia del documento. Al terminar, se puede compartir el PDF mediante el menú nativo de Android.

## Descargar

La versión más reciente estará disponible en **[Releases](https://github.com/Manolobernales/AppAndroid/releases/latest)**.

1. Abre la página de Releases desde tu teléfono Android.
2. Descarga el archivo `FirmaPDF-Android-v0.6.0.apk`.
3. Abre el APK y sigue los pasos de Android para instalarlo. El sistema puede advertir que se trata de una aplicación instalada fuera de Google Play.
4. Abre FirmaPDF, selecciona un PDF y firma.

## Funciones

- Abrir archivos PDF desde el selector de archivos o desde otra aplicación.
- Dibujar la firma con el dedo y arrastrarla a la ubicación deseada.
- Mostrar el nombre del firmante encima de la firma.
- Confirmar expresamente la intención de firmar.
- Guardar el PDF firmado y un registro JSON complementario con fecha y huellas SHA-256.
- Compartir el PDF firmado con WhatsApp, correo u otras aplicaciones compatibles.
- Procesar los documentos en el teléfono; no se suben a un servidor de FirmaPDF.

## Privacidad y limitaciones

FirmaPDF es una herramienta práctica para insertar una firma manuscrita en un PDF. **No genera una firma digital certificada ni verifica por sí sola la identidad del firmante.** El archivo JSON de auditoría es complementario y tampoco está firmado criptográficamente.

La aplicación necesita conexión a internet para cargar las bibliotecas PDF.js y pdf-lib desde sus CDN. El procesamiento del documento se realiza localmente.

## Seguridad

Instala APK únicamente desde este repositorio y revisa los cambios publicados. Android puede mostrar una advertencia para aplicaciones que se instalan fuera de Google Play. Esa advertencia no demuestra por sí sola que la aplicación sea maliciosa, pero tampoco equivale a una certificación de seguridad.

## Compilar desde el código fuente

El código está organizado como un proyecto Android con Kotlin, Gradle y una interfaz local WebView. En la pestaña **Actions**, el flujo de publicación compila el APK y lo adjunta a la versión de GitHub.

## Versión

- Aplicación: FirmaPDF 0.6.0
- Android: API mínima 29
- Paquete: `pe.firmawhatsapp.app`

---
Desarrollada como herramienta independiente para uso personal. No está afiliada a WhatsApp ni a Google.
