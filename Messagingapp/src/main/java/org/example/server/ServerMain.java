package org.example.server;

import org.example.server.core.Server;
import org.example.server.database.DatabaseManager;

public class ServerMain {
    public static void main(String[] args) {
        DatabaseManager.initDatabase();

        new Server().start();  // ← start() pas main()
    }
}