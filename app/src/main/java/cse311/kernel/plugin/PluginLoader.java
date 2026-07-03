package cse311.kernel.plugin;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ServiceLoader;

import cse311.Logger.FileLogger;
import cse311.Logger.FileLogger.LogLevel;
import cse311.kernel.NonContiguous.paging.ReplacementPolicy;
import cse311.kernel.contiguous.AllocationStrategy;
import cse311.kernel.scheduler.Scheduler;

@SuppressWarnings("PMD.UseProperClassLoader")
public final class PluginLoader {

    private PluginLoader() {
        // Utility class
    }

    /**
     * Loads the first SPI implementation of the given service interface from a JAR.
     *
     * @param jarFile          the plugin JAR
     * @param serviceInterface the SPI interface or abstract class
     * @param <T>              the service type
     * @return the first discovered implementation
     * @throws IllegalArgumentException if no implementation is found
     * @throws Exception                if the JAR cannot be read or the service cannot be instantiated
     */
    public static <T> T loadPlugin(File jarFile, Class<T> serviceInterface) throws Exception {
        URL[] urls = { jarFile.toURI().toURL() };

        try (URLClassLoader loader = new URLClassLoader(urls, PluginLoader.class.getClassLoader())) {
            ServiceLoader<T> serviceLoader = ServiceLoader.load(serviceInterface, loader);

            java.util.Iterator<T> iterator = serviceLoader.iterator();
            if (iterator.hasNext()) {
                T implementation = iterator.next();
                FileLogger.log(LogLevel.DEBUG,
                        "PluginLoader: Loaded " + serviceInterface.getSimpleName()
                                + " implementation: " + implementation.getClass().getName());
                return implementation;
            }
        }

        throw new IllegalArgumentException(
                "No implementation found for " + serviceInterface.getName()
                        + " in " + jarFile.getName()
                        + ". Ensure the JAR contains META-INF/services/" + serviceInterface.getName());
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
}
