package com.abyssdev.entertheabyss.network;

import com.abyssdev.entertheabyss.interfaces.GameController;
import com.abyssdev.entertheabyss.mapas.Sala;
import com.abyssdev.entertheabyss.pantallas.Pantalla;
import com.abyssdev.entertheabyss.pantallas.PantallaJuego;
import com.abyssdev.entertheabyss.pantallas.PantallaJuego;
import com.abyssdev.entertheabyss.personajes.Enemigo;
import com.badlogic.gdx.math.Rectangle;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;

/**
 * ServerThread - Versión SERVIDOR
 * Maneja la comunicación de red del servidor
 */
public class ServerThread extends Thread {

    private DatagramSocket socket;
    private final int serverPort = 9999;
    private boolean end = false;
    private final int MAX_CLIENTS = 2;
    private int connectedClients = 0;
    private ArrayList<Client> clients = new ArrayList<>();
    private PantallaJuego gameController;
    private Rectangle hitbox;

    public ServerThread(PantallaJuego gameController) {
        this.gameController = gameController;
        try {
            socket = new DatagramSocket(serverPort);
            socket.setSoTimeout(0); // Sin timeout
        } catch (SocketException e) {
            System.err.println("❌ Error al crear socket del servidor: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        System.out.println("🟢 Servidor de red iniciado en puerto " + serverPort);

        while (!end) {
            DatagramPacket packet = new DatagramPacket(new byte[2048], 2048);
            try {
                socket.receive(packet);
                processMessage(packet);
            } catch (IOException e) {
                if (!end) {
                    System.err.println("❌ Error al recibir paquete: " + e.getMessage());
                }
            }
        }

        System.out.println("🔴 Servidor de red detenido");
    }

    private void processMessage(DatagramPacket packet) {
        String message = (new String(packet.getData())).trim();
        String[] parts = message.split(":");
        int clientIndex = findClientIndex(packet);

        System.out.println("📨 [" + packet.getAddress() + ":" + packet.getPort() + "] " + message);

        switch (parts[0]) {
            case "Connect":
                handleConnect(packet, clientIndex);
                break;

            case "Disconnect":
                InetAddress address = packet.getAddress();
                int port = packet.getPort();
                System.out.println("🔌 Cliente desconectado: " + address + ":" + port);

                int playerIndex = findPlayerIndex(address, port);
                if (playerIndex != -1) {
                    Client disconnectedClient = clients.get(playerIndex);
                    System.out.println("🧹 Eliminando cliente " + disconnectedClient.getNum());

                    clients.remove(playerIndex); // ✅ elimina el objeto del ArrayList
                    connectedClients = Math.max(connectedClients - 1, 0);

                } else {
                    System.out.println("⚠️ Cliente no encontrado para eliminar");
                }
                break;



            case "Input":
                // Input:arriba:abajo:izquierda:derecha
                if (clientIndex != -1 && parts.length >= 5) {
                    Client client = clients.get(clientIndex);
                    boolean arriba = Boolean.parseBoolean(parts[1]);
                    boolean abajo = Boolean.parseBoolean(parts[2]);
                    boolean izquierda = Boolean.parseBoolean(parts[3]);
                    boolean derecha = Boolean.parseBoolean(parts[4]);

                    gameController.actualizarMovimiento(client.getNum(), arriba, abajo, izquierda, derecha);
                }
                break;

            case "Attack":
                if (clientIndex != -1) {
                    Client client = clients.get(clientIndex);
                    gameController.attack(client.getNum());
                }
                break;

            case "Dash":
                if (clientIndex != -1) {

                    System.out.println("🏃 Jugador " + clients.get(clientIndex).getNum() + " usa dash");
                }
                break;

            case "ChangeRoom":
                if (clientIndex != -1 && parts.length >= 2) {
                    Client client = clients.get(clientIndex);
                    String roomId = parts[1];
                    gameController.changeRoom(client.getNum(), roomId);
                }
                break;


            case "ComprarHabilidad":
                // ComprarHabilidad:nombreHabilidad
                if (clientIndex != -1 && parts.length >= 2) {
                    Client client = clients.get(clientIndex);
                    String nombreHabilidad = parts[1];
                    gameController.comprarHabilidad(client.getNum(), nombreHabilidad);
                }
                break;

            default:
                if (clientIndex == -1) {
                    System.out.println("⚠️ Cliente no conectado intentando enviar: " + parts[0]);
                    sendMessage("NotConnected", packet.getAddress(), packet.getPort());
                }
                break;
        }
    }

    private void handleConnect(DatagramPacket packet, int clientIndex) {
        if (clientIndex != -1) {
            sendMessage("AlreadyConnected", packet.getAddress(), packet.getPort());
            return;
        }

        if (connectedClients < MAX_CLIENTS) {
            // Asignar el número más bajo disponible
            int playerNum = 1;
            ArrayList<Integer> usados = new ArrayList<>();
            for (Client c : clients) {
                usados.add(c.getNum());
            }
            while (usados.contains(playerNum)) {
                playerNum++;
            }

            Client newClient = new Client(playerNum, packet.getAddress(), packet.getPort());
            clients.add(newClient);
            connectedClients++;

            sendExistingEnemiesToClient(packet.getAddress(), packet.getPort());
            sendMessage("Connected:" + playerNum, packet.getAddress(), packet.getPort());
            System.out.println("✅ Cliente " + playerNum + " conectado desde " + packet.getAddress() + ":" + packet.getPort());

            gameController.crearJugador(playerNum);

            if (connectedClients == MAX_CLIENTS) {
                System.out.println("🎮 Todos los jugadores conectados, iniciando juego...");
                for (Client client : clients) {
                    sendMessage("Start", client.getIp(), client.getPort());
                }
            }
        } else {
            sendMessage("Full", packet.getAddress(), packet.getPort());
        }
    }


    private int findClientIndex(DatagramPacket packet) {
        String id = packet.getAddress().toString() + ":" + packet.getPort();

        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).getId().equals(id)) {
                return i;
            }
        }

