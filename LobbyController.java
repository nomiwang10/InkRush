import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.scene.media.AudioClip; // Import for sound effects
import javafx.scene.media.Media;     // Import for music
import javafx.scene.media.MediaPlayer; // Import for music player

import java.io.IOException;
import java.net.URL;

/**
 * Controller for the Lobby screen.
 * Handles user input for Username and Server IP address.
 * Validates input and transitions to the Game (Canvas) screen.
 */
public class LobbyController {

    @FXML
    private Button joinButton;

    @FXML
    private TextField playerNameField;

    @FXML
    private TextField ipAddressField;

    @FXML
    private ListView<String> playersListView;

    @FXML
    // Matches the fx:id in your FXML
    private Canvas avatarCanvas;

    // Tracking for drawing
    private double lastX;
    private double lastY;

    // --- Sound Fields ---
    private MediaPlayer backgroundMusicPlayer;
    private AudioClip joinSound;

    /**
     * Initializes the controller class.
     * Sets default values and prepares the UI.
     */
    @FXML
    public void initialize() {
        // Set a default IP for easier testing, or leave empty
        if (ipAddressField.getText().isEmpty()) {
            ipAddressField.setText("localhost");
        }

        // Ensure button is enabled so users can attempt to join
        joinButton.setDisable(false);

        // Setup the drawing logic
        setupAvatarDrawing();

        // Load and Play Background Music ---
        setupAudio();
    }

    private void setupAvatarDrawing() {
        GraphicsContext gc = avatarCanvas.getGraphicsContext2D();
        gc.setLineWidth(3);
        gc.setStroke(Color.BLACK);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);

        // Draw a dot on click
        avatarCanvas.setOnMousePressed(e -> {
            lastX = e.getX();
            lastY = e.getY();
            gc.strokeOval(lastX, lastY, 1, 1);
        });

        // Draw a line on drag
        avatarCanvas.setOnMouseDragged(e -> {
            gc.strokeLine(lastX, lastY, e.getX(), e.getY());
            lastX = e.getX();
            lastY = e.getY();
        });
    }
    /**
     * Helper method to load audio resources
     */
    private void setupAudio() {
        try {
            // 1. Load Background Music
            URL musicUrl = getClass().getResource("lobby_music.mp3");
            if (musicUrl != null) {
                Media media = new Media(musicUrl.toExternalForm());
                backgroundMusicPlayer = new MediaPlayer(media);
                backgroundMusicPlayer.setCycleCount(MediaPlayer.INDEFINITE); // Loop forever
                backgroundMusicPlayer.setVolume(0.5); // 50% volume
                backgroundMusicPlayer.play();
            } else {
                System.out.println("Warning: lobby_music.mp3 not found");
            }

            // 2. Load Join Sound Effect
            URL soundUrl = getClass().getResource("join.wav");
            if (soundUrl != null) {
                joinSound = new AudioClip(soundUrl.toExternalForm());
            } else {
                System.out.println("Warning: join.wav not found");
            }

        } catch (Exception e) {
            System.out.println("Error loading audio: " + e.getMessage());
        }
    }
    /**
     * Clears the avatar canvas when the Clear button is clicked.
     */
    @FXML
    public void onClearAvatarClicked() {
        if (avatarCanvas != null) {
            GraphicsContext gc = avatarCanvas.getGraphicsContext2D();
            // Clear the entire 120x120 area
            gc.clearRect(0, 0, avatarCanvas.getWidth(), avatarCanvas.getHeight());
        }
    }

    /**
     * Handles the "Join Game" button click.
     * Validates input, loads the game screen, passes connection info, and switches scenes.
     */
    @FXML
    public void onJoinClicked() {
        // Play the sound effect immediately
        if (joinSound != null) {
            joinSound.play();
        }

        try {

            String username = playerNameField.getText().trim();
            String serverIP = ipAddressField.getText().trim();

            // Basic Validation
            if (username.isEmpty()) {
                System.out.println("Please enter a name!");
                return;
            }
            if (serverIP.isEmpty()) {
                System.out.println("Please enter an IP address!");
                return;
            }


            // Take a snapshot of whatever the user drew
            WritableImage avatarImage = avatarCanvas.snapshot(null, null);

            // Stop the Lobby Music before switching
            if (backgroundMusicPlayer != null) {
                backgroundMusicPlayer.stop();
            }

            // 2. Load the Game (Canvas) FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Canvas.fxml"));
            Parent gameRoot = loader.load();

            // 3. Get the controller and pass the Connection Info
            CanvasController gameController = loader.getController();
            gameController.setConnectionInfo(username, serverIP, avatarImage);
            // 4. Switch the Scene
            Stage stage = (Stage) joinButton.getScene().getWindow();

            // Ensure the game disconnects from server when the window is closed
            stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
                @Override
                public void handle(WindowEvent event) {
                    gameController.disconnect();
                }
            });

            stage.setScene(new Scene(gameRoot));
            stage.setTitle("InkRush - Game: " + username);

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error loading game screen: " + e.getMessage());
        }
    }
}