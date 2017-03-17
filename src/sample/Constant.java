package sample;

import com.google.gson.Gson;

import javax.crypto.Cipher;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;

/**
 * Created by Punit on 3/17/2017.
 */
class Constant {
    static HashMap contacts = new HashMap<>();
    static Gson converter = new Gson();
    static final String ME = "ME";
    static String FRIEND = "FRIEND";
    static ObjectInputStream inputStream;
    static ObjectOutputStream outputStream;
    static Socket s;
    static ServerSocket listener;
    static Boolean encryptionState = false;
    static PrivateKey privateKey = null;
    static PublicKey publicKey = null;

    static final String EXIT = String.valueOf("Iamclosing".hashCode());
    static Cipher encryptCipher;
    static final int BLOCK_SIZE = 2048;
    static final String FILEOVER = Integer.toString("FILE SENT".hashCode());
    static final String OPFILE = Integer.toString("FILE INCOMING".hashCode());
    static final String IGNORE = Integer.toString("IGNORE".hashCode());
    static File toSend;
}
