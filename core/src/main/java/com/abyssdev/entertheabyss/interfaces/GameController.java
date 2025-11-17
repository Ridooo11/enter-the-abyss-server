package com.abyssdev.entertheabyss.interfaces;

public interface GameController {
    void startGame();
    void move(int numPlayer, float x, float y);
    void attack(int numPlayer);
    void enemyKilled(int numPlayer, int enemyId);
    void changeRoom(int numPlayer, String roomId);
    void timeOut();
    void comprarHabilidad(int numPlayer, String nombreHabilidad);
    boolean hacerDash(int numPlayer); // Cambiado de void a boolean
    void playerDied(int numPlayer);
    void resetearServidorCompleto();
    void comprarVida(int numPlayer, int precio);

    void enviarHabilidadesACliente(int numPlayer);


}
