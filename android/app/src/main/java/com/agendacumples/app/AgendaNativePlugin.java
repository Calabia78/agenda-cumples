package com.agendacumples.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Base64;

import androidx.activity.result.ActivityResult;
import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Funciones nativas propias:
 *  - pickFile: elegir un archivo (el CSV exportado) y devolver su contenido.
 *  - deleteFile: borrar ese archivo tras importarlo.
 *  - openUrl: abrir un enlace fuera de la app (WhatsApp).
 *  - installApk: descargar una actualización e iniciar su instalación.
 */
@CapacitorPlugin(name = "AgendaNative")
public class AgendaNativePlugin extends Plugin {

    private static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_APK_BYTES = 80 * 1024 * 1024;

    /** Fondo de ventana e iconos de las barras del sistema según el aspecto elegido. */
    static void applyAppearance(Activity activity, boolean dark) {
        activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(dark ? 0xFF121010 : 0xFFF7F7F6));
        androidx.core.view.WindowInsetsControllerCompat c = androidx.core.view.WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        c.setAppearanceLightStatusBars(!dark);
        c.setAppearanceLightNavigationBars(!dark);
    }

    @PluginMethod
    public void setTheme(final PluginCall call) {
        final boolean dark = "dark".equals(call.getString("mode", "light"));
        getContext().getSharedPreferences("cfg", android.content.Context.MODE_PRIVATE).edit().putString("theme", dark ? "dark" : "light").apply();
        final Activity activity = getActivity();
        if (activity == null) {
            call.resolve();
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                applyAppearance(activity, dark);
                call.resolve();
            }
        });
    }

    @PluginMethod
    public void pickFile(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(call, intent, "pickResult");
    }

    @ActivityCallback
    private void pickResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            JSObject r = new JSObject();
            r.put("cancelled", true);
            call.resolve(r);
            return;
        }
        Uri uri = result.getData().getData();
        try {
            String name = "";
            Cursor c = getContext().getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) name = c.getString(0);
                } finally {
                    c.close();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = getContext().getContentResolver().openInputStream(uri);
            if (in == null) {
                call.reject("No se pudo abrir el archivo");
                return;
            }
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_FILE_BYTES) {
                        call.reject("El archivo es demasiado grande");
                        return;
                    }
                }
            } finally {
                in.close();
            }
            JSObject r = new JSObject();
            r.put("cancelled", false);
            r.put("name", name == null ? "" : name);
            r.put("uri", uri.toString());
            r.put("data", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
            call.resolve(r);
        } catch (Exception e) {
            call.reject("No se pudo leer el archivo: " + e.getMessage());
        }
    }

    @PluginMethod
    public void deleteFile(PluginCall call) {
        String uriStr = call.getString("uri");
        boolean deleted = false;
        if (uriStr != null && !uriStr.isEmpty()) {
            try {
                Uri uri = Uri.parse(uriStr);
                deleted = DocumentsContract.deleteDocument(getContext().getContentResolver(), uri);
            } catch (Exception e) {
                deleted = false;
            }
        }
        JSObject r = new JSObject();
        r.put("deleted", deleted);
        call.resolve(r);
    }

    @PluginMethod
    public void openUrl(PluginCall call) {
        String url = call.getString("url");
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
            call.reject("Enlace no válido");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (ActivityNotFoundException e) {
            call.reject("No hay ninguna aplicación para abrir el enlace");
        }
    }

    @PluginMethod
    public void installApk(final PluginCall call) {
        final String url = call.getString("url");
        if (url == null || !url.startsWith("https://")) {
            call.reject("Enlace no válido");
            return;
        }
        final Activity activity = getActivity();
        if (activity == null) {
            call.reject("La app no está en primer plano");
            return;
        }
        // Android pide un permiso especial para instalar APK desde una app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            try {
                Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(s);
            } catch (Exception e) {
                call.reject("No se pudo abrir el permiso de instalación: " + e);
                return;
            }
            JSObject r = new JSObject();
            r.put("needsPermission", true);
            call.resolve(r);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File dir = new File(activity.getCacheDir(), "updates");
                    if (!dir.exists()) dir.mkdirs();
                    File[] old = dir.listFiles();
                    if (old != null) for (File f : old) f.delete();
                    final File apk = new File(dir, "cumples-update.apk");

                    HttpURLConnection conn = null;
                    String current = url;
                    for (int i = 0; i < 6; i++) {
                        conn = (HttpURLConnection) new URL(current).openConnection();
                        conn.setInstanceFollowRedirects(false);
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(30000);
                        int code = conn.getResponseCode();
                        if (code >= 300 && code < 400) {
                            String loc = conn.getHeaderField("Location");
                            conn.disconnect();
                            if (loc == null || !loc.startsWith("https://")) throw new Exception("Redirección no válida");
                            current = loc;
                            conn = null;
                            continue;
                        }
                        if (code != 200) throw new Exception("El servidor respondió " + code);
                        break;
                    }
                    if (conn == null) throw new Exception("Sin respuesta");
                    long total = 0;
                    InputStream in = conn.getInputStream();
                    OutputStream out = new FileOutputStream(apk);
                    try {
                        byte[] buf = new byte[32768];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            total += n;
                            if (total > MAX_APK_BYTES) throw new Exception("Archivo demasiado grande");
                        }
                    } finally {
                        out.close();
                        in.close();
                        conn.disconnect();
                    }
                    if (total < 100000) throw new Exception("Descarga incompleta (" + total + " bytes)");
                    final Uri apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
                    final long size = total;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Intent install = new Intent(Intent.ACTION_VIEW);
                                install.setDataAndType(apkUri, "application/vnd.android.package-archive");
                                install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                activity.startActivity(install);
                                JSObject r = new JSObject();
                                r.put("started", true);
                                r.put("bytes", size);
                                call.resolve(r);
                            } catch (Exception e) {
                                call.reject("No se pudo abrir el instalador: " + e);
                            }
                        }
                    });
                } catch (Exception e) {
                    call.reject("No se pudo descargar la actualización: " + e);
                }
            }
        }).start();
    }
}
