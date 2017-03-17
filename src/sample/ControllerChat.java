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
import javafx.scene.control.*;
import javafx.stage.FileChooser;
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

import static sample.Constant.*;

/*
  Created by Punit Kulal on 2/21/2017.
 */
public class ControllerChat {
    public Button Browse;
    public Button sendFile;
    public TextArea chat;
    public TextArea input;
    public Button send;
    public Button closeSession;
    public TextField toBeSentFile;
    public ProgressBar fileLoad;
    public Button cancelSendning;
    private Cipher decryptCipher;
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
    private SendFileService sender;
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
        ME = (String) closeSession.getParent().getProperties().get(ME);
        FRIEND = (String) closeSession.getParent().getProperties().get(FRIEND);
        set = true;
        new Thread(contactUpdater).start();
    }

    private void forcedExit() {
        try {
            outputStream.close();
            if (listener != null) {
                listener.close();
            }
            s.close();
            //Alert for user
            alertBuilder(Alert.AlertType.ERROR, "Connection closed.", "Seems like " + FRIEND + " has disconnected.");
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
                outputStream.close();
                if (listener != null) {
                    listener.close();
                }
                s.close();
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
        sender = new SendFileService(encryptionState);
        //fileLoad.progressProperty().bind(sender.progressProperty());
        //sender.setOnSucceeded(event -> fileLoad.progressProperty().unbind());
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
                recieveFile = new File(fileName);
                if (!recieveFile.createNewFile()) {
                    AtomicBoolean inputConfirmation = new AtomicBoolean(false);
                    Platform.runLater(() ->
                            buildOverwriteConfirmation(inputConfirmation, resultValue)
                    );
                    while (!inputConfirmation.get())
                        System.out.println("in waiting object loop.");
                }
                //Replace filename after validation
                if (!recieveFile.canWrite()) {
                    //Alert recievefile cannot write
                    System.out.println("No permission");
                    outputStream.writeObject(IGNORE);
                    outputStream.writeObject(Boolean.FALSE);
                    Platform.runLater(() -> alertBuilder(Alert.AlertType.ERROR, "Error in recieving file.",
                            "You don't have permission to write in this directory."));
                    return;
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

    private void recieveEncryptedFile() {
        File recieveFile = null;
        System.out.println("Recieveing file");
        AtomicBoolean resultValue = new AtomicBoolean(true);
        try {
            String fileName = getDecryptedString((byte[]) inputStream.readObject());
            //Replace filename after validation
            recieveFile = new File(fileName);
            if (!recieveFile.createNewFile()) {
                AtomicBoolean inputConfirm = new AtomicBoolean(false);
                Platform.runLater(() -> buildOverwriteConfirmation(inputConfirm, resultValue));
                while (!inputConfirm.get())
                    System.out.println("in waiting object loop.");
            }
            if (!recieveFile.canWrite()) {
                //Alert recievefile cannot write
                System.out.println("No permission");
                outputStream.writeObject(encrypt(IGNORE));
                outputStream.writeObject(Boolean.FALSE);
                Platform.runLater(() -> alertBuilder(Alert.AlertType.ERROR, "Error in recieving file.",
                        "You don't have permission to write in this directory."));
                return;
            } else if (!resultValue.get()) {
                outputStream.writeObject(encrypt(IGNORE));
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
        if (!result.isPresent())
            resultValue.set(false);
        inputConfirmation.set(true);
    }

    private String getDecryptedString(byte[] encryptBytes) {
        if (encryptBytes != null)
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

    public void setFile(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select file to send.");
        toSend = fileChooser.showOpenDialog(new Stage());
        if (toSend != null)
            toBeSentFile.setText(toSend.getName());
    }

    private void updateProgressBar(double current,double length, boolean progressBarPresent) {
        if (progressBarPresent) {
            System.out.println("sending "+ current/length +" % complete");
            Platform.runLater(() -> fileLoad.setProgress((current / length)));
        }
    }

    private boolean setProgressBar(long length) {
        if (length > 1024 * 1024 * 2) {
            Platform.runLater(() -> {
                fileLoad.setProgress(0);
                fileLoad.setVisible(true);
                cancelSendning.setVisible(true);
            });
            return true;
        }
        return false;
    }

    private void updateFileInformationInChatBox(String s, File file) {
        Platform.runLater(() -> updateScreen(s + file.getName() + " Size: " + file.length() / 1024 + "KB", ""));
    }

    public void cancelSend(ActionEvent actionEvent) {
        sender.cancel();
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
                        else if (msg.equals(IGNORE))
                            ;
                        else
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

        private void sendUnencryptedFile(Task task) {
            fileSendingMode = true;
            System.out.println("set mode true");
            Socket fileSenderSocket = null;
            ObjectOutputStream fileOuputStream = null;
            FileInputStream fis = null;
            BufferedInputStream fileReader = null;
            //Build file selector
            File file = toSend;
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
                    boolean progressBarPresent=setProgressBar(length);
                    System.out.println("Progress Bar :"+progressBarPresent);
                    System.out.println("set up progressbar");
                    fis = new FileInputStream(file);
                    fileReader = new BufferedInputStream(fis);
                    while (current < length && !task.isCancelled()) {
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
                        updateProgressBar(current, length, progressBarPresent);
                    }
                    closeProgressBar(progressBarPresent);
                    updateFileInformationInChatBox("File Sent: ", file);
                    toSend = null;
                    Platform.runLater(() -> toBeSentFile.setText(""));
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

        private void sendEncryptedFile(Task task) {
            fileSendingMode = true;
            Socket fileSenderSocket = null;
            ObjectOutputStream fileOuputStream = null;
            FileInputStream fis = null;
            BufferedInputStream fileReader = null;
            Cipher fileEncrypt = null;
            //Build file selector
            File file = toSend;
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
                    boolean progressBarPresent=setProgressBar(length);
                    fileOuputStream = new ObjectOutputStream(fileSenderSocket.getOutputStream());
                    fileOuputStream.writeObject(length);
                    fileOuputStream.writeObject(encrypt(sendfilekey.getEncoded()));
                    fileEncrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    byte[] iv = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
                    IvParameterSpec ivspec = new IvParameterSpec(iv);
                    fileEncrypt.init(Cipher.ENCRYPT_MODE, sendfilekey, ivspec);
                    fis = new FileInputStream(file);
                    fileReader = new BufferedInputStream(fis);
                    while (current < length && !task.isCancelled()) {
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
                        if (length > 1024 * 1024 * 2) {
                            long finalCurrent = current;
                            updateProgressBar(current,length, progressBarPresent);
                        }
                    }
                    closeProgressBar(progressBarPresent);
                    long finalLength = length;
                    updateFileInformationInChatBox("File Sent ", file);
                    toSend = null;
                    Platform.runLater(() -> toBeSentFile.setText(""));
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
                    if (toSend == null) {
                        Platform.runLater(() -> alertBuilder(Alert.AlertType.WARNING, "Unable to send file", "No File Selected"));
                        return null;
                    }
                    if (encrypted)
                        sendEncryptedFile(this);
                    else
                        sendUnencryptedFile(this);
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
                long length = 0, current = 0;
                ServerSocket fileRecieveServer = null;
                Socket fileRecieveSocket = null;
                ObjectInputStream fileInputStream = null;
                FileOutputStream writer = null;
                BufferedOutputStream bos = null;
                @Override
                protected Object call() throws Exception {

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
                            boolean progressBarPresent=setProgressBar(length);
                            assert recieveFile != null;
                            FileOutputStream writer = new FileOutputStream(recieveFile);
                            BufferedOutputStream bos = new BufferedOutputStream(writer);
                            byte[] fileBuffer;
                            while (current < length && !isCancelled()) {
                                System.out.println("in recieve loop");
                                fileBuffer = fileDecrypt.doFinal((byte[]) fileInputStream.readObject());
                                bos.write(fileBuffer);
                                current += fileBuffer.length;
                                updateProgressBar(current,length, progressBarPresent);
                            }
                            updateFileInformationInChatBox("File Recieved :", recieveFile);
                            closeProgressBar(progressBarPresent);
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
                            boolean progressBarPresent = setProgressBar(length);
                            System.out.println("Created progress Bar");
                            writer = new FileOutputStream(recieveFile);
                            bos = new BufferedOutputStream(writer);
                            byte[] fileBuffer;
                            while (current < length && !isCancelled()) {
                                System.out.println("in recieve loop");
                                fileBuffer = (byte[]) fileInputStream.readObject();
                                bos.write(fileBuffer);
                                current += fileBuffer.length;
                                updateProgressBar(current,length,progressBarPresent);
                            }
                            updateFileInformationInChatBox("File Recieved :", recieveFile);
                            closeProgressBar(progressBarPresent);
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

    //Also resets important parameter.
    private void closeProgressBar(boolean progressBarPresent) {
        if (progressBarPresent){
         Platform.runLater(() -> {fileLoad.setProgress(0);
         fileLoad.setVisible(false);
         cancelSendning.setVisible(false);});
        }
        reset();
    }

    private void reset(){
        gotConfirmation.set(false);
        fileSendingMode=false;
    }
}

//TODO Implement cancelling of file transfer