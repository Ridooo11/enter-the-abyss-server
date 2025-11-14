package com.abyssdev.entertheabyss.pantallas;

import com.abyssdev.entertheabyss.interfaces.GameController;
import com.abyssdev.entertheabyss.mapas.*;
import com.abyssdev.entertheabyss.personajes.*;
import com.abyssdev.entertheabyss.network.ServerThread;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.ArrayList;
import java.util.HashMap;

public class PantallaJuego extends Pantalla implements GameController {

    // 🌐 Red
    private ServerThread serverThread;
    private boolean juegoIniciado = false;
    private boolean servidorActivo = false;

    // 🗺️ Mundo
    private Mapa mapaActual;
    private Sala salaActual;

    // 👥 Jugadores
    private HashMap<Integer, Jugador> jugadores = new HashMap<>();
    private static final int MAX_JUGADORES = 2;

    // ⏱️ Ticks
    private float tiempoAcumulado = 0f;
    private static final float TICK_RATE = 1f / 30f; // 30 FPS lógica

    // 🎨 NUEVO: Renderizado del servidor
    private OrthographicCamera camara;
    private Viewport viewport;
    private BitmapFont font;
    private Texture texturaFade;
    private Texture spriteJugador;

    public PantallaJuego(Game juego, SpriteBatch batch) {
        super(juego, batch);
    }

    @Override
    public void show() {
        if (servidorActivo) {
            System.out.println("⚠️ Servidor ya activo, reiniciando...");
            limpiarCompletamente();
        }
        System.out.println("🖥️ Servidor iniciado");
        servidorActivo = true;

        camara = new OrthographicCamera();
        viewport = new FitViewport(32, 32 * (9f / 16f), camara);
        viewport.apply();

        font = new BitmapFont();
        font.getData().setScale(2f);
        font.setColor(Color.WHITE);

        texturaFade = generarTextura();
        spriteJugador = new Texture("personajes/player.png");

        serverThread = new ServerThread(this);
        serverThread.start();

        System.out.println("⏳ Esperando jugadores...");

        // Inicializar mapa y salas
        mapaActual = new Mapa("mazmorra1");
        mapaActual.agregarSala(new Sala("sala1", "maps/mapa1_sala1.tmx", 1, this.serverThread));
        mapaActual.agregarSala(new Sala("sala2", "maps/mapa1_sala2.tmx", 1, this.serverThread));
        mapaActual.agregarSala(new Sala("sala3", "maps/mapa1_sala5.tmx", 1, this.serverThread));
        mapaActual.agregarSala(new Sala("sala4", "maps/mapa1_sala4.tmx", 1, this.serverThread));
        mapaActual.agregarSala(new Sala("sala5", "maps/mapa2_posible.tmx", 1, this.serverThread));

        salaActual = mapaActual.getSala("sala1");
        mapaActual.establecerSalaActual("sala1");

        // 🔹 GENERAR ENEMIGOS DE LA SALA INICIAL
        System.out.println("👾 Generando enemigos iniciales...");
        salaActual.generarEnemigos();

    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // 🎨 NUEVO: Renderizar estado del servidor
        if (!juegoIniciado) {
            renderizarPantallaEspera();
            return;
        }

        // Actualizar lógica del juego
        tiempoAcumulado += delta;
        while (tiempoAcumulado >= TICK_RATE) {
            actualizarLogicaJuego(TICK_RATE);
            tiempoAcumulado -= TICK_RATE;
        }

        enviarActualizaciones();

        // 🎨 NUEVO: Renderizar el juego
        renderizarJuego(delta);
    }

