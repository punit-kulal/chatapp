package sample;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		Parent root = FXMLLoader.load(getClass().getResource("index.fxml"));
		primaryStage.setTitle("ChatApp");
		Scene index = new Scene(root, 600, 275);
		primaryStage.setScene(index);
		primaryStage.show();
		primaryStage.getOnCloseRequest();
	}

	//To close sockets when close butoon is clicked
	@Override
	public void stop() throws Exception {
		if (ControllerIndex.outputStream != null)
			ControllerIndex.outputStream.close();
		if (ControllerIndex.listener != null)
			ControllerIndex.listener.close();
		if (ControllerIndex.s != null)
			ControllerIndex.s.close();
	}
}



