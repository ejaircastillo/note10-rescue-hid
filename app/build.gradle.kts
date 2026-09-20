import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * PIN embebido OPCIONAL para el envío directo (build privado).
 *
 * Se lee de `pin-local.properties` en la raíz del proyecto, que está en .gitignore:
 * el PIN nunca entra al repositorio ni al APK público. Si el archivo no existe (o no
 * tiene la clave `pin`), el campo queda vacío, la app funciona igual y el botón de
 * envío directo avisa que no hay PIN.
 *
 * Para forzar el build público aunque el archivo exista:
 *     ./gradlew assembleDebug -PskipPin=true
 *
 * Se embebe ofuscado (XOR 0x5A + hex, igual que PinVault.decode) para que no aparezca
 * como texto plano en el .dex. Es ofuscación, no criptografía: el APK privado no se
 * publica ni se comparte.
 */
val pinLocalFile = rootProject.file("pin-local.properties")
val skipPin = (project.findProperty("skipPin") as String?) == "true"
val embeddedPin: String = if (!skipPin && pinLocalFile.exists()) {
    Properties().apply { pinLocalFile.inputStream().use { load(it) } }
        .getProperty("pin", "")
        .trim()
} else {
    ""
}

fun obfuscatePin(pin: String): String =
    pin.map { ((it.code xor 0x5A) and 0xFF).toString(16).padStart(2, '0') }.joinToString("")

if (embeddedPin.isEmpty()) {
    println("pin-local.properties: sin PIN embebido (build público${if (skipPin) ", -PskipPin" else ""})")
} else {
    println("pin-local.properties: PIN embebido de ${embeddedPin.length} dígitos -> APK PRIVADO, no publicar")
}

android {
    namespace = "com.ejair.note10rescue"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ejair.note10rescue"
        minSdk = 24
        targetSdk = 34
        versionCode = 6
        versionName = "1.0.5"

        // PIN ofuscado (o cadena vacía en el build público).
        buildConfigField("String", "EMBEDDED_PIN_OBFUSCATED", "\"${obfuscatePin(embeddedPin)}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
