import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameOverController {

    @FXML private Canvas confettiCanvas;

    @FXML private Label winnerLabel;

    @FXML private Label place1Icon;
    @FXML private Label place1Name;
    @FXML private Label place1Score;

    @FXML private Label place2Icon;
    @FXML private Label place2Name;
    @FXML private Label place2Score;

    @FXML private Label place3Icon;
    @FXML private Label place3Name;
    @FXML private Label place3Score;

    @FXML private Label countdownLabel;
    @FXML private Button restartButton;
    @FXML private Button closeButton;

    private int countdown = 10;
    private List<Confetti> confettiList = new ArrayList<>();
    private AnimationTimer confettiTimer;
    private Random random = new Random();

    // Store canvas dimensions for performance
    private double width;
    private double height;

    private class Confetti {
        double x, y;
        double vx, vy;
        Color color;
        double size;
        double rotation;
        double rotationSpeed;

        Confetti(double startX, double startY) {
            this.x = startX;
            this.y = startY;
            // Spread X velocity slightly more
            this.vx = (random.nextDouble() - 0.5) * 6;
            this.vy = random.nextDouble() * 3 + 2;
            this.size = random.nextDouble() * 8 + 4;
            this.rotation = random.nextDouble() * 360;
            this.rotationSpeed = (random.nextDouble() - 0.5) * 10;

            Color[] colors = {
                Color.web("#ffb84d"), Color.web("#1E90FF"), Color.web("#FFD700"),
                Color.web("#FF69B4"), Color.web("#00FA9A"), Color.web("#FF6347")
            };
            this.color = colors[random.nextInt(colors.length)];
        }

        void update() {
            x += vx;
            y += vy;
            rotation += rotationSpeed;
            vy += 0.1; // Slight gravity
        }

        // Dynamic check based on current screen size
        boolean isOffScreen() {
            return y > height || x < -50 || x > width + 50;
        }
    }

    @FXML
    public void initialize() {
        // 1. Bind Canvas to the parent container so it resizes with the window
        // Note: The parent must be the StackPane in the FXML
        if (confettiCanvas.getParent() instanceof javafx.scene.layout.Region) {
            javafx.scene.layout.Region parent = (javafx.scene.layout.Region) confettiCanvas.getParent();
            confettiCanvas.widthProperty().bind(parent.widthProperty());
            confettiCanvas.heightProperty().bind(parent.heightProperty());
        }

        // 2. Initialize dimensions
        width = confettiCanvas.getWidth();
        height = confettiCanvas.getHeight();

        // 3. Update dimensions if they change
        confettiCanvas.widthProperty().addListener((obs, oldVal, newVal) -> width = newVal.doubleValue());
        confettiCanvas.heightProperty().addListener((obs, oldVal, newVal) -> height = newVal.doubleValue());

        startConfetti();
    }

    private void startConfetti() {
        GraphicsContext gc = confettiCanvas.getGraphicsContext2D();

        // Initial burst
        for (int i = 0; i < 100; i++) {
            // Use 'width' instead of 500
            confettiList.add(new Confetti(random.nextDouble() * width, -random.nextDouble() * 200));
        }

        confettiTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // Clear the ENTIRE canvas, not just 500x600
                gc.clearRect(0, 0, width, height);

                for (int i = confettiList.size() - 1; i >= 0; i--) {
                    Confetti c = confettiList.get(i);
                    c.update();

                    if (c.isOffScreen()) {
                        confettiList.remove(i);
                        // Respawn at random X within current WIDTH
                        confettiList.add(new Confetti(random.nextDouble() * width, -20));
                    } else {
                        gc.save();
                        gc.translate(c.x, c.y);
                        gc.rotate(c.rotation);
                        gc.setFill(c.color);
                        gc.fillRect(-c.size/2, -c.size/2, c.size, c.size);
                        gc.restore();
                    }
                }
            }
        };

        confettiTimer.start();
    }

    public void setLeaderboardData(String leaderboardData) {
        try {
            // Clean up the input just in case
            if (leaderboardData == null) return;

            String[] parts = leaderboardData.split(":");

            // Winner
            if (parts.length >= 2) {
                // Update specific labels
                winnerLabel.setText(parts[0]); // Just name, "WINNER" is static in FXML
                place1Name.setText(parts[0]);
                place1Score.setText(parts[1]);
            }

            // 2nd
            if (parts.length >= 4) {
                place2Name.setText(parts[2]);
                place2Score.setText(parts[3]);
            } else {
                place2Name.setText("---");
                place2Score.setText("---");
            }

            // 3rd
            if (parts.length >= 6) {
                place3Name.setText(parts[4]);
                place3Score.setText(parts[5]);
            } else {
                place3Name.setText("---");
                place3Score.setText("---");
            }

        } catch (Exception e) {
            System.err.println("Leaderboard Error: " + e.getMessage());
        }
    }

    // ... Rest of your restart/close logic remains the same ...

    public void startCountdown() {
        new Thread(() -> {
            while (countdown > 0) {
                final int current = countdown;
                Platform.runLater(() -> countdownLabel.setText("Returning to game in " + current + "s..."));
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                countdown--;
            }
            Platform.runLater(this::closeGame);
        }).start();
    }

    @FXML
    private void onRestartClicked() {
        if (confettiTimer != null) confettiTimer.stop();
        // Send logic...
        closeGame();
    }

    @FXML
    private void onCloseClicked() {
        if (confettiTimer != null) confettiTimer.stop();
        Platform.runLater(() -> {
            Stage stage = (Stage) closeButton.getScene().getWindow();
            stage.close();
            Platform.exit();
            System.exit(0);
        });
    }

    private void closeGame() {
        if (confettiTimer != null) confettiTimer.stop();
        Platform.runLater(() -> {
            Stage stage = (Stage) closeButton.getScene().getWindow();
            if(stage != null) stage.close();
        });
    }
}