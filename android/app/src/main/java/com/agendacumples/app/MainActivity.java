package com.agendacumples.app;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AgendaNativePlugin.class);
        // Aspecto elegido por la persona (automático, claro u oscuro).
        String theme = getSharedPreferences("cfg", MODE_PRIVATE).getString("theme", "auto");
        AppCompatDelegate.setDefaultNightMode(AgendaNativePlugin.nightModeFor(theme));
        super.onCreate(savedInstanceState);

        // Android dibuja la app bajo la barra de estado y la de navegación: dejamos ese espacio libre.
        final View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
        applyBarIcons();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyBarIcons();
    }

    /** Iconos oscuros sobre fondo claro y claros sobre fondo oscuro. */
    private void applyBarIcons() {
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(!night);
        c.setAppearanceLightNavigationBars(!night);
    }
}
