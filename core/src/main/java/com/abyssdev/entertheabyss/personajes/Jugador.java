package com.abyssdev.entertheabyss.personajes;

import com.abyssdev.entertheabyss.habilidades.*;
import com.abyssdev.entertheabyss.logica.ManejoEntradas;
import com.abyssdev.entertheabyss.personajes.Accion;
import com.abyssdev.entertheabyss.personajes.Direccion;
import com.abyssdev.entertheabyss.ui.Hud;
import com.abyssdev.entertheabyss.ui.Sonidos;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

import java.util.HashMap;
import java.util.Map;

/**
 * Versión SERVIDOR del Jugador
 * Contiene TODA la lógica: movimiento, colisiones, combate, stats
 * NO tiene renderizado ni animaciones
 */
public class Jugador {
    // Identificación
    private int numeroJugador;

    // Física y posición
    private Vector2 posicion;
    private float velocidad = 3f;
    private float velocidadBase = 3f;

    // Hitbox
    private final float ancho = 3f, alto = 3f;
    private final float anchoHitbox = 1f;
    private final float altoHitbox = 1f;
    private final float offsetHitboxX = 1f;
    private final float offsetHitboxY = 0.5f;

    // Stats
    private int vida = 100;
    private int vidaMaxima = 100;
    private int monedas = 0;
    private int danioBase = 5;

    // Estado
    private Accion accionActual = Accion.ESTATICO;
    private Direccion direccionActual = Direccion.ABAJO;

    // Combate
    private Rectangle hitboxAtaque;
    private float tiempoUltimoAtaque = 0f;
    private float cooldownAtaque = 1f;
    private float porcentajeReduccionDanio = 0f;

    // Habilidades
    private boolean regeneracionActiva = false;
    private float tiempoRegeneracion = 0f;
    private float intervaloRegeneracion = 1f;
    private int cantidadRegeneracion = 1;

    private boolean evasionHabilitada = false;
    private boolean estaEvadiendo = false;
    private float duracionEvasion = 0.3f;
    private float tiempoEvasion = 0f;
    private float cooldownEvasion = 1.5f;
    private float tiempoDesdeUltimaEvasion = 0f;


    private boolean arriba, abajo, izquierda, derecha;

    private Map<String, Habilidad> habilidades;

    public Jugador(int numeroJugador, float x, float y) {
        this.numeroJugador = numeroJugador;
        this.posicion = new Vector2(x, y);
        this.hitboxAtaque = new Rectangle(0, 0, 0, 0);

    }



    public boolean intentarComprarHabilidad(String nombreHabilidad) {
        Habilidad habilidad = habilidades.get(nombreHabilidad);

        if (habilidad == null) return false;

        habilidad.comprada = true;
        habilidad.aplicar(this);

        return true;
    }


    public String serializarHabilidades() {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, Habilidad> entry : habilidades.entrySet()) {
            sb.append(entry.getKey()).append(",");
            sb.append(entry.getValue().comprada ? "1" : "0").append(";");
        }

        return sb.toString();
    }

    /**
     * Actualiza la física y lógica del jugador
     * Llamado por el servidor cada frame
     */
    public void update(float delta, Array<Rectangle> colisiones) {
        // Calcular dirección del movimiento
        float dx = 0, dy = 0;
        if (arriba) dy += 1;
        if (abajo) dy -= 1;
        if (izquierda) dx -= 1;
        if (derecha) dx += 1;

        // Normalizar diagonal
        if (dx != 0 && dy != 0) {
            dx *= 0.7071f;
            dy *= 0.7071f;
        }

        // Aplicar movimiento con colisiones
        float nuevaX = posicion.x + dx * velocidad * delta;
        float nuevaY = posicion.y + dy * velocidad * delta;

        Rectangle hitboxX = new Rectangle(nuevaX + offsetHitboxX, posicion.y + offsetHitboxY, anchoHitbox, altoHitbox);
        Rectangle hitboxY = new Rectangle(posicion.x + offsetHitboxX, nuevaY + offsetHitboxY, anchoHitbox, altoHitbox);

        boolean colisionX = false;
        for (Rectangle r : colisiones) {
            if (r.overlaps(hitboxX)) {
                colisionX = true;
                break;
            }
        }
        if (!colisionX) posicion.x = nuevaX;

        boolean colisionY = false;
        for (Rectangle r : colisiones) {
            if (r.overlaps(hitboxY)) {
                colisionY = true;
                break;
            }
        }
        if (!colisionY) posicion.y = nuevaY;

        // Actualizar estado de acción
        boolean estaMoviendo = (dx != 0 || dy != 0);

        if (accionActual == Accion.ATAQUE) {
            // La animación de ataque se maneja en el cliente
            // Aquí solo verificamos si terminó el cooldown
            if (tiempoUltimoAtaque >= 0.5f) { // Duración aproximada de ataque
                accionActual = Accion.ESTATICO;
                hitboxAtaque.setSize(0, 0);
            }
        } else if (estaMoviendo) {
            accionActual = Accion.CAMINAR;

            // Actualizar dirección
            if (Math.abs(dx) > Math.abs(dy)) {
                direccionActual = dx > 0 ? Direccion.DERECHA : Direccion.IZQUIERDA;
            } else if (dy != 0) {
                direccionActual = dy > 0 ? Direccion.ARRIBA : Direccion.ABAJO;
            }
        } else {
            accionActual = Accion.ESTATICO;
        }

        // Cooldowns
        tiempoUltimoAtaque += delta;

        // Regeneración
        if (regeneracionActiva && vida < vidaMaxima) {
            tiempoRegeneracion += delta;
            if (tiempoRegeneracion >= intervaloRegeneracion) {
                tiempoRegeneracion = 0f;
                vida = Math.min(vida + cantidadRegeneracion, vidaMaxima);
            }
        }

        // Evasión
        if (estaEvadiendo) {
            tiempoEvasion += delta;
            if (tiempoEvasion >= duracionEvasion) {
                estaEvadiendo = false;
                velocidad = velocidadBase;
            }
        } else {
            tiempoDesdeUltimaEvasion += delta;
        }
    }

    /**
     * Procesa el input de ataque recibido del cliente
     */
    public void procesarAtaque() {
        if (tiempoUltimoAtaque >= cooldownAtaque && accionActual != Accion.MUERTE) {
            accionActual = Accion.ATAQUE;
            tiempoUltimoAtaque = 0;
            actualizarHitboxAtaque();
        }
    }

    /**
     * Procesa el input de evasión recibido del cliente
     */
    public void procesarEvasion() {
        if (!evasionHabilitada || estaEvadiendo || tiempoDesdeUltimaEvasion < cooldownEvasion) {
            return;
        }

        estaEvadiendo = true;
        tiempoEvasion = 0f;
        tiempoDesdeUltimaEvasion = 0f;
        velocidad = velocidadBase * 4f;
    }

    private void actualizarHitboxAtaque() {
        float hitboxWidth = 0.5f;
        float hitboxHeight = 0.5f;
        float offsetX = 0, offsetY = 0;

        switch (direccionActual) {
            case ABAJO:
                offsetX = (ancho - hitboxWidth) / 2;
                offsetY = -0.5f;
                break;
            case ARRIBA:
                offsetX = (ancho - hitboxWidth) / 2;
                offsetY = alto;
                break;
            case IZQUIERDA:
                offsetX = -0.5f;
                offsetY = (alto - hitboxHeight) / 2;
                break;
            case DERECHA:
                offsetX = ancho;
                offsetY = (alto - hitboxHeight) / 2;
                break;
        }

        hitboxAtaque.set(posicion.x + offsetX, posicion.y + offsetY, hitboxWidth, hitboxHeight);
    }

    public void recibirDanio(int danioBruto) {
        if (danioBruto <= 0) return;

        float danioReducido = danioBruto * (1f - porcentajeReduccionDanio);
        int danioFinal = Math.max(1, (int) Math.floor(danioReducido));

        this.vida -= danioFinal;

        if (this.vida <= 0) {
            this.vida = 0;
            accionActual = Accion.MUERTE;
        }
    }

    // ==================== SETTERS PARA INPUTS ====================
    public void setMovimientoArriba(boolean activo) { this.arriba = activo; }
    public void setMovimientoAbajo(boolean activo) { this.abajo = activo; }
    public void setMovimientoIzquierda(boolean activo) { this.izquierda = activo; }
    public void setMovimientoDerecha(boolean activo) { this.derecha = activo; }

    // ==================== HABILIDADES ====================
    public void aumentarVelocidad(float incremento) {
        this.velocidadBase += incremento;
        this.velocidad = this.velocidadBase;
    }

    public void reducirCooldownAtaque(float reduccion) {
        this.cooldownAtaque = Math.max(0.1f, this.cooldownAtaque - reduccion);
    }

    public void aumentarDanio(int cantidad) {
        this.danioBase += cantidad;
    }

    public void habilitarEvasion(boolean habilitar) {
        this.evasionHabilitada = habilitar;
    }

    public void aumentarVidaMaxima(int cantidad) {
        this.vidaMaxima += cantidad;
        this.vida = Math.min(this.vida, this.vidaMaxima);
    }

    public void setAccionActual(Accion accion) {
        this.accionActual = accion;
    }

    public void reducirDanioRecibido(float porcentaje) {
        this.porcentajeReduccionDanio = Math.min(0.9f, this.porcentajeReduccionDanio + porcentaje);
    }

    public void activarRegeneracion(int cantidadPorSegundo) {
        this.regeneracionActiva = true;
        this.cantidadRegeneracion = cantidadPorSegundo;
    }


    public void atacar() {
        if (tiempoUltimoAtaque >= cooldownAtaque && accionActual != Accion.MUERTE) {
            accionActual = Accion.ATAQUE;
            tiempoUltimoAtaque = 0;
            Sonidos.reproducirAtaque();
        }
    }

    public void intentarEvasion() {
        if (!evasionHabilitada) return;

        if (tiempoDesdeUltimaEvasion < cooldownEvasion) return;


        tiempoEvasion = 0f;
        tiempoDesdeUltimaEvasion = 0f;

        // Aumenta temporalmente la velocidad
        velocidad = velocidadBase * 4f;

        // Reproducir sonido opcional
        Sonidos.reproducirEvasion(); // solo si querés agregarlo


    }

    // ==================== GETTERS ====================
    public int getNumeroJugador() { return this.numeroJugador; }
    public float getX() { return this.posicion.x; }
    public float getY() { return this.posicion.y; }
    public Vector2 getPosicion() { return this.posicion; }
    public int getVida() { return this.vida; }
    public int getVidaMaxima() { return this.vidaMaxima; }
    public int getMonedas() { return this.monedas; }
    public int getDanio() { return this.danioBase; }
    public Accion getAccionActual() { return this.accionActual; }
    public Direccion getDireccionActual() { return this.direccionActual; }
    public Rectangle getHitboxAtaque() { return this.hitboxAtaque; }
    public Rectangle getHitbox() {
        return new Rectangle(posicion.x + offsetHitboxX, posicion.y + offsetHitboxY, anchoHitbox, altoHitbox);
    }
    public Map<String, Habilidad> getHabilidades() {
        return this.habilidades;
    }

    // ==================== SETTERS ====================
    public void setX(float x) { this.posicion.x = x; }
    public void setY(float y) { this.posicion.y = y; }
    public void setVida(int vida) { this.vida = Math.max(0, Math.min(vida, vidaMaxima)); }
    public void setMonedas(int monedas) { this.monedas = Math.max(0, monedas); }
    public void modificarMonedas(int cantidad) { setMonedas(this.monedas + cantidad); }


    public float getAncho() {
        return this.ancho;
    }

    public float getAlto() {
        return this.alto;
    }
}
