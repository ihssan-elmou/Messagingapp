package org.example.utils;
public class Constants {
    public static final int    TEXT_PORT   = 5000;
    public static final int    AUDIO_PORT  = 5001;
    public static final int    VIDEO_PORT  = 5002;
    public static final int    FILE_PORT   = 5003;
    public static final int    BUFFER_SIZE = 8192;
    public static final int    VIDEO_FPS   = 15;
    public static final int    VIDEO_W     = 320;
    public static final int    VIDEO_H     = 240;
    public static final int VIDEO_LISTEN = 6100;
    public static final String SERVER_HOST = "192.168.1.38";
    // Ports client dynamiques — chaque client choisit un port libre
    // Plus de AUDIO_LISTEN / VIDEO_LISTEN fixes ici
}