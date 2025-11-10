package com.abyssdev.entertheabyss.pantallas;

import com.abyssdev.entertheabyss.interfaces.GameController;
import com.abyssdev.entertheabyss.mapas.*;
import com.abyssdev.entertheabyss.personajes.*;
import com.abyssdev.entertheabyss.network.ServerThread;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

import java.util.ArrayList;
import java.util.HashMap;

public class PantallaJuego extends Pantalla implements GameController {

    // 🌐 Red
    private ServerThread serverThread;
    private boolean juegoIniciado = false;

    // 🗺️ Mundo
    private Mapa mapaActual;
    private Sala salaActual;

    // 👥 Jugadores
    private HashMap<Integer, Jugador> jugadores = new HashMap<>();
    private static final int MAX_JUGADORES = 2;

    // ⏱️ Ticks
    private float tiempoAcumulado = 0f;
    private static final float TICK_RATE = 1f / 30f; // 30 FPS lógica

    public PantallaJuego(Game juego, SpriteBatch batch) {
        super(juego, batch);
    }

    @Override
    public void show() {
        System.out.println("🖥️ Servidor iniciado");
        serverThread = new ServerThread(this);
        serverThread.start();

        System.out.println("⏳ Esperando jugadores...");

        // Inicializar mapa y salas
        mapaActual = new Mapa("mazmorra1");
        mapaActual.agregarSala(new Sala("sala1", "maps/mapa1_sala1.tmx", 2, this.serverThread));
        mapaActual.agregarSala(new Sala("sala2", "maps/mapa1_sala2.tmx", 0, this.serverThread));
        mapaActual.agregarSala(new Sala("sala3", "maps/mapa1_sala5.tmx", 2, this.serverThread));
        mapaActual.agregarSala(new Sala("sala4", "maps/mapa1_sala4.tmx", 1, this.serverThread));
        mapaActual.agregarSala(new Sala("sala5", "maps/mapa2_posible.tmx", 15, this.serverThread));

        cambiarSala("sala1");
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (!juegoIniciado) return;

        tiempoAcumulado += delta;
        while (tiempoAcumulado >= TICK_RATE) {
            actualizarLogicaJuego(TICK_RATE);
            tiempoAcumulado -= TICK_RATE;
        }

        enviarActualizaciones();


    }

    private void actualizarLogicaJuego(float delta) {
        // 1️⃣ Actualizar jugadores
        for (Jugador jugador : jugadores.values()) {
            jugador.update(delta, salaActual.getColisiones());
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
                    boolean ataco = enemigo.actualizar(delta, jugadorCercano.getPosicion(), salaActual.getColisiones(), enemigos);
                    if (ataco) {
                        jugadorCercano.recibirDanio(enemigo.getDanio());
                        serverThread.sendMessageToAll("UpdateHealth:" + jugadorCercano.getNumeroJugador() + ":" + jugadorCercano.getVida());
                    }
                }
            }
        }

        // 3️⃣ Colisiones de ataques
        for (Jugador jugador : jugadores.values()) {
            Rectangle hitboxAtaque = jugador.getHitboxAtaque();
            if (hitboxAtaque.getWidth() <= 0 || enemigos == null) continue;

            for (int i = enemigos.size() - 1; i >= 0; i--) {
                Enemigo enemigo = enemigos.get(i);
                if (!enemigo.debeEliminarse() && hitboxAtaque.overlaps(enemigo.getRectangulo())) {
                    enemigo.recibirDanio(jugador.getDanio());
                    if (enemigo.debeEliminarse()) {
                        jugador.modificarMonedas(10);
                        serverThread.sendMessageToAll("UpdateCoins:" + jugador.getNumeroJugador() + ":" + jugador.getMonedas());
                    }
                }
            }
        }

        boolean noHayEnemigosVivos = enemigos == null || enemigos.isEmpty();



        // 4️⃣ Boss (solo en sala5)
        if (salaActual.getId().equalsIgnoreCase("sala5")) {
            Boss boss = salaActual.getBoss();
            if (boss == null) salaActual.generarBoss();
            else if (!boss.debeEliminarse()) {
                Jugador jugadorCercano = obtenerJugadorMasCercano(boss);
                if (jugadorCercano != null) {
                    boolean ataco = boss.actualizar(delta, jugadorCercano.getPosicion(), salaActual.getColisiones(),
                        enemigos != null ? enemigos : new ArrayList<>());
                    if (ataco) {
                        jugadorCercano.recibirDanio(boss.getDanio());
                        serverThread.sendMessageToAll("UpdateHealth:" + jugadorCercano.getNumeroJugador() + ":" + jugadorCercano.getVida());
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

        Sala salaAnterior = salaActual;
        salaActual = salaDestino;
        mapaActual.establecerSalaActual(destinoId);

        System.out.println("🗺️ Servidor: cambiando a sala " + destinoId);

        // 🔹 Generar enemigos si no existen
        if (salaActual.getEnemigos() == null || salaActual.getEnemigos().isEmpty()) {
            salaActual.generarEnemigos();
        }


        ArrayList<ZonaTransicion> zonas = new ArrayList<>();
        zonas.add(new ZonaTransicion(30, 10, 2, 4, "sala2", "spawn_centro"));
        zonas.add(new ZonaTransicion(0, 10, 2, 4, "sala1", "spawn_centro"));

        // Enviar info de zonas a todos los clientes
        for (ZonaTransicion zona : zonas) {
            String msg = "ZonaTransicion:" + zona.x + "," + zona.y + "," + zona.width + "," +
                zona.height + "," + zona.destinoSalaId + "," + zona.spawnName;
            serverThread.sendMessageToAll(msg);
        }

        // 🔹 Avisar a clientes que cambien de sala
        serverThread.sendMessageToAll("RoomChange:" + destinoId);

        // 🔹 Enviar enemigos existentes
        if (salaActual.getEnemigos() != null && !salaActual.getEnemigos().isEmpty()) {
            String datosEnemigos = "";
            for (Enemigo e : salaActual.getEnemigos()) {
                datosEnemigos += e.getPosicion().x + "," + e.getPosicion().y + ";";
            }
            if (!datosEnemigos.isEmpty()) {
                datosEnemigos = datosEnemigos.substring(0, datosEnemigos.length() - 1);
                serverThread.sendMessageToAll("SyncEnemies:" + datosEnemigos);
            }
        }

        // 🔹 Reposicionar jugadores y enviar sus posiciones a clientes
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



    // ===== GameController =====
    @Override public void startGame() { juegoIniciado = true; }
    @Override public void move(int n, float x, float y) {}
    @Override public void attack(int n) { if (jugadores.get(n) != null) jugadores.get(n).procesarAtaque(); }
    @Override public void enemyKilled(int n, int id) {}
    @Override public void bossKilled(int n) {}
    @Override public void changeRoom(int n, String roomId) { cambiarSala(roomId); serverThread.sendMessageToAll("RoomChange:" + roomId); }
    @Override public void timeOut() { serverThread.disconnectClients(); }

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
    public void dispose() {
        if (serverThread != null) serverThread.terminate();
        if (mapaActual != null) mapaActual.dispose();
    }

    public Sala getSalaActual() { return salaActual; }
}
