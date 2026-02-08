import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main application class for InkRush game server.
 */
public class ServerApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        try {
            // --- 1. Initialize WordBank dependency ---
            String filePath = "words.txt";
            WordBank wordBank = new WordBank(filePath);

            // --- 2. Load FXML and Inject Dependency ---
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Server.fxml"));
            Parent root = loader.load();

            ServerController controller = loader.getController();

            //Pass the initialized WordBank to the controller
            controller.setWordBankDependency(wordBank);

            Scene scene = new Scene(root);
            stage.setTitle("InkRush Server");
            stage.setScene(scene);
            stage.show();

            controller.runServer();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    /**
     * Handles application shutdown.
     * Ensures server resources are properly closed.
     */
    @Override
    public void stop(){
        System.exit(0);
    }
}