    // 🎨 NUEVO: Método para renderizar pantalla de espera
    private void renderizarPantallaEspera() {
        batch.begin();

        String texto1 = "SERVIDOR ACTIVO";
        String texto2 = "Esperando " + (MAX_JUGADORES - serverThread.getConnectedClients()) + " jugador(es)...";
        String texto3 = "Jugadores conectados: " + serverThread.getConnectedClients() + "/" + MAX_JUGADORES;

        font.setColor(Color.GREEN);
        font.draw(batch, texto1,
            Gdx.graphics.getWidth() / 2f - 150,
            Gdx.graphics.getHeight() / 2f + 50);

        font.setColor(Color.YELLOW);
        font.draw(batch, texto2,
            Gdx.graphics.getWidth() / 2f - 200,
            Gdx.graphics.getHeight() / 2f);

        font.setColor(Color.WHITE);
        font.draw(batch, texto3,
            Gdx.graphics.getWidth() / 2f - 150,
            Gdx.graphics.getHeight() / 2f - 50);

        batch.end();
    }

    // 🎨 NUEVO: Método para renderizar el juego
    private void renderizarJuego(float delta) {
        // Actualizar cámara para seguir al primer jugador
        actualizarCamara();

        // Renderizar mapa
        salaActual.getRenderer().setView(camara);
        salaActual.getRenderer().render();

        batch.setProjectionMatrix(camara.combined);
        batch.begin();

        // Renderizar enemigos
        ArrayList<Enemigo> enemigos = salaActual.getEnemigos();
        if (enemigos != null) {
            for (Enemigo enemigo : enemigos) {
                if (!enemigo.debeEliminarse()) {
                    enemigo.renderizar(batch);
                }
            }
        }

        // Renderizar boss
        Boss boss = salaActual.getBoss();
        if (boss != null && !boss.debeEliminarse()) {
            boss.renderizar(batch);
        }

        // Renderizar jugadores (solo visuales)
        for (Jugador jugador : jugadores.values()) {
            dibujarJugadorServidor(jugador);
        }

        batch.end();

        // Renderizar UI de debug
        renderizarDebugUI();
    }

    // 🎨 NUEVO: Dibujar jugador en el servidor
    private void dibujarJugadorServidor(Jugador jugador) {
        // Obtener el frame del sprite (fila 0, columna 0 = idle mirando abajo)
        TextureRegion frameJugador = new TextureRegion(spriteJugador, 0, 0, 48, 48);

        // Aplicar color según el número de jugadores
        Color color = jugador.getNumeroJugador() == 1 ? Color.BLUE : Color.RED;
        batch.setColor(color);

        // Dibujar el sprite del jugador
        batch.draw(frameJugador,
            jugador.getX(),
            jugador.getY(),
            jugador.getAncho(),
            jugador.getAlto());

        // Restaurar color blanco
        batch.setColor(Color.WHITE);

        // Dibujar etiqueta "P1" o "P2" encima del jugador (más pequeño)
        font.getData().setScale(0.1f); // Más pequeño que antes (era 0.5f)

        // Color del texto según el jugador
        font.setColor(jugador.getNumeroJugador() == 1 ? Color.CYAN : Color.YELLOW);

        // Centrar el texto sobre el jugador
        String etiqueta = "P" + jugador.getNumeroJugador();
        GlyphLayout layout = new GlyphLayout(font, etiqueta);
        float textX = jugador.getX() + (jugador.getAncho() / 2f) - (layout.width / 2f);
        float textY = jugador.getY() + jugador.getAlto() + 0.5f; // Encima del jugador

        font.draw(batch, etiqueta, textX, textY);

        // Restaurar escala del font
        font.getData().setScale(2f);
    }

    // 🎨 NUEVO: UI de debug para el servidor
    private void renderizarDebugUI() {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.begin();

        font.getData().setScale(1f);
        font.setColor(Color.CYAN);

        int y = Gdx.graphics.getHeight() - 20;
        int lineHeight = 25;

        font.draw(batch, "=== SERVIDOR DEBUG ===", 10, y);
        y -= lineHeight;

        font.draw(batch, "Sala: " + salaActual.getId(), 10, y);
        y -= lineHeight;

        font.draw(batch, "Jugadores: " + jugadores.size(), 10, y);
        y -= lineHeight;

        ArrayList<Enemigo> enemigos = salaActual.getEnemigos();
        int enemigosVivos = 0;
        if (enemigos != null) {
            for (Enemigo e : enemigos) {
                if (!e.debeEliminarse()) enemigosVivos++;
            }
        }
        font.draw(batch, "Enemigos vivos: " + enemigosVivos, 10, y);
        y -= lineHeight;

        Boss boss = salaActual.getBoss();
        if (boss != null) {
            font.draw(batch, "Boss: " + (boss.debeEliminarse() ? "Muerto" : "Vida: " + boss.getVida()), 10, y);
            y -= lineHeight;
        }

        for (Jugador jugador : jugadores.values()) {
            font.draw(batch, "P" + jugador.getNumeroJugador() +
                " - Vida: " + jugador.getVida() +
                " - Pos: (" + (int)jugador.getX() + "," + (int)jugador.getY() + ")", 10, y);
            y -= lineHeight;
        }

        font.getData().setScale(2f);
        batch.end();
    }

    // 🎨 NUEVO: Actualizar cámara
    private void actualizarCamara() {
        if (jugadores.isEmpty()) return;

        Jugador jugadorASeguir = jugadores.values().iterator().next();

        float halfWidth = camara.viewportWidth / 2f;
        float halfHeight = camara.viewportHeight / 2f;

        float x = jugadorASeguir.getX();
        float y = jugadorASeguir.getY();

        float limiteIzquierdo = halfWidth;
        float limiteDerecho = Math.max(limiteIzquierdo, salaActual.getAnchoMundo() - halfWidth);
        float limiteInferior = halfHeight;
        float limiteSuperior = Math.max(limiteInferior, salaActual.getAltoMundo() - halfHeight);

        x = MathUtils.clamp(x, limiteIzquierdo, limiteDerecho);
        y = MathUtils.clamp(y, limiteInferior, limiteSuperior);

        camara.position.set(x, y, 0);
        camara.update();
    }

    // 🎨 NUEVO: Generar textura simple
    private Texture generarTextura() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture textura = new Texture(pixmap);
        pixmap.dispose();
        return textura;
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        camara.position.set(camara.viewportWidth / 2f, camara.viewportHeight / 2f, 0);
        camara.update();
    }

    private void actualizarLogicaJuego(float delta) {
        // 1️⃣ Actualizar jugadores
        for (Jugador jugador : jugadores.values()) {
            jugador.update(delta, salaActual.getColisiones());

            // ✅ VERIFICAR SI EL JUGADOR MURIÓ
            if (jugador.getVida() <= 0 && jugador.getAccionActual() != Accion.MUERTE) {
                jugador.setAccionActual(Accion.MUERTE);
                playerDied(jugador.getNumeroJugador());
            }
        }

        // 2️⃣ Actualizar enemigos
        ArrayList<Enemigo> enemigos = salaActual.getEnemigos();
        if (enemigos != null) {
            for (int i = enemigos.size() - 1; i >= 0; i--) {
                Enemigo enemigo = enemigos.get(i);

                if (enemigo.debeEliminarse()) {
                    enemigos.remove(i);
                    serverThread.sendMessageToAll("EnemyDead:" + i);
                    continue;
                }

                Jugador jugadorCercano = obtenerJugadorMasCercano(enemigo);
                if (jugadorCercano != null) {
                    boolean ataco = enemigo.actualizar(delta, jugadorCercano.getPosicion(),
                        salaActual.getColisiones(), enemigos);
                    if (ataco) {
                        jugadorCercano.recibirDanio(enemigo.getDanio());
                        serverThread.sendMessageToAll("UpdateHealth:" +
                            jugadorCercano.getNumeroJugador() + ":" + jugadorCercano.getVida());
                    }
                }
            }
        }

        // 3️⃣ Colisiones de ataques - ✅ MODIFICAR ESTA SECCIÓN
        for (Jugador jugador : jugadores.values()) {
            Rectangle hitboxAtaque = jugador.getHitboxAtaque();
            if (hitboxAtaque.getWidth() <= 0 || enemigos == null) continue;



            //aca está el problema de las monedas, no se elimina al instante al enemigo y queda como pegándole, por cada tick le da 10 monedas más
            for (int i = enemigos.size() - 1; i >= 0; i--) {
                Enemigo enemigo = enemigos.get(i);
                if (!enemigo.debeEliminarse() && hitboxAtaque.overlaps(enemigo.getRectangulo())) {
                    if (jugador.puedeGolpear()) {
                        enemigo.recibirDanio(jugador.getDanio());
                        jugador.marcarGolpe();
                    }


                    // ✅ SI EL ENEMIGO MUERE, DAR MONEDAS
                    if (enemigo.getVida() <= 0 && !enemigo.isRewardGiven()) {
                        enemigo.setRewardGiven(true);  // SOLO UNA VEZ
                        int monedasGanadas = 10; // Puedes ajustar esto
                        jugador.modificarMonedas(monedasGanadas);

                        // ✅ ENVIAR ACTUALIZACIÓN DE MONEDAS A TODOS LOS CLIENTES
                        serverThread.sendMessageToAll("UpdateCoins:" +
                            jugador.getNumeroJugador() + ":" + jugador.getMonedas());

                        System.out.println("💰 Jugador " + jugador.getNumeroJugador() +
                            " ganó " + monedasGanadas + " monedas. Total: " + jugador.getMonedas());
                    }
                }
            }
        }


        if (salaActual.getId().equalsIgnoreCase("sala5")) {
            Boss boss = salaActual.getBoss();
            if (boss == null) {
                salaActual.generarBoss();
            } else if (!boss.debeEliminarse()) {
                Jugador jugadorCercano = obtenerJugadorMasCercano(boss);
                if (jugadorCercano != null) {
                    boolean ataco = boss.actualizar(delta, jugadorCercano.getPosicion(),
                        salaActual.getColisiones(), enemigos != null ? enemigos : new ArrayList<>());
                    if (ataco) {
                        jugadorCercano.recibirDanio(boss.getDanio());
                        serverThread.sendMessageToAll("UpdateHealth:" +
                            jugadorCercano.getNumeroJugador() + ":" + jugadorCercano.getVida());
                    }
                }


                for (Jugador jugador : jugadores.values()) {
                    Rectangle hitboxAtaque = jugador.getHitboxAtaque();
                    if (hitboxAtaque.getWidth() <= 0) continue;

                    if (hitboxAtaque.overlaps(boss.getRectangulo())) {
                        boss.recibirDanio(jugador.getDanio());


                        if (boss.debeEliminarse()) {
                            int monedasGanadas = 50; // Boss da más monedas
                            jugador.modificarMonedas(monedasGanadas);

                            serverThread.sendMessageToAll("UpdateCoins:" +
                                jugador.getNumeroJugador() + ":" + jugador.getMonedas());
                            serverThread.sendMessageToAll("BossDead");

                            System.out.println("👑 Jugador " + jugador.getNumeroJugador() +
                                " derrotó al Boss y ganó " + monedasGanadas + " monedas!");
                        }
                    }
                }
            }
        }

        salaActual.actualizarPuertas();
        verificarTransicionesServidor();
    }


    public void verificarTransicionesServidor() {
        for (Jugador jugador : jugadores.values()) {
            // Asegurarse de que no haya enemigos vivos antes de permitir la transición
            boolean noHayEnemigosVivos = salaActual.getEnemigos() == null || salaActual.getEnemigos().isEmpty();
            if (!noHayEnemigosVivos) continue;

            for (ZonaTransicion zona : salaActual.getZonasTransicion()) {
                if (jugador.getHitbox().overlaps(zona)) {
                    // Cambiar sala en el servidor
                    cambiarSala(zona.destinoSalaId);

                    // Notificar a todos los clientes
                    serverThread.sendMessageToAll("RoomChange:" + zona.destinoSalaId);
                    serverThread.sendExistingEnemiesToClient();

                    // Romper el bucle para no procesar más zonas
                    break;
                }
            }
        }
    }




    private void enviarActualizaciones() {
        for (Jugador jugador : jugadores.values()) {
            String msg = "Update:Jugador:" + jugador.getNumeroJugador() + ":" +
                jugador.getX() + ":" + jugador.getY() + ":" +
                jugador.getAccionActual().name() + ":" + jugador.getDireccionActual().name();
            serverThread.sendMessageToAll(msg);
        }

        ArrayList<Enemigo> enemigos = salaActual.getEnemigos();
        if (enemigos == null) return;

        for (int i = 0; i < enemigos.size(); i++) {
            Enemigo e = enemigos.get(i);
            String msg = "Update:Enemigo:" + i + ":" + e.getPosicion().x + ":" + e.getPosicion().y + ":" +
                e.getEstado().name() + ":" + (e.isHaciaIzquierda() ? "IZQUIERDA" : "DERECHA");
            serverThread.sendMessageToAll(msg);
        }

        Boss boss = salaActual.getBoss();
        if (boss != null && !boss.debeEliminarse()) {
            String msgBoss = "Update:Boss:0:" +
                boss.getPosicion().x + ":" +
                boss.getPosicion().y + ":" +
                boss.getEstado().name() + ":" +
                (boss.isHaciaIzquierda() ? "IZQUIERDA" : "DERECHA");
            serverThread.sendMessageToAll(msgBoss);
        }
    }


    private Jugador obtenerJugadorMasCercano(Enemigo enemigo) {
        Jugador cercano = null;
        float min = Float.MAX_VALUE;

        for (Jugador jugador : jugadores.values()) {
            float dist = enemigo.getPosicion().dst(jugador.getPosicion());
            if (dist < min) {
                min = dist;
                cercano = jugador;
            }
        }
        return cercano;
    }

    public void cambiarSala(String destinoId) {
        Sala salaDestino = mapaActual.getSala(destinoId);
        if (salaDestino == null) {
            System.err.println("❌ Sala destino no encontrada en servidor: " + destinoId);
            return;
        }

        // 🔹 PRIMERO: Cambiar la sala actual
        salaActual = salaDestino;
        mapaActual.establecerSalaActual(destinoId);

        serverThread.sendMessageToAll("RoomChange:" + destinoId);
        System.out.println("🗺️ Servidor: cambiando a sala " + destinoId);

        // 🔹 SEGUNDO: Generar enemigos si no existen
        if (salaActual.getEnemigos() == null || salaActual.getEnemigos().isEmpty()) {
            System.out.println("👾 Generando enemigos para sala " + destinoId);
            salaActual.generarEnemigos();
            enviarEnemigosASala(destinoId);

        } else {
            System.out.println("♻️ Reutilizando enemigos existentes de sala " + destinoId);
            enviarEnemigosASala(destinoId);
        }

        if (destinoId.equalsIgnoreCase("sala5")) {
            salaActual.generarBoss();
            Boss boss = salaActual.getBoss();

            if (boss != null) {
                String msgBoss = "SpawnBoss:" +
                    boss.getPosicion().x + ":" +
                    boss.getPosicion().y;
                serverThread.sendMessageToAll(msgBoss);
                System.out.println("👑 Boss enviado a clientes: " + msgBoss);
            }
        }

        if (salaActual.getEnemigos() != null && !salaActual.getEnemigos().isEmpty()) {
            StringBuilder datosEnemigos = new StringBuilder();


            for (Enemigo e : salaActual.getEnemigos()) {
                datosEnemigos.append(e.getPosicion().x).append(", ").append(e.getPosicion().y).append(";");
            }


            System.out.println("Datos de enemigos: " + datosEnemigos.toString());

            if (datosEnemigos.length() > 0) {

                datosEnemigos = new StringBuilder(datosEnemigos.substring(0, datosEnemigos.length() - 1));


                serverThread.sendMessageToAll("SyncEnemies:" + datosEnemigos);
            }
        }


        // 🔹 TERCERO: Reposicionar jugadores
        for (Jugador jugador : jugadores.values()) {
            SpawnPoint spawn = null;
            for (SpawnPoint sp : salaDestino.getSpawnPoints()) {
                if (sp.name.equals("spawn_centro") && sp.salaId.equals(destinoId)) {
                    spawn = sp;
                    break;
                }
            }

            if (spawn != null) {
                jugador.setX(spawn.x);
                jugador.setY(spawn.y);
            } else if (!salaDestino.getSpawnPoints().isEmpty()) {
                SpawnPoint fallback = salaDestino.getSpawnPoints().first();
                jugador.setX(fallback.x);
                jugador.setY(fallback.y);
            } else {
                jugador.setX(salaDestino.getAnchoMundo() / 2f);
                jugador.setY(salaDestino.getAltoMundo() / 2f);
            }

            // Enviar posición actualizada
            String msg = "Update:Jugador:" + jugador.getNumeroJugador() + ":" +
                jugador.getX() + ":" + jugador.getY() + ":" +
                jugador.getAccionActual().name() + ":" +
                jugador.getDireccionActual().name();
            serverThread.sendMessageToAll(msg);
        }
    }

    // 🔹 AGREGAR ESTE MÉTODO
    private void enviarEnemigosASala(String salaId) {
        Sala sala = mapaActual.getSala(salaId);
        if (sala == null || sala.getEnemigos() == null) {
            System.out.println("⚠️ No hay enemigos para enviar en sala " + salaId);
            return;
        }

        System.out.println("📦 Enviando " + sala.getEnemigos().size() + " enemigos de sala " + salaId);

        for (int i = 0; i < sala.getEnemigos().size(); i++) {
            Enemigo e = sala.getEnemigos().get(i);
            if (e != null && !e.debeEliminarse()) {
                String msg = "SpawnEnemy:" + i + ":" +
                    e.getPosicion().x + ":" +
                    e.getPosicion().y;
                serverThread.sendMessageToAll(msg);
                System.out.println("   📨 Enemigo " + i + " en (" +
                    e.getPosicion().x + ", " + e.getPosicion().y + ")");
            }
        }


    }


    // ===== GameController =====
    @Override public void startGame() { juegoIniciado = true;}
    @Override public void move(int n, float x, float y) {}
    @Override public void attack(int n) { if (jugadores.get(n) != null) jugadores.get(n).procesarAtaque(); }
    @Override public void enemyKilled(int n, int id) {}
    @Override public void bossKilled(int n) {}
    @Override public void changeRoom(int n, String roomId) { cambiarSala(roomId);}
    @Override
    public void playerDied(int numPlayer) {
        juegoIniciado = false;
        System.out.println("💀 Jugador " + numPlayer + " ha muerto");

        // Notificar a todos los clientes sobre la muerte
        serverThread.sendMessageToAll("PlayerDied:" + numPlayer);

        // Esperar 2 segundos antes de mostrar Game Over
        new Thread(() -> {
            try {
                Thread.sleep(2000);

                // Enviar señal de Game Over a todos los clientes
                serverThread.sendMessageToAll("GameOver");
                System.out.println("🎮 Game Over enviado a todos los clientes");

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }




    @Override
    public void comprarHabilidad(int numPlayer, String nombreHabilidad) {
        Jugador jugador = jugadores.get(numPlayer);
        if (jugador == null) return;

        boolean exito = jugador.intentarComprarHabilidad(nombreHabilidad);

        if (exito) {
            // Enviar confirmación con datos actualizados
            String datosHabilidades = jugador.serializarHabilidades();
            serverThread.sendMessage("CompraExitosa:" + nombreHabilidad + ":" + datosHabilidades + ":" + jugador.getMonedas(),
                serverThread.getClientByNum(numPlayer).getIp(),
                serverThread.getClientByNum(numPlayer).getPort());
        } else {
            // Enviar rechazo
            serverThread.sendMessage("CompraFallida:" + nombreHabilidad,
                serverThread.getClientByNum(numPlayer).getIp(),
                serverThread.getClientByNum(numPlayer).getPort());
        }
    }

    public void actualizarMovimiento(int n, boolean up, boolean down, boolean left, boolean right) {
        Jugador j = jugadores.get(n);
        if (j == null) return;
        j.setMovimientoArriba(up);
        j.setMovimientoAbajo(down);
        j.setMovimientoIzquierda(left);
        j.setMovimientoDerecha(right);
    }

    public void crearJugador(int n) {
        if (jugadores.containsKey(n)) return;
        Jugador j = new Jugador(n, 10f + n * 2f, 10f);
        jugadores.put(n, j);
        System.out.println("✅ Jugador " + n + " creado");

        if (jugadores.size() >= MAX_JUGADORES) {
            startGame();
            serverThread.sendMessageToAll("Start");
        }
    }

    @Override
    public void timeOut() {
        System.out.println("⏱️ TimeOut llamado - Reseteando servidor");
        resetearServidorCompleto();
    }


    @Override
    public void resetearServidorCompleto() {
        System.out.println("🔄 ========== RESETEO COMPLETO DEL SERVIDOR ==========");

        // 1. Detener el juego
        juegoIniciado = false;

        // 2. Limpiar jugadores
        if (jugadores != null) {
            jugadores.clear();
        }

        // 3. REGENERAR TODAS LAS SALAS (esto limpia enemigos, puertas, etc.)
        if (mapaActual != null) {
            mapaActual.regenerarTodasLasSalas();
        }

        // 4. Volver a la sala inicial
        if (mapaActual != null && mapaActual.getSala("sala1") != null) {
            salaActual = mapaActual.getSala("sala1");
            mapaActual.establecerSalaActual("sala1");

            // ✅ REGENERAR ENEMIGOS DE LA SALA INICIAL
            System.out.println("👾 Generando enemigos iniciales de sala1...");
            salaActual.generarEnemigos();
        }

        // 5. Resetear timers
        tiempoAcumulado = 0f;

        System.out.println("✅ Servidor reseteado completamente");
        System.out.println("👥 Esperando " + MAX_JUGADORES + " jugadores...");
        System.out.println("====================================================");
    }

    private void limpiarCompletamente() {
        System.out.println("🧹 Limpiando estado del servidor...");

        // 1. Detener thread de red
        if (serverThread != null) {
            serverThread.disconnectAllClients();
            serverThread.terminate();
            try {
                serverThread.join(1000); // Esperar máximo 1 segundo
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            serverThread = null;
        }

        // 2. Limpiar jugadores
        if (jugadores != null) {
            jugadores.clear();
        }

        // 3. Limpiar mapa y salas (CRÍTICO)
        if (mapaActual != null) {
            mapaActual.dispose();
            mapaActual = null;
        }


        // 4. Resetear estado
        juegoIniciado = false;
        salaActual = null;
        tiempoAcumulado = 0f;

        System.out.println("✅ Estado del servidor limpiado");
    }

    @Override
    public void dispose() {
        System.out.println("🔴 Dispose de PantallaJuego (SERVIDOR)");

        // Detener servidor
        if (serverThread != null) {
            serverThread.disconnectAllClients();
            serverThread.terminate();

            try {
                serverThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Limpiar mapa
        if (mapaActual != null) {
            mapaActual.dispose();
        }

        if (texturaFade != null) {
            texturaFade.dispose();
        }
        if (spriteJugador != null) {
            spriteJugador.dispose();
        }
    }

    public void resetearJuego() {
        System.out.println("🔄 Reseteando juego completo...");

        limpiarCompletamente();

        // Volver al menú
        Gdx.app.postRunnable(() -> {
            juego.setScreen(new MenuInicio(juego, batch));
        });
    }

    public Sala getSalaActual() { return salaActual; }
}
