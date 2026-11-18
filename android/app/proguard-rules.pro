# Flutter Engine y Plugins
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.embedding.** { *; }
-keep class io.flutter.provider.** { *; }

# Media3 / ExoPlayer & Transformer (Garantiza reproduccion fluida y recorte de video)
-keep class androidx.media3.decoder.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.transformer.** { *; }
-keep class androidx.media3.effect.** { *; }
-dontwarn androidx.media3.**

# SharedPreferences, ImagePicker y FilePicker
-keep class com.mr.flutter.plugin.filepicker.** { *; }
-keep class io.flutter.plugins.sharedpreferences.** { *; }
-keep class io.flutter.plugins.imagepicker.** { *; }

# Motores de Live Wallpaper y Servicio
-keep class com.ixeken.gakuu.engines.** { *; }
-keep class com.ixeken.gakuu.IxekenWallpaperService** { *; }

# Proteccion de fuentes y recursos de assets de Flutter
-keepclassmembers class * {
    *** get*();
    *** set*(***);
}

# Ignorar componentes opcionales de Google Play Core (PlayStoreDeferredComponentManager)
-dontwarn com.google.android.play.core.**

