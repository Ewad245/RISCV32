package cse311;

import cse311.kernel.NonContiguous.paging.ReplacementPolicy;
import cse311.kernel.contiguous.AllocationStrategy;
import cse311.kernel.contiguous.MemoryBlock;
import cse311.kernel.plugin.PluginLoader;
import cse311.kernel.process.ProgramInfo;
import cse311.kernel.process.Task;
import cse311.kernel.process.TaskState;
import cse311.kernel.scheduler.Scheduler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLoaderTest {

    private static File pluginJar;

    @BeforeAll
    static void setUp() {
        // Look for the compiled example-plugins JAR in standard build output directories
        File jar = new File("example-plugins/build/libs/example-plugins.jar");
        if (!jar.exists()) {
            jar = new File("../example-plugins/build/libs/example-plugins.jar");
        }
        pluginJar = jar;
    }

    @Test
    void testInspectJarValid() throws IOException {
        if (!pluginJar.exists()) {
            return; // Skip if test run without prior jar build
        }

        PluginLoader.PluginManifest manifest = PluginLoader.inspectJar(pluginJar);
        assertNotNull(manifest);
        assertFalse(manifest.isEmpty());
        assertTrue(manifest.hasScheduler(), "JAR should provide Scheduler");
        assertTrue(manifest.hasAllocator(), "JAR should provide AllocationStrategy");
        assertTrue(manifest.hasReplacementPolicy(), "JAR should provide ReplacementPolicy");
    }

    @Test
    void testLoadCustomAllocator() throws Exception {
        if (!pluginJar.exists()) {
            return;
        }

        AllocationStrategy allocator = PluginLoader.loadCustomAllocator(pluginJar);
        assertNotNull(allocator);

        List<MemoryBlock> holes = Collections.singletonList(new MemoryBlock(0x1000, 1024));
        int region = allocator.findRegion(holes, 512);
        assertEquals(0x1000, region);
    }

    @Test
    void testLoadCustomScheduler() throws Exception {
        if (!pluginJar.exists()) {
            return;
        }

        Scheduler scheduler = PluginLoader.loadCustomScheduler(pluginJar);
        assertNotNull(scheduler);

        Task t1 = new Task(1, 0, 1024, 0, new ProgramInfo(0, 0, 0, 0, 0, 0));
        t1.setPriority(5);
        t1.setState(TaskState.READY);

        scheduler.addTask(t1);
        assertEquals(1, scheduler.getReadyTaskCount());

        Task scheduled = scheduler.schedule();
        assertEquals(t1, scheduled);
    }

    @Test
    void testLoadCustomReplacementPolicy() throws Exception {
        if (!pluginJar.exists()) {
            return;
        }

        ReplacementPolicy policy = PluginLoader.loadCustomReplacementPolicy(pluginJar);
        assertNotNull(policy);

        policy.onMap(3);
        policy.onMap(7);

        int victim = policy.pickVictim(f -> f == 3);
        assertEquals(3, victim);
    }

    @Test
    void testNoFileLockingOnOriginalJar() throws Exception {
        if (!pluginJar.exists()) {
            return;
        }

        // Copy example jar to a temporary test file
        File tempTestJar = File.createTempFile("test_lock_", ".jar");
        tempTestJar.deleteOnExit();
        Files.copy(pluginJar.toPath(), tempTestJar.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Load plugin from this file
        AllocationStrategy strategy = PluginLoader.loadCustomAllocator(tempTestJar);
        assertNotNull(strategy);

        // Verify the original temp file is NOT locked on Windows: we should be able to delete or overwrite it immediately
        boolean deleted = tempTestJar.delete();
        assertTrue(deleted, "The original JAR file must not be locked by PluginLoader!");
    }

    @Test
    void testInspectNonExistentJar() {
        File dummy = new File("non_existent_plugin_file.jar");
        assertThrows(IOException.class, () -> PluginLoader.inspectJar(dummy));
    }

    @Test
    void testLoadNonExistentJar() {
        File dummy = new File("non_existent_plugin_file.jar");
        assertThrows(IllegalArgumentException.class, () -> PluginLoader.loadCustomScheduler(dummy));
    }
}

