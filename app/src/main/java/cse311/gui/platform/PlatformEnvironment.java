package cse311.gui.platform;

import com.jpro.webapi.WebAPI;

public class PlatformEnvironment {
    
    private static PlatformManager manager;
    
    public static PlatformManager getManager() {
        if (manager == null) {
            if (isWeb()) {
                manager = new WebPlatformManager();
            } else {
                manager = new DesktopPlatformManager();
            }
        }
        return manager;
    }
    
    private static boolean isWeb() {
        try {
            return WebAPI.isBrowser();
        } catch (LinkageError | IllegalStateException e) {
            return false;
        }
    }
}
