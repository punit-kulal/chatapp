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
    public TextField input;
    public Button send;
    public Button closeSession;
    public TextField toBeSentFile;
    public ProgressBar fileLoad;
    public Button cancelSending;
    public Button saveChat;
    private Cipher decryptCipher;
    private InputStreamService inputReader;
    private boolean fileSendingMode = false, set = false;
    private Task contactUpdater;
    private Base64.Encoder encoder;
    private Base64.Decoder decoder;
    private AtomicBoolean gotConfirmation = new AtomicBoolean(false);
    private AtomicBoolean confirm = new AtomicBoolean(false);
    private SecretKey sendfilekey;
    private SecretKeySpec recievekey;
    private SendFileService sender;
    private RecieveFileService recieveFileService;

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    @FXML
    public void initialize() {
        //Initialoze keys for encryption
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
                    return k.generateKey();
                }
            };
            aes.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, event ->
                    sendfilekey = aes.getValue());
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
                if (!contacts.containsKey(Friend)) {
                    contacts.put(Friend.toLowerCase(), s.getInetAddress().getHostAddress());
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
        //Scroll automatically woth chAT
        chat.textProperty().addListener(observable -> chat.setScrollTop(Double.MAX_VALUE));
        //Task to keep on listening for input from stream
        inputReader = new InputStreamService();
        inputReader.start();
    }

    //Helper method to initialize the name of chatting
    private void setString() {
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
            alertBuilder(Alert.AlertType.ERROR, "Connection closed.", "Seems like " + Friend + " has disconnected.");
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
            output = MyName + ": " + msg;
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
        chat.appendText(source.toUpperCase() + " " + msg + "\n");
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
        //disable send key
        sendFile.setDisable(true);
        sender = new SendFileService(encryptionState);
        //Enabling send button.
        sender.setOnFailed(event -> sendFile.setDisable(false));
        sender.setOnSucceeded(event -> sendFile.setDisable(false));
        sender.setOnCancelled(event -> sendFile.setDisable(false));
        sender.start();
    }

    //Executes in bg thread.
    private void receiveFile() {
        sendFile.setDisable(true);
        if (encryptionState) {
            recieveEncryptedFile();
        } else {
            AtomicBoolean resultValue = new AtomicBoolean(true);
            File recieveFile = null;
            try {
                String fileName = (String) inputStream.readObject();
                recieveFile = new File(fileName);
                checkForOverwrite(recieveFile, resultValue);
                //Replace filename after validation
                if (!recieveFile.canWrite()) {
                    //Alert recieve file cannot write to directory
                    outputStream.writeObject(IGNORE);
                    outputStream.writeObject(Boolean.FALSE);
                    Platform.runLater(() -> alertBuilder(Alert.AlertType.ERROR, "Error in recieving file.",
                            "You don't have permission to write in this directory."));
                    sendFile.setDisable(false);
                } else if (!resultValue.get()) {
                    //Denied pemission to overwrite current file.
                    outputStream.writeObject(IGNORE);
                    outputStream.writeObject(Boolean.FALSE);
                    sendFile.setDisable(false);
                } else {//Give confirmation
                    File finalRecieveFile = recieveFile;
                    Platform.runLater(() -> {
                        recieveFileService = new RecieveFileService(finalRecieveFile);
                        resetSendButtonOnCompletion(recieveFileService);
                        recieveFileService.start();
                    });
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void resetSendButtonOnCompletion(RecieveFileService recieveFileService) {
        recieveFileService.setOnCancelled(event -> sendFile.setDisable(false));
        recieveFileService.setOnSucceeded(event -> sendFile.setDisable(false));
        recieveFileService.setOnFailed(event -> sendFile.setDisable(false));
    }

    private void checkForOverwrite(File recieveFile, AtomicBoolean resultValue) throws IOException {
        if (!recieveFile.createNewFile()) {
            AtomicBoolean inputConfirmation = new AtomicBoolean(false);
            Platform.runLater(() ->
                    buildOverwriteConfirmation(inputConfirmation, resultValue)
            );
            while (!inputConfirmation.get())
                ;
        }
    }

    private void recieveEncryptedFile() {
        File recieveFile = null;
        AtomicBoolean resultValue = new AtomicBoolean(true);
        try {
            String fileName = getDecryptedString((byte[]) inputStream.readObject());
            //Replace filename after validation
            recieveFile = new File(fileName);
            checkForOverwrite(recieveFile, resultValue);
            if (!recieveFile.canWrite()) {
                //Alert recievefile cannot write
                outputStream.writeObject(encrypt(IGNORE));
                outputStream.writeObject(Boolean.FALSE);
                Platform.runLater(() -> alertBuilder(Alert.AlertType.ERROR, "Error in recieving file.",
                        "You don't have permission to write in this directory."));
                sendFile.setDisable(false);
            } else if (!resultValue.get()) {
                outputStream.writeObject(encrypt(IGNORE));
                outputStream.writeObject(Boolean.FALSE);
                sendFile.setDisable(false);
            } else {//Give confirmation
                File finalRecieveFile = recieveFile;
                Platform.runLater(() -> {
                    recieveFileService = new RecieveFileService(finalRecieveFile);
                    resetSendButtonOnCompletion(recieveFileService);
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

    public void setFile(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select file to send.");
        toSend = fileChooser.showOpenDialog(new Stage());
        if (toSend != null)
            toBeSentFile.setText(toSend.getName());
    }

    private void updateProgressBar(double current, double length, boolean progressBarPresent) {
        if (progressBarPresent) {
            Platform.runLater(() -> fileLoad.setProgress((current / length)));
        }
    }

    private boolean setProgressBar(long length) {
        if (length > 1024 * 1024 * 2) {
            Platform.runLater(() -> {
                fileLoad.setProgress(0);
                fileLoad.setVisible(true);
                cancelSending.setVisible(true);
            });
            return true;
        }
        return false;
    }

    private void updateFileInformationInChatBox(String s, File file) {
        Platform.runLater(() -> updateScreen(s + file.getName() + " Size: " + file.length() / 1024 + "KB", ""));
    }

    public void cancelSend(ActionEvent actionEvent) {
        if (sender != null && sender.isRunning()) {
            sender.cancel();
            sender.reset();
        }
        if (recieveFileService != null && recieveFileService.isRunning()) {
            recieveFileService.cancel();
            recieveFileService.reset();
        }
    }

    public void saveChat(ActionEvent actionEvent) {
        FileChooser savetxt = new FileChooser();
        savetxt.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        savetxt.setTitle("Click location to save file");
        File fileToBeSaved = savetxt.showSaveDialog(new Stage());
        String data = chat.getText();
        if (fileToBeSaved != null) {
            FileSaveTask task = new FileSaveTask(fileToBeSaved, data);
            task.setOnSucceeded(event -> alertBuilder(Alert.AlertType.INFORMATION, "Success!", "File saved Successfully as " + fileToBeSaved.getName()));
            new Thread(task).start();
        }
    }

    //Also resets important parameter.
    private void closeProgressBar(boolean progressBarPresent) {
        if (progressBarPresent) {
            Platform.runLater(() -> {
                fileLoad.setProgress(0);
                fileLoad.setVisible(false);
                cancelSending.setVisible(false);
            });
        }
        reset();
    }

    private void reset() {
        gotConfirmation.set(false);
        fileSendingMode = false;
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
                            receiveFile();
                        else if (msg.equals(IGNORE))
                            ;
                        else
                            //Dont add source since sendr attaches his name;
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
                    while (!gotConfirmation.get())
                        ;
                    //checking whether ready to recieve in input service.
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            //client ready to recieve.?
            if (confirm.get()) {
                boolean progressBarPresent = false;
                //Sending Block try with resources statement
                try (Socket fileSenderSocket = new Socket(s.getInetAddress().getHostAddress(), 25100);
                     ObjectOutputStream fileSendStream = new ObjectOutputStream(fileSenderSocket.getOutputStream());
                     FileInputStream fis = new FileInputStream(file);
                     BufferedInputStream fileReader = new BufferedInputStream(fis);
                     ObjectInputStream objectInputStream = new ObjectInputStream(fileSenderSocket.getInputStream())) {
                    fileSendingMode = false;
                    confirm.set(false);
                    ReceiverFeedbackReceiveTask t = new ReceiverFeedbackReceiveTask(objectInputStream);
                    new Thread(t).start();
                    progressBarPresent = setProgressBar(length);
                    fileSendStream.writeObject(length);
                    while (current < length && !task.isCancelled()) {
                        if (length - current < BLOCK_SIZE) {
                            fileBuffer = new byte[(int) (length - current)];
                            fileReader.read(fileBuffer, 0, (int) (length - current));
                            current = length;
                        } else {
                            fileReader.read(fileBuffer, 0, fileBuffer.length);
                            current += fileBuffer.length;
                        }
                        fileSendStream.writeObject(fileBuffer);
                        updateProgressBar(current, length, progressBarPresent);
                    }
                    closeProgressBar(progressBarPresent);
                    if (!task.isCancelled())
                        updateFileInformationInChatBox("File Sent ", file);
                    toSend = null;
                    Platform.runLater(() -> toBeSentFile.setText(""));
                } catch (IOException e) {
                    e.printStackTrace();
                    //If task cancelled by current user dont show error.
                    if (!task.isCancelled()) {
                        Platform.runLater(() -> alertBuilder(Alert.AlertType.ERROR, "Sending failed",
                                Friend + " has cancelled recieving the file."));
                    }
                    closeProgressBar(progressBarPresent);
                }
            } else {
                Platform.runLater(() -> alertBuilder(Alert.AlertType.ERROR, "Unable to send file.",
                        "Something went wrong on the reciever side."));
            }
        }

        private void sendEncryptedFile(Task task) {
            fileSendingMode = true;
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
                    while (!gotConfirmation.get())
                        ;
                    //checking whether ready to recieve in input service.
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            //client ready to recieve.?
            if (confirm.get()) {
                boolean progressBarPresent = false;
                try (Socket fileSenderSocket = new Socket(s.getInetAddress().getHostAddress(), 25100);
                     ObjectOutputStream fileOuputStream = new ObjectOutputStream(fileSenderSocket.getOutputStream());
                     FileInputStream fis = new FileInputStream(file);
                     BufferedInputStream fileReader = new BufferedInputStream(fis);
                     ObjectInputStream objectInputStream = new ObjectInputStream(fileSenderSocket.getInputStream())) {
                    fileSendingMode = false;
                    confirm.set(false);
                    ReceiverFeedbackReceiveTask t = new ReceiverFeedbackReceiveTask(objectInputStream);
                    new Thread(t).start();
                    progressBarPresent = setProgressBar(length);
                    fileOuputStream.writeObject(length);
                    fileOuputStream.writeObject(encrypt(sendfilekey.getEncoded()));
                    fileEncrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    byte[] iv = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
                    IvParameterSpec ivspec = new IvParameterSpec(iv);
                    fileEncrypt.init(Cipher.ENCRYPT_MODE, sendfilekey, ivspec);
                    while (current < length && !task.isCancelled()) {
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
                            updateProgressBar(current, length, progressBarPresent);
                        }
                    }
                    closeProgressBar(progressBarPresent);
                    long finalLength = length;
                    if (!task.isCancelled())
                        updateFileInformationInChatBox("File Sent ", file);
                    else

                    toSend = null;
                    Platform.runLater(() -> toBeSentFile.setText(""));
                } catch (IOException e) {
                    e.printStackTrace();
                    if (!task.isCancelled()) {
                        Platform.runLater(() -> Platform.runLater(() -> alertBuilder(Alert.AlertType.ERROR, "Sending failed",
                                Friend + " has cancelled recieving the file.")));
                    }
                    closeProgressBar(progressBarPresent);
                } catch (NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException e) {
                    e.printStackTrace();
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

                @Override
                protected Object call() throws Exception {
                    boolean progressBarPresent = false;
                    SenderCancelledCheckerTask s = null;
                    if (encryptionState) {
                        Cipher fileDecrypt;
                        byte[] iv = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
                        IvParameterSpec ivspec = new IvParameterSpec(iv);
                        outputStream.writeObject(encrypt(IGNORE));
                        outputStream.writeObject(Boolean.TRUE);
                        try (ServerSocket fileRecieveServer = new ServerSocket(25100);
                             Socket fileRecieveSocket = fileRecieveServer.accept();
                             ObjectInputStream fileInputStream = new ObjectInputStream(fileRecieveSocket.getInputStream());
                             FileOutputStream fos = new FileOutputStream(recieveFile);
                             BufferedOutputStream bos = new BufferedOutputStream(fos);
                             ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileRecieveSocket.getOutputStream())) {
                            length = (long) fileInputStream.readObject();
                            byte[] decrypt_key = decrypt((byte[]) fileInputStream.readObject());
                            assert decrypt_key != null;
                            recievekey = new SecretKeySpec(decrypt_key, "AES");
                            fileDecrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
                            fileDecrypt.init(Cipher.DECRYPT_MODE, recievekey, ivspec);
                            progressBarPresent = setProgressBar(length);
                            assert recieveFile != null;
                            byte[] fileBuffer;
                            s = new SenderCancelledCheckerTask(objectOutputStream);
                            new Thread(s).start();
                            while (current < length && !isCancelled()) {
                                fileBuffer = fileDecrypt.doFinal((byte[]) fileInputStream.readObject());
                                bos.write(fileBuffer);
                                current += fileBuffer.length;
                                updateProgressBar(current, length, progressBarPresent);
                            }
                            if (!isCancelled())
                                updateFileInformationInChatBox("File Recieved :", recieveFile);
                            else
                                Platform.runLater(() -> updateScreen("File transfer cancelled by user. Recieved: " + current / 1024 + "KB",
                                        ""));
                            closeProgressBar(progressBarPresent);
                            //File receive completes here
                        } catch (IOException e) {
                            e.printStackTrace();
                            if (!isCancelled()) {
                                Platform.runLater(() -> alertBuilder(Alert.AlertType.ERROR, "Sending failed",
                                        Friend + " has cancelled sending the file or the connection was interrupted."));
                            }
                            closeProgressBar(progressBarPresent);
                        } catch (ClassNotFoundException | NoSuchPaddingException | NoSuchAlgorithmException | BadPaddingException | InvalidAlgorithmParameterException | IllegalBlockSizeException | InvalidKeyException e) {
                            e.printStackTrace();
                        }
                    } else {
                        outputStream.writeObject(IGNORE);
                        outputStream.writeObject(Boolean.TRUE);
                        ServerSocket fileRecieveServer = new ServerSocket(25100);
                        try (Socket fileRecieveSocket = fileRecieveServer.accept();
                             ObjectInputStream fileRecieveStream = new ObjectInputStream(fileRecieveSocket.getInputStream());
                             FileOutputStream writer = new FileOutputStream(recieveFile);
                             BufferedOutputStream bos = new BufferedOutputStream(writer);
                             ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileRecieveSocket.getOutputStream())) {
                            length = (long) fileRecieveStream.readObject();
                            s = new SenderCancelledCheckerTask(objectOutputStream);
                            new Thread(s).start();
                            progressBarPresent = setProgressBar(length);
                            byte[] fileBuffer;
                            while (current < length && !isCancelled()) {
                                fileBuffer = (byte[]) fileRecieveStream.readObject();
                                bos.write(fileBuffer);
                                current += fileBuffer.length;
                                updateProgressBar(current, length, progressBarPresent);
                            }
                            if (!isCancelled())
                                updateFileInformationInChatBox("File Recieved :", recieveFile);
                            else
                                Platform.runLater(() -> updateScreen("File transfer cancelled by user. Recieved: " + current / 1024 + "KB",
                                        ""));
                            closeProgressBar(progressBarPresent);
                        } catch (IOException e) {
                            e.printStackTrace();
                            if (!isCancelled()) {
                                Platform.runLater(() -> alertBuilder(Alert.AlertType.ERROR, "Receiving failed",
                                        Friend + " has cancelled sending the file or the connection was interrupted."));
                            }
                            closeProgressBar(progressBarPresent);
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                        s.cancel();
                    }
                    return null;
                }

            };
        }
    }

    class FileSaveTask extends Task {
        File file;
        String data;

        FileSaveTask(File file, String data) {
            this.file = file;
            this.data = data;
        }

        @Override
        protected Object call() throws Exception {
            try (PrintWriter out = new PrintWriter(file.getName())) {
                out.println(data);
            }
            return null;
        }
    }

    class SenderCancelledCheckerTask extends Task {
        ObjectOutputStream senderOnChecker;

        SenderCancelledCheckerTask(ObjectOutputStream senderOnChecker) {
            this.senderOnChecker = senderOnChecker;
        }

        @Override
        protected Object call() throws Exception {
            try {
                Thread.sleep(5000);
                while (!isCancelled()) {
                    senderOnChecker.writeObject(isRunning);
                    Thread.sleep(5000);
                }
            } catch (IOException e) {
                e.printStackTrace();
                senderOnChecker.close();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    class ReceiverFeedbackReceiveTask extends Task {
        ObjectInputStream obj;

        ReceiverFeedbackReceiveTask(ObjectInputStream feedbackInputStream) {
            this.obj = feedbackInputStream;
        }

        @Override
        protected Object call() {
            try {
                while (obj != null)
                    obj.readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
