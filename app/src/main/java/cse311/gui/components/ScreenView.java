package cse311.gui.components;

import cse311.FramebufferDevice;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;

import java.nio.IntBuffer;

/**
 * High-Performance JavaFX Screen View for DOOM (doomgeneric) and Graphical Display.
 * Uses PixelBuffer zero-copy hardware texture updates and GPU nearest-neighbor upscaling.
 */
public class ScreenView extends StackPane {

    private static final long FPS_UPDATE_INTERVAL_NS = 500_000_000L;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final FramebufferDevice framebufferDevice;
    private final DoomInputMapper inputMapper;
    private final PixelBuffer<IntBuffer> pixelBuffer;
    private final ImageView imageView;
    private final Label fpsLabel;
    private AnimationTimer renderTimer;

    private long lastFpsTime = 0;
    private int frameCount = 0;
    private boolean showFps = true;

    public ScreenView(FramebufferDevice framebufferDevice) {
        this(framebufferDevice, new DoomInputMapper());
    }

    public ScreenView(FramebufferDevice framebufferDevice, DoomInputMapper inputMapper) {
        this.framebufferDevice = framebufferDevice;
        this.inputMapper = inputMapper;

        // 1. Wrap off-heap IntBuffer directly in JavaFX PixelBuffer
        IntBuffer intBuffer = framebufferDevice.getDirectIntBuffer();
        PixelFormat<IntBuffer> pixelFormat = PixelFormat.getIntArgbPreInstance();
        this.pixelBuffer = new PixelBuffer<>(
                FramebufferDevice.WIDTH,
                FramebufferDevice.HEIGHT,
                intBuffer,
                pixelFormat
        );

        // 2. Configure ImageView for GPU nearest-neighbor crisp pixel scaling
        WritableImage writableImage = new WritableImage(pixelBuffer);
        this.imageView = new ImageView(writableImage);
        this.imageView.setSmooth(false); // Sharp retro pixels (no bilinear blurring)
        this.imageView.setPreserveRatio(true); // Preserve retro DOOM aspect ratio

        // 3. Bind ImageView scaling to container size
        this.imageView.fitWidthProperty().bind(widthProperty());
        this.imageView.fitHeightProperty().bind(heightProperty());

        // 4. Create retro HUD FPS Label
        this.fpsLabel = new Label("FPS: --");
        this.fpsLabel.setStyle(
                "-fx-text-fill: #00ff66; " +
                "-fx-font-family: 'Consolas', 'Courier New', monospace; " +
                "-fx-font-weight: bold; " +
                "-fx-font-size: 13px; " +
                "-fx-background-color: rgba(0, 0, 0, 0.7); " +
                "-fx-border-color: rgba(0, 255, 102, 0.4); " +
                "-fx-border-radius: 4px; " +
                "-fx-background-radius: 4px; " +
                "-fx-padding: 3px 8px;"
        );
        StackPane.setAlignment(this.fpsLabel, Pos.TOP_RIGHT);
        StackPane.setMargin(this.fpsLabel, new Insets(10, 10, 0, 0));
        this.fpsLabel.setMouseTransparent(true); // Don't capture mouse events

        getChildren().addAll(imageView, fpsLabel);

        // Styling for dark gaming frame
        setMinSize(0, 0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setStyle("-fx-background-color: #0d0d0d; -fx-alignment: center;");
        setFocusTraversable(true); // Allow focus to capture keyboard inputs

        // 5. Setup Key Event Handlers for DOOM Controls & Shortcuts
        setupInputHandlers();

        // 6. Start Frame Refresh Loop
        startRenderTimer();
    }

    private void setupInputHandlers() {
        setOnMouseClicked(event -> requestFocus());

        setOnKeyPressed(event -> handleKeyEvent(event, true));
        setOnKeyReleased(event -> handleKeyEvent(event, false));
    }

    private void handleKeyEvent(KeyEvent event, boolean pressed) {
        if (pressed && event.getCode() == KeyCode.F3 && event.isControlDown()) {
            // Ctrl+F3 toggles FPS HUD overlay (allowing plain F3 to pass to DOOM for Load Menu)
            showFps = !showFps;
            fpsLabel.setVisible(showFps);
            event.consume();
            return;
        }

        int doomKey = inputMapper.mapEventToDoomKey(event);
        if (doomKey != 0) {
            framebufferDevice.pushKeyEvent(doomKey, pressed);
            event.consume();
        }
    }

    private final javafx.geometry.Rectangle2D fullDirtyRegion =
            new javafx.geometry.Rectangle2D(0, 0, FramebufferDevice.WIDTH, FramebufferDevice.HEIGHT);

    private void startRenderTimer() {
        this.renderTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (framebufferDevice.getFrameReadyFlag().compareAndSet(true, false)) {
                    pixelBuffer.updateBuffer(pb -> fullDirtyRegion);
                    frameCount++;
                }

                if (lastFpsTime == 0) {
                    lastFpsTime = now;
                } else if (now - lastFpsTime >= FPS_UPDATE_INTERVAL_NS) { // Recalculate FPS every 500ms
                    long elapsed = now - lastFpsTime;
                    double fps = (frameCount * NANOS_PER_SECOND) / elapsed;
                    if (showFps) {
                        fpsLabel.setText(String.format("FPS: %.1f", fps));
                    }
                    frameCount = 0;
                    lastFpsTime = now;
                }
            }
        };
        this.renderTimer.start();
    }

    public void stop() {
        if (renderTimer != null) {
            renderTimer.stop();
        }
    }
}
