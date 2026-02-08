import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main application class for the InkRush game client.
 * Launches the Lobby Screen.
 */
public class ClientApp extends Application {

    /**
     * Main entry point for the application
     * @param args command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Starts the JavaFX application and displays the Lobby window.
     */
    @Override
    public void start(Stage stage) {
        try {
            // Wrap the loading code to catch the hidden error
            Parent root = FXMLLoader.load(getClass().getResource("Lobby.fxml"));

            Scene scene = new Scene(root);
            stage.setTitle("InkRush Client");
            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            // This will print the ACTUAL error to your console
            System.out.println("CRITICAL ERROR DURING STARTUP:");
            e.printStackTrace();
        }
    }

    /**
     * Handles application shutdown.
     */
    @Override
    public void stop() {
        // System.exit(0) ensures all background threads (like the server listener)
        // are killed when the window closes.
        System.exit(0);
    }
}