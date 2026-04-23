package cse311.gui;

import cse311.RV32Cpu;
import cse311.kernel.Kernel;
import cse311.util.Signal;
import javafx.application.Platform;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ripes-style ProcessorHandler: coordinates UI updates from multiple CPU cores.
 *
 * Mirrors Ripes' ProcessorHandler which:
 * 1. Wraps per-CPU signals into a unified UI update stream
 * 2. Debounces high-frequency clock events into safe JavaFX frame updates
 * 3. Suppresses UI updates during continuous "run" mode (like Ripes'
 * setEnableSignals(false))
 *
 * Architecture mapping:
 * Ripes: ProcessorHandler (singleton, single CPU)
 * Here: ProcessorHandler (per-Kernel, multi-CPU SMP)
 *
 * Ripes uses QTimer + mutex for debouncing. We use AtomicBoolean +
 * Platform.runLater()
 * which achieves the same lock-free debounce in JavaFX.
 */
public class ProcessorHandler {

    private final Kernel kernel;
    private final Signal uiUpdateSignal = new Signal();

    private final AtomicBoolean updateScheduled = new AtomicBoolean(false);
    private volatile boolean runMode = false;

    public ProcessorHandler(Kernel kernel) {
        this.kernel = kernel;
    }

    /**
     * Bind to all CPU clock signals and route them through the debouncer.
     * Call this once during MainController.initialize().
     */
    public void bindCpuSignals() {
        int coreCount = kernel.getConfig().getCoreCount();
        for (int i = 0; i < coreCount; i++) {
            RV32Cpu cpu = kernel.getCpu(i);
            cpu.processorWasClocked.connect(this::onProcessorClocked);
            cpu.processorWasReset.connect(this::onProcessorReset);
        }
    }

    /**
     * Connect a UI refresh callback. Equivalent to connecting to
     * Ripes' procStateChangedNonRun signal.
     */
    public void onUiUpdate(Runnable callback) {
        uiUpdateSignal.connect(callback);
    }

    /**
     * Enter "run mode" — suppresses per-instruction UI updates.
     * Equivalent to Ripes' vsrtl_proc->setEnableSignals(false).
     */
    public void enterRunMode() {
        runMode = true;
    }

    /**
     * Exit "run mode" — re-enables per-instruction UI updates.
     * Equivalent to Ripes' vsrtl_proc->setEnableSignals(true).
     */
    public void exitRunMode() {
        runMode = false;
        triggerUiUpdate();
    }

    /**
     * Called when any CPU executes an instruction.
     * Runs on the CPU thread (not JavaFX).
     */
    private void onProcessorClocked() {
        if (runMode) {
            return;
        }
        triggerUiUpdate();
    }

    /**
     * Called when any CPU is reset.
     */
    private void onProcessorReset() {
        triggerUiUpdate();
    }

    /**
     * Debounced UI update trigger.
     *
     * Mirrors Ripes' _triggerProcStateChangeTimer():
     * - If an update is already queued, skip (debounce)
     * - Queue a single Platform.runLater() for the next JavaFX pulse
     * - When the pulse fires, allow the next clock to queue again
     */
    private void triggerUiUpdate() {
        if (updateScheduled.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                uiUpdateSignal.emit();
                updateScheduled.set(false);
            });
        }
    }
}