        return -1;
    }

    public void sendMessage(String message, InetAddress clientIp, int clientPort) {
        if (socket == null || socket.isClosed()) {
            System.err.println("⚠️ Socket cerrado, no se puede enviar: " + message);
            return;
        }

        byte[] byteMessage = message.getBytes();
        DatagramPacket packet = new DatagramPacket(byteMessage, byteMessage.length, clientIp, clientPort);

        try {
            socket.send(packet);
            // System.out.println("📤 [" + clientIp + ":" + clientPort + "] " + message);
        } catch (IOException e) {
            System.err.println("❌ Error al enviar mensaje a " + clientIp + ":" + clientPort + " - " + e.getMessage());
        }
    }





    public void sendMessageToAll(String message) {
        for (Client client : new ArrayList<>(clients)) {
            if (client != null) {
                sendMessage(message, client.getIp(), client.getPort());
            }
        }
    }


    private int findPlayerIndex(InetAddress address, int port) {
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i) != null &&
                clients.get(i).getIp().equals(address) &&
                clients.get(i).getPort() == port) {
                return i;
            }
        }
        return -1;
    }


    public void sendExistingEnemiesToClient(InetAddress clientIp, int clientPort) {
        if (this.gameController == null || this.gameController.getSalaActual() == null) return;

        Sala sala = this.gameController.getSalaActual();
        if (sala.getEnemigos() == null) return;

        for (int i = 0; i < sala.getEnemigos().size(); i++) {
            Enemigo enemigo = sala.getEnemigos().get(i);
            String msg = "SpawnEnemy:" + i + ":" +
                enemigo.getPosicion().x + ":" +
                enemigo.getPosicion().y;

            sendMessage(msg, clientIp, clientPort); // solo al nuevo cliente
        }

        System.out.println("📦 Enviados " + sala.getEnemigos().size() + " enemigos existentes a " + clientIp + ":" + clientPort);
    }

    public void sendExistingEnemiesToClient() {
        if (this.gameController == null || this.gameController.getSalaActual() == null) return;

        Sala sala = this.gameController.getSalaActual();
        if (sala.getEnemigos() == null) return;

        for (int i = 0; i < sala.getEnemigos().size(); i++) {
            Enemigo enemigo = sala.getEnemigos().get(i);
            String msg = "SpawnEnemy:" + i + ":" +
                enemigo.getPosicion().x + ":" +
                enemigo.getPosicion().y;

            sendMessageToAll(msg); // 👈 se manda a todos
        }

        System.out.println("📦 Enviados " + sala.getEnemigos().size() + " enemigos existentes a todos los clientes");
    }

    public Client getClientByNum(int num) {
        for (Client client : clients) {
            if (client.getNum() == num) {
                return client;
            }
        }
        return null;
    }


    public void disconnectClients() {
        System.out.println("🔌 Desconectando todos los clientes");

        for (Client client : clients) {
            sendMessage("Disconnect", client.getIp(), client.getPort());
        }

        for (int i = 0; i < clients.size(); i++) {
                clients.remove(i);
                connectedClients--;
                System.out.println("🔌 Cliente " + i + " desconectado. Clientes restantes: " + connectedClients);
        }

        clients.clear();
        connectedClients = 0;
    }

    public void terminate() {
        System.out.println("🛑 Terminando servidor de red...");

        this.end = true;

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        this.interrupt();
    }

    public int getConnectedClients() {
        return connectedClients;
    }
}
