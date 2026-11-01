package cse311.kernel.plugin;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

import cse311.Logger.FileLogger;
import cse311.Logger.FileLogger.LogLevel;
import cse311.kernel.NonContiguous.paging.ReplacementPolicy;
import cse311.kernel.contiguous.AllocationStrategy;
import cse311.kernel.scheduler.Scheduler;

public final class PluginLoader {

    private static final Map<Class<?>, URLClassLoader> ACTIVE_LOADERS_BY_TYPE = new ConcurrentHashMap<>();

    private PluginLoader() {
        // Utility class
    }

    /**
     * Descriptor of extension points found in a plugin JAR file.
     */
    public static final class PluginManifest {
        private final boolean hasScheduler;
        private final boolean hasAllocator;
        private final boolean hasReplacementPolicy;

        public PluginManifest(boolean hasScheduler, boolean hasAllocator, boolean hasReplacementPolicy) {
            this.hasScheduler = hasScheduler;
            this.hasAllocator = hasAllocator;
            this.hasReplacementPolicy = hasReplacementPolicy;
        }

        public boolean hasScheduler() {
            return hasScheduler;
        }

        public boolean hasAllocator() {
            return hasAllocator;
        }

        public boolean hasReplacementPolicy() {
            return hasReplacementPolicy;
        }

        public boolean isEmpty() {
            return !hasScheduler && !hasAllocator && !hasReplacementPolicy;
        }
    }

    /**
     * Inspects a JAR file to check which simulator SPI services it declares.
     */
    public static PluginManifest inspectJar(File jarFile) throws IOException {
        if (jarFile == null || !jarFile.exists() || !jarFile.isFile()) {
            throw new IOException("JAR file does not exist: " + (jarFile == null ? "null" : jarFile.getPath()));
        }

        try (JarFile jar = new JarFile(jarFile)) {
            boolean hasScheduler = jar.getEntry("META-INF/services/" + Scheduler.class.getName()) != null;
            boolean hasAllocator = jar.getEntry("META-INF/services/" + AllocationStrategy.class.getName()) != null;
            boolean hasReplacementPolicy = jar.getEntry(
                    "META-INF/services/" + ReplacementPolicy.class.getName()) != null;
            return new PluginManifest(hasScheduler, hasAllocator, hasReplacementPolicy);
        }
    }

    /**
     * Copies a JAR to a temporary file so that the original file is never locked by URLClassLoader on Windows.
     */
    private static File createTempCopy(File originalJar) throws IOException {
        File tempFile = File.createTempFile("plugin_", "_" + originalJar.getName());
        tempFile.deleteOnExit();
        Files.copy(originalJar.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return tempFile;
    }

    /**
     * Loads all SPI implementations of the given service interface from a JAR.
     *
     * @param jarFile          the plugin JAR
     * @param serviceInterface the SPI interface or abstract class
     * @param <T>              the service type
     * @return non-empty list of implementations
     * @throws IllegalArgumentException if no implementation is found or instantiation fails
     * @throws Exception                if the JAR cannot be read
     */
    @SuppressWarnings("PMD.CloseResource")
    public static <T> List<T> loadPlugins(File jarFile, Class<T> serviceInterface) throws Exception {
        if (jarFile == null || !jarFile.exists()) {
            throw new IllegalArgumentException("Plugin JAR file does not exist: " + jarFile);
        }

        File isolatedJar = createTempCopy(jarFile);
        URL[] urls = { isolatedJar.toURI().toURL() };
        ClassLoader parentLoader = Thread.currentThread().getContextClassLoader();
        URLClassLoader loader = new URLClassLoader(urls, parentLoader);

        List<T> implementations = new ArrayList<>();
        try {
            ServiceLoader<T> serviceLoader = ServiceLoader.load(serviceInterface, loader);
            Iterator<T> iterator = serviceLoader.iterator();

            while (iterator.hasNext()) {
                T impl = iterator.next();
                implementations.add(impl);
                FileLogger.log(LogLevel.DEBUG, "PluginLoader: Discovered " + serviceInterface.getSimpleName()
                        + " implementation: " + impl.getClass().getName());
            }
        } catch (ServiceConfigurationError | LinkageError e) {
            closeLoaderSilently(loader);
            throw new IllegalArgumentException(
                    "Failed to load " + serviceInterface.getSimpleName() + " provider from "
                            + jarFile.getName() + ": " + e.getMessage(), e);
        } catch (Throwable t) {
            closeLoaderSilently(loader);
            throw new IllegalArgumentException(
                    "Unexpected error loading " + serviceInterface.getSimpleName() + " from "
                            + jarFile.getName() + ": " + t.getMessage(), t);
        }

        if (implementations.isEmpty()) {
            closeLoaderSilently(loader);
            throw new IllegalArgumentException(
                    "No implementation found for " + serviceInterface.getName()
                            + " in " + jarFile.getName()
                            + ". Ensure the JAR contains META-INF/services/" + serviceInterface.getName());
        }

        // Close and replace previous loader for this service type to prevent Metaspace leaks
        URLClassLoader previousLoader = ACTIVE_LOADERS_BY_TYPE.put(serviceInterface, loader);
        if (previousLoader != null) {
            closeLoaderSilently(previousLoader);
        }

        return implementations;
    }

    /**
     * Loads the first SPI implementation of the given service interface from a JAR.
     */
    public static <T> T loadPlugin(File jarFile, Class<T> serviceInterface) throws Exception {
        List<T> list = loadPlugins(jarFile, serviceInterface);
        return list.get(0);
    }

    public static Scheduler loadCustomScheduler(File jarFile) throws Exception {
        return loadPlugin(jarFile, Scheduler.class);
    }

    public static AllocationStrategy loadCustomAllocator(File jarFile) throws Exception {
        return loadPlugin(jarFile, AllocationStrategy.class);
    }

    public static ReplacementPolicy loadCustomReplacementPolicy(File jarFile) throws Exception {
        return loadPlugin(jarFile, ReplacementPolicy.class);
    }

    /**
     * Unloads any active plugin classloader for the given service interface.
     */
    public static void unloadPlugin(Class<?> serviceInterface) {
        URLClassLoader loader = ACTIVE_LOADERS_BY_TYPE.remove(serviceInterface);
        if (loader != null) {
            closeLoaderSilently(loader);
        }
    }

    /**
     * Unloads all active plugin classloaders.
     */
    public static void unloadAll() {
        for (Class<?> key : new ArrayList<>(ACTIVE_LOADERS_BY_TYPE.keySet())) {
            unloadPlugin(key);
        }
    }

    private static void closeLoaderSilently(URLClassLoader loader) {
        try {
            loader.close();
        } catch (IOException e) {
            FileLogger.log(LogLevel.ERROR, "PluginLoader: Failed to close class loader: " + e.getMessage());
        }
    }
}
