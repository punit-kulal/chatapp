package sample;

import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static sample.ControllerIndex.*;

/*
  Created by Punit Kulal on 2/21/2017.
 */
public class ControllerChat {

    static private final String E = "Iamclosing";
    static final String EXIT = Integer.toString(E.hashCode());
    static Cipher encryptCipher;
    private static Cipher decryptCipher;
    private final int BLOCK_SIZE = 2048;
    private final int ENCRYPT_BLOCK_SIZE = 64;
    private final String FILEOVER = "FILE SENT";
    private final String OPFILE = "FILE INCOMING";
    private final String IGNORE = "IGNORE";
    public TextArea chat;
    public TextArea input;
    public Button send;
    public Button closeSession;
    public Button toBeSentFile;
    private InputStreamService inputReader;
    private String ME;
    private boolean fileSendingMode = false, set = false;
    private Task contactUpdater;
    private Base64.Encoder encoder;
    private Base64.Decoder decoder;
    private AtomicBoolean gotConfirmation = new AtomicBoolean(false);
    private AtomicBoolean confirm = new AtomicBoolean(false);
    private SecretKey sendfilekey;
    private SecretKeySpec recievekey;

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    @FXML
    public void initialize() {

        if (encryptionState) {
            try {
                encoder = Base64.getEncoder();
                decoder = Base64.getDecoder();
                encryptCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
                encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
                decryptCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
                decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
            } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
                e.printStackTrace();
            }
            Task<SecretKey> aes = new Task<SecretKey>() {
                @Override
                protected SecretKey call() throws Exception {
                    KeyGenerator k = KeyGenerator.getInstance("AES");
                    k.init(128);
                    System.out.println("ENERATing key");
                    return k.generateKey();
                }
            };
            aes.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, event ->
            {
                System.out.println("key Generated.");
                sendfilekey = aes.getValue();
            });
            new Thread(aes).start();
        }
        //A task which updates friends connection in the contact if not present.
        contactUpdater = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (Files.exists(Paths.get("contact.json"))) {
                    FileReader reader = new FileReader("contact.json");
                    contacts = converter.fromJson(reader, HashMap.class);
                }
                if (!contacts.containsKey(FRIEND)) {
                    contacts.put(FRIEND.toLowerCase(), s.getInetAddress().getHostAddress());
                    String jsonMap = converter.toJson(contacts);
                    if (!Files.exists(Paths.get("contact.json")))
                        Files.createFile(Paths.get("contact.json"));
                    FileWriter writer = new FileWriter("contact.json");
                    writer.write(jsonMap);
                    writer.close();
                }
                return null;
            }
        };
        chat.textProperty().addListener(observable -> chat.setScrollTop(Double.MAX_VALUE));
        //Task to keep on listening for input from stream
        inputReader = new InputStreamService();
        inputReader.start();
    }

    //Helper method to initialize the name of chatting
    private void setString() {
        ME = (String) closeSession.getParent().getProperties().get(ControllerIndex.ME);
        FRIEND = (String) closeSession.getParent().getProperties().get(ControllerIndex.FRIEND);
        set = true;
        new Thread(contactUpdater).start();
    }

    private void forcedExit() {
        try {
            ControllerIndex.outputStream.close();
            if (listener != null) {
                ControllerIndex.listener.close();
            }
            ControllerIndex.s.close();
            //Alert for user
            Alert connectionClosed = new Alert(Alert.AlertType.ERROR);
            connectionClosed.setTitle("Connection closed.");
            connectionClosed.setContentText("Seems like " + FRIEND + " has disconnected.");
            connectionClosed.showAndWait();
            //Load index page
            Parent node = FXMLLoader.load(getClass().getResource("index.fxml"));
            Stage mystage = (Stage) send.getScene().getWindow();
            mystage.setTitle("ChatApp");
            mystage.setScene(new Scene(node, 600, 275));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void sendMessage() {
        String output;
        byte[] buffer;
        String source = "Me: ", msg = input.getText();
        if (msg.equals(""))
            return;
        if (!set)
            setString();
        try {
            output = ME + ": " + msg;
            if (encryptionState) {
                buffer = encrypt(output.getBytes(StandardCharsets.UTF_8));
                //Not using write object
                outputStream.writeObject(buffer);

            } else {
                outputStream.writeObject(output);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        updateScreen(msg, source);
    }

    //Helper method to update current screen
    private void updateScreen(String msg, String source) {
        chat.appendText(source + msg + "\n");
        input.setText("");
    }

    @FXML
    public void exit(ActionEvent actionEvent) {
        try {
            //Notifying the other user to exit.
            if (!s.isClosed()) {
                if (encryptionState) {
                    outputStream.writeObject(encrypt(EXIT.getBytes(StandardCharsets.UTF_8)));
                } else
                    outputStream.writeObject(EXIT);
            }
            //Stop current executing task close all sockets
            inputReader.cancel();
            if (!s.isClosed()) {
                ControllerIndex.outputStream.close();
                if (listener != null) {
                    ControllerIndex.listener.close();
                }
                ControllerIndex.s.close();
            }
            //Load index page
            Parent node = FXMLLoader.load(getClass().getResource("index.fxml"));
            Stage mystage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
            mystage.setTitle("ChatApp");
            mystage.setScene(new Scene(node, 600, 275));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendFile(ActionEvent actionEvent) {
        SendFileService sender = new SendFileService(encryptionState);
        sender.start();
    }

    //Executes in bg thread.
    private void recieveFile() {
        if (encryptionState) {
            recieveEncryptedFile();
        } else {
            AtomicBoolean resultValue = new AtomicBoolean(true);
            File recieveFile = null;
            System.out.println("Recieveing file");
            try {
                String fileName = (String) inputStream.readObject();
                recieveFile = new File("testing.txt");
                if (!recieveFile.createNewFile()) {
                    AtomicBoolean inputConfirmation = new AtomicBoolean(false);
                    Platform.runLater(() ->
                            buildOverwriteConfirmation(inputConfirmation, resultValue)
                    );
                    while (!inputConfirmation.get())
                        ;
                }
                //Replace filename after validation
                if (!recieveFile.canWrite()) {
                    //Alert recievefile cannot write
                    System.out.println("No permission");
                    outputStream.writeObject(IGNORE);
                    outputStream.writeObject(Boolean.FALSE);
                    Platform.runLater(() -> alertBuilder(Alert.AlertType.ERROR, "Error in recieving file.",
                            "You don't have permission to write in this directory."));
                    return ;
                } else if (!resultValue.get()) {
                    outputStream.writeObject(IGNORE);
                    outputStream.writeObject(Boolean.FALSE);
                    return;
                } else {//Give confirmation
                    File finalRecieveFile = recieveFile;
                    Platform.runLater(() -> {
                        RecieveFileService recieveFileService = new RecieveFileService(finalRecieveFile);
                        recieveFileService.start();
                    });
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void buildOverwriteConfirmation(AtomicBoolean inputConfirmation, AtomicBoolean resultValue) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setHeaderText("File already Exists");
        confirmation.setContentText("Do you want to overwrite the existing file.");
        ButtonType buttonTypeYes = new ButtonType("Yes");
        ButtonType buttonTypeNo = new ButtonType("No");
        confirmation.getButtonTypes().setAll(buttonTypeYes, buttonTypeNo);
        Optional<ButtonType> result = confirmation.showAndWait();
        result.ifPresent(buttonType -> {
            if (buttonType == buttonTypeYes)
                resultValue.set(true);
            else
                resultValue.set(false);
        });
        if(!result.isPresent())
            resultValue.set(false);
        inputConfirmation.set(true);
    }

    private void recieveEncryptedFile() {
        File recieveFile = null;
        System.out.println("Recieveing file");
        AtomicBoolean resultValue = new AtomicBoolean(true);
        try {
            String fileName = getDecryptedString((byte[]) inputStream.readObject());
            //Replace filename after validation
            recieveFile = new File("testing.txt");
            if (!recieveFile.createNewFile()) {
                AtomicBoolean inputConfirm = new AtomicBoolean(false);
                Platform.runLater(() -> buildOverwriteConfirmation(inputConfirm, resultValue));
                while (!inputConfirm.get())
                    ;
            }
            if (!recieveFile.canWrite()) {
                //Alert recievefile cannot write
                System.out.println("No permission");
                outputStream.writeObject(encrypt(IGNORE));
                outputStream.writeObject(Boolean.FALSE);
                Platform.runLater(() -> alertBuilder(Alert.AlertType.ERROR, "Error in recieving file.",
                        "You don't have permission to write in this directory."));
                return;
            } else if (!resultValue.get()){
                outputStream.writeObject(encrypt(IGNORE));
                outputStream.writeObject(Boolean.FALSE);
                return;
            }
                else {//Give confirmation
                File finalRecieveFile = recieveFile;
                Platform.runLater(() -> {
                    RecieveFileService recieveFileService = new RecieveFileService(finalRecieveFile);
                    recieveFileService.start();
                });
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private String getDecryptedString(byte[] encryptBytes) {
        if (encryptBytes!=null)
        return new String(decrypt(encryptBytes), StandardCharsets.UTF_8);
        else
            throw new NullPointerException();
    }

    //Create encrypt and decrypt method to ease Complexity
    private byte[] encrypt(byte[] simpleBytes) {
        try {
            return encoder.encode(encryptCipher.doFinal(simpleBytes));
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
        return null;
    }

    private byte[] encrypt(String simpleString) {
        return encrypt(simpleString.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] decrypt(byte[] encryptBytes) {
        try {
            return decryptCipher.doFinal(decoder.decode(encryptBytes));
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void alertBuilder(Alert.AlertType alertType, String headerText, String contentText) {
        Alert alert = new Alert(alertType);
        alert.setHeaderText(headerText);
        alert.setContentText(contentText);
        alert.show();
    }


    class InputStreamService extends Service {
        @Override
        protected Task createTask() {
            return new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    byte[] input1;
                    while (!isCancelled()) {
                        String msg;
                        if (fileSendingMode) {
                            confirm.set((Boolean) inputStream.readObject());
                            System.out.println("set confirmation: " + confirm.get());
                            gotConfirmation.set(true);
                        }
                        if (encryptionState) {
                            input1 = (byte[]) inputStream.readObject();
                            msg = getDecryptedString(input1);
                        } else {
                            msg = (String) inputStream.readObject();
                        }
                        if (msg.equals(EXIT))
                            break;
                        else if (msg.equals(OPFILE))
                            recieveFile();
                        Platform.runLater(() -> updateScreen(msg, ""));
                    }
                    //Quiting to index because server has left.
                    Platform.runLater(ControllerChat.this::forcedExit);
                    return null;
                }
            };
        }
    }

    class SendFileService extends Service {
        boolean encrypted;

        SendFileService(boolean encrypted) {
            this.encrypted = encrypted;
        }

        void sendUnencryptedFile() {
            fileSendingMode = true;
            System.out.println("set mode true");
            Socket fileSenderSocket = null;
            ObjectOutputStream fileOuputStream = null;
            FileInputStream fis = null;
            BufferedInputStream fileReader = null;
            //Build file selector
            File file = new File("success.txt");
            long length = file.length(), current = 0;
            int size;
            byte[] fileBuffer = new byte[BLOCK_SIZE];
            try {
                if (!file.canRead()) {
                    Platform.runLater(() -> alertBuilder(Alert.AlertType.ERROR, "Error in sending file.",
                            "You don't have permission to read the file."));
                    fileSendingMode = false;
                    return;
                } else {
                    outputStream.writeObject(OPFILE);
                    outputStream.writeObject(file.getName());
                    System.out.println("about to enter loop");
                    while (!gotConfirmation.get())
                        ;
                    System.out.println("passed confirmation area");
                    //checking whether ready to recieve in input service.
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            //client ready to recieve.?
            System.out.println(confirm.get());
            if (confirm.get()) {
                try {
                    fileSendingMode = false;
                    confirm.set(false);
                    System.out.println("Sender entered in confirm loop");
                    System.out.println("Trying to connect");
                    fileSenderSocket = new Socket(s.getInetAddress().getHostAddress(), 25100);
                    System.out.println("Conneted successfully");
                    fileOuputStream = new ObjectOutputStream(fileSenderSocket.getOutputStream());
                    fileOuputStream.writeObject(length);
                    fis = new FileInputStream(file);
                    fileReader = new BufferedInputStream(fis);
                    while (current < length) {
                        System.out.println("in loop");
                        if (length - current < BLOCK_SIZE) {
                            fileBuffer = new byte[(int) (length - current)];
                            fileReader.read(fileBuffer, 0, (int) (length - current));
                            current = length;
                        } else {
                            fileReader.read(fileBuffer, 0, fileBuffer.length);
                            current += fileBuffer.length;
                        }
                        fileOuputStream.writeObject(fileBuffer);
                    }
                    System.out.println("File sent");
                    //inputReader.start();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        assert fileOuputStream != null;
                        fileOuputStream.close();
                        assert fileSenderSocket != null;
                        fileSenderSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            } else {
                Platform.runLater(() -> alertBuilder(Alert.AlertType.ERROR, "Unable to send file.",
                        "Something went wrong on the reciever side."));
            }
        }

        private void sendEncryptedFile() {
            fileSendingMode = true;
            Socket fileSenderSocket = null;
            ObjectOutputStream fileOuputStream = null;
            FileInputStream fis = null;
            BufferedInputStream fileReader = null;
            Cipher fileEncrypt = null;
            //Build file selector
            File file = new File("success.txt");
            long length = file.length(), current = 0;
            int size;
            byte[] fileBuffer = new byte[BLOCK_SIZE];
            try {
                if (!file.canRead()) {
                    Platform.runLater(() -> alertBuilder(Alert.AlertType.ERROR, "Error in sending file.",
                            "You don't have permission to read the file."));
                    fileSendingMode = false;
                    return;
                } else {
                    outputStream.writeObject(encrypt(OPFILE));
                    outputStream.writeObject(encrypt(file.getName()));
                    System.out.println("about to enter loop");
                    while (!gotConfirmation.get())
                        ;
                    System.out.println("passed confirmation area");
                    //checking whether ready to recieve in input service.
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            //client ready to recieve.?
            System.out.println(confirm.get());
            if (confirm.get()) {
                try {
                    fileSendingMode = false;
                    confirm.set(false);
                    System.out.println("Sender entered in confirm loop");
                    System.out.println("Trying to connect");
                    fileSenderSocket = new Socket(s.getInetAddress().getHostAddress(), 25100);
                    System.out.println("Conneted successfully");
                    fileOuputStream = new ObjectOutputStream(fileSenderSocket.getOutputStream());
                    fileOuputStream.writeObject(length);
                    fileOuputStream.writeObject(encrypt(sendfilekey.getEncoded()));
                    fileEncrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    byte[] iv = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
                    IvParameterSpec ivspec = new IvParameterSpec(iv);
                    fileEncrypt.init(Cipher.ENCRYPT_MODE, sendfilekey, ivspec);
                    fis = new FileInputStream(file);
                    fileReader = new BufferedInputStream(fis);
                    while (current < length) {
                        System.out.println("in loop");
                        if (length - current < BLOCK_SIZE) {
                            fileBuffer = new byte[(int) (length - current)];
                            fileReader.read(fileBuffer, 0, (int) (length - current));
                            current = length;
                        } else {
                            fileReader.read(fileBuffer, 0, fileBuffer.length);
                            current += fileBuffer.length;
                        }
                        fileOuputStream.writeObject(fileEncrypt.doFinal(fileBuffer));

                    }
                    System.out.println("File sent");
                    fileReader.close();
                    fis.close();
                    //inputReader.start();
                } catch (IOException | NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | InvalidAlgorithmParameterException | BadPaddingException | IllegalBlockSizeException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (fileOuputStream != null) {
                            fileOuputStream.close();
                        }
                        if (fileSenderSocket != null) {
                            fileSenderSocket.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            } else {
                Platform.runLater(() -> alertBuilder(Alert.AlertType.ERROR, "Unable to send file.",
                        "Something went wrong on the reciever side."));
            }
        }

        @Override
        protected Task createTask() {
            return new Task() {
                @Override
                protected Object call() throws Exception {
                    System.out.println("service started.");
                    if (encrypted)
                        sendEncryptedFile();
                    else
                        sendUnencryptedFile();
                    return null;
                }
            };
        }
    }


    class RecieveFileService extends Service {
        File recieveFile;

        RecieveFileService(File recieveFile) {
            this.recieveFile = recieveFile;
        }

        @Override
        protected Task createTask() {
            return new Task() {
                @Override
                protected Object call() throws Exception {
                    long length = 0, current = 0;
                    ServerSocket fileRecieveServer = null;
                    Socket fileRecieveSocket = null;
                    ObjectInputStream fileInputStream = null;
                    if (encryptionState) {
                        Cipher fileDecrypt;
                        byte[] iv = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
                        IvParameterSpec ivspec = new IvParameterSpec(iv);
                        try {
                            outputStream.writeObject(encrypt(IGNORE));
                            fileRecieveServer = new ServerSocket(25100);
                            outputStream.writeObject(Boolean.TRUE);
                            System.out.println("Confirmaton sent.");
                            fileRecieveSocket = fileRecieveServer.accept();
                            System.out.println("Connection accepted");
                            fileInputStream = new ObjectInputStream(fileRecieveSocket.getInputStream());
                            length = (long) fileInputStream.readObject();
                            byte[] decrypt_key = decrypt((byte[]) fileInputStream.readObject());
                            assert decrypt_key != null;
                            recievekey = new SecretKeySpec(decrypt_key, "AES");
                            fileDecrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
                            fileDecrypt.init(Cipher.DECRYPT_MODE, recievekey, ivspec);
                            assert recieveFile != null;
                            FileOutputStream writer = new FileOutputStream(recieveFile);
                            BufferedOutputStream bos = new BufferedOutputStream(writer);
                            byte[] fileBuffer;
                            while (current < length) {
                                System.out.println("in recieve loop");
                                fileBuffer = fileDecrypt.doFinal((byte[]) fileInputStream.readObject());
                                bos.write(fileBuffer);
                                current += fileBuffer.length;
                            }
                            bos.write("recieved".getBytes());
                            bos.flush();
                            bos.close();
                            writer.close();
                            System.out.println("file recieved");
                        } catch (IOException | ClassNotFoundException | NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | BadPaddingException | InvalidAlgorithmParameterException | IllegalBlockSizeException e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                if (fileRecieveSocket != null) {
                                    fileRecieveSocket.close();
                                }
                                if (fileInputStream != null) {
                                    fileInputStream.close();
                                }
                                if (fileRecieveServer != null) {
                                    fileRecieveServer.close();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        outputStream.writeObject(IGNORE);
                        fileRecieveServer = new ServerSocket(25100);
                        outputStream.writeObject(Boolean.TRUE);
                        System.out.println("Confirmaton sent.");
                        try {
                            fileRecieveSocket = fileRecieveServer.accept();
                            System.out.println("Connection accepted");
                            fileInputStream = new ObjectInputStream(fileRecieveSocket.getInputStream());
                            length = (long) fileInputStream.readObject();
                            assert recieveFile != null;
                            FileOutputStream writer = new FileOutputStream(recieveFile);
                            BufferedOutputStream bos = new BufferedOutputStream(writer);
                            byte[] fileBuffer;
                            while (current < length) {
                                System.out.println("in recieve loop");
                                fileBuffer = (byte[]) fileInputStream.readObject();
                                bos.write(fileBuffer);
                                current += fileBuffer.length;
                            }
                            bos.write("recieved".getBytes());
                            bos.close();
                            writer.close();
                            System.out.println("file recieved");
                        } catch (IOException | ClassNotFoundException e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                fileRecieveServer.close();
                                if (fileRecieveSocket != null) {
                                    fileRecieveSocket.close();
                                }
                                if (fileInputStream != null) {
                                    fileInputStream.close();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    return null;
                }
            };
        }
    }
}

