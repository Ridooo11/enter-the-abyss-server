package com.abyssdev.entertheabyss;

import com.abyssdev.entertheabyss.pantallas.MenuInicio;
import com.abyssdev.entertheabyss.pantallas.PantallaWin;
import com.abyssdev.entertheabyss.ui.Imagenes;
import com.abyssdev.entertheabyss.ui.Sonidos;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class EnterTheAbyssPrincipal extends Game {
    public SpriteBatch batch; // SpriteBatch usado por todas las pantallas que va a tener el juego
    private Preferences prefs;

    @Override
    public void create() {
        batch = new SpriteBatch();

        // ✅ CARGAR TODAS LAS IMÁGENES UNA SOLA VEZ
        Imagenes.cargar();

        // Inicializar preferencias
        prefs = Gdx.app.getPreferences("EnterTheAbyss_Settings");

        float volumenMusica = prefs.getFloat("volumenMusica", .2f);
        float volumenEfectos = prefs.getFloat("volumenEfectos", .2f);

        // Inicializar sonidos y aplicar volúmenes
        Sonidos.cargar();
        Sonidos.setVolumenMusica(volumenMusica);
        Sonidos.setVolumenEfectos(volumenEfectos);

        Gdx.app.addLifecycleListener(new com.badlogic.gdx.LifecycleListener() {
            @Override
            public void pause() {}

            @Override
            public void resume() {}

            @Override
            public void dispose() {
                System.out.println("❌ Ventana cerrada - Limpiando recursos");

                // Asegurar que la pantalla actual se dispose correctamente
                if (getScreen() != null) {
                    getScreen().dispose();
                }
            }
        });


        // Arrancar en el menú
        setScreen(new MenuInicio(this,batch));
    }

    @Override
    public void dispose() {
        System.out.println("🔴 Dispose del juego principal");

        // Limpiar pantalla actual
        if (getScreen() != null) {
            getScreen().dispose();
        }

        batch.dispose();
        Sonidos.dispose();
        Imagenes.dispose(); // ✅ LIBERAR IMÁGENES

    }


    public Preferences getPreferencias() {
        return prefs;
    }
}
