package cse311.kernel.plugin;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

import cse311.Logger.FileLogger;
import cse311.Logger.FileLogger.LogLevel;
import cse311.kernel.NonContiguous.paging.ReplacementPolicy;
import cse311.kernel.contiguous.AllocationStrategy;
import cse311.kernel.scheduler.Scheduler;

@SuppressWarnings("PMD.UseProperClassLoader")
public class PluginLoader {

    public static Scheduler loadCustomScheduler(File jarFile, String className) throws Exception {
        URL[] urls = { jarFile.toURI().toURL() };

        try (URLClassLoader loader = new URLClassLoader(urls, PluginLoader.class.getClassLoader())) {

            Class<?> loadedClass = Class.forName(className, true, loader);

            if (Scheduler.class.isAssignableFrom(loadedClass)) {
                try {
                    return (Scheduler) loadedClass.getDeclaredConstructor(int.class).newInstance(3);
                } catch (NoSuchMethodException e) {
                    FileLogger.log(LogLevel.DEBUG,
                            "PluginLoader: No int constructor found, trying no-arg constructor.");
                    return (Scheduler) loadedClass.getDeclaredConstructor().newInstance();
                }
            } else {
                throw new IllegalArgumentException(
                        "The provided class does not extend cse311.kernel.scheduler.Scheduler");
            }
        }
    }

    public static AllocationStrategy loadCustomAllocator(File jarFile, String className) throws Exception {
        URL[] urls = { jarFile.toURI().toURL() };

        try (URLClassLoader loader = new URLClassLoader(urls, PluginLoader.class.getClassLoader())) {
            Class<?> loadedClass = Class.forName(className, true, loader);

            if (AllocationStrategy.class.isAssignableFrom(loadedClass)) {
                return (AllocationStrategy) loadedClass.getDeclaredConstructor().newInstance();
            } else {
                throw new IllegalArgumentException("Class does not implement cse311.kernel.contiguous.AllocationStrategy");
            }
        }
    }

    public static ReplacementPolicy loadCustomReplacementPolicy(File jarFile, String className) throws Exception {
        URL[] urls = { jarFile.toURI().toURL() };

        try (URLClassLoader loader = new URLClassLoader(urls, PluginLoader.class.getClassLoader())) {
            Class<?> loadedClass = Class.forName(className, true, loader);

            if (ReplacementPolicy.class.isAssignableFrom(loadedClass)) {
                return (ReplacementPolicy) loadedClass.getDeclaredConstructor().newInstance();
            } else {
                throw new IllegalArgumentException(
                        "Class does not implement cse311.kernel.NonContiguous.paging.ReplacementPolicy");
            }
        }
    }
}
