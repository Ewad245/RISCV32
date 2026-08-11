package cse311.gui.components;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.EnumMap;
import java.util.Map;

/**
 * Decoupled input mapper that translates JavaFX KeyEvents into DOOM (doomgeneric) key constants.
 * Supports standard controls, function keys (F1-F12), backspace, and printable ASCII characters.
 */
public class DoomInputMapper {

    // DOOM Keyboard Constants (matching doomkeys.h)
    public static final int KEY_RIGHTARROW = 0xae;
    public static final int KEY_LEFTARROW  = 0xac;
    public static final int KEY_UPARROW    = 0xad;
    public static final int KEY_DOWNARROW  = 0xaf;
    public static final int KEY_USE        = 0xa2;
    public static final int KEY_FIRE       = 0xa3;
    public static final int KEY_ESCAPE     = 27;
    public static final int KEY_ENTER      = 13;
    public static final int KEY_TAB        = 9;
    public static final int KEY_BACKSPACE  = 0x7f;
    public static final int KEY_PAUSE      = 0xff;
    public static final int KEY_EQUALS     = 0x3d;
    public static final int KEY_MINUS      = 0x2d;

    public static final int KEY_RSHIFT     = 0x80 + 0x36;
    public static final int KEY_RCTRL      = 0x80 + 0x1d;
    public static final int KEY_RALT       = 0x80 + 0x38;
    public static final int KEY_LALT       = KEY_RALT;

    public static final int KEY_F1         = 0x80 + 0x3b;
    public static final int KEY_F2         = 0x80 + 0x3c;
    public static final int KEY_F3         = 0x80 + 0x3d;
    public static final int KEY_F4         = 0x80 + 0x3e;
    public static final int KEY_F5         = 0x80 + 0x3f;
    public static final int KEY_F6         = 0x80 + 0x40;
    public static final int KEY_F7         = 0x80 + 0x41;
    public static final int KEY_F8         = 0x80 + 0x42;
    public static final int KEY_F9         = 0x80 + 0x43;
    public static final int KEY_F10        = 0x80 + 0x44;
    public static final int KEY_F11        = 0x80 + 0x57;
    public static final int KEY_F12        = 0x80 + 0x58;

    private final Map<KeyCode, Integer> keyBindings = new EnumMap<>(KeyCode.class);

    public DoomInputMapper() {
        loadDefaultBindings();
    }

    /**
     * Initializes default key mapping from JavaFX KeyCode to DOOM key constants.
     */
    public final void loadDefaultBindings() {
        keyBindings.clear();

        // Standard Navigation & Control
        keyBindings.put(KeyCode.ENTER, KEY_ENTER);
        keyBindings.put(KeyCode.ESCAPE, KEY_ESCAPE);
        keyBindings.put(KeyCode.TAB, KEY_TAB);
        keyBindings.put(KeyCode.BACK_SPACE, KEY_BACKSPACE);
        keyBindings.put(KeyCode.DELETE, KEY_BACKSPACE);

        keyBindings.put(KeyCode.LEFT, KEY_LEFTARROW);
        keyBindings.put(KeyCode.RIGHT, KEY_RIGHTARROW);
        keyBindings.put(KeyCode.UP, KEY_UPARROW);
        keyBindings.put(KeyCode.DOWN, KEY_DOWNARROW);

        keyBindings.put(KeyCode.CONTROL, KEY_FIRE);
        keyBindings.put(KeyCode.SPACE, KEY_USE);
        keyBindings.put(KeyCode.SHIFT, KEY_RSHIFT);
        keyBindings.put(KeyCode.ALT, KEY_RALT);

        keyBindings.put(KeyCode.EQUALS, KEY_EQUALS);
        keyBindings.put(KeyCode.MINUS, KEY_MINUS);

        // Function keys F1 - F12
        keyBindings.put(KeyCode.F1, KEY_F1);
        keyBindings.put(KeyCode.F2, KEY_F2);
        keyBindings.put(KeyCode.F3, KEY_F3);
        keyBindings.put(KeyCode.F4, KEY_F4);
        keyBindings.put(KeyCode.F5, KEY_F5);
        keyBindings.put(KeyCode.F6, KEY_F6);
        keyBindings.put(KeyCode.F7, KEY_F7);
        keyBindings.put(KeyCode.F8, KEY_F8);
        keyBindings.put(KeyCode.F9, KEY_F9);
        keyBindings.put(KeyCode.F10, KEY_F10);
        keyBindings.put(KeyCode.F11, KEY_F11);
        keyBindings.put(KeyCode.F12, KEY_F12);
    }

    /**
     * Maps a JavaFX KeyEvent to a DOOM key code.
     *
     * @param event JavaFX key event
     * @return DOOM key code integer, or 0 if unmapped
     */
    public int mapEventToDoomKey(KeyEvent event) {
        KeyCode code = event.getCode();

        // 1. Check explicitly mapped special keys
        Integer mappedKey = keyBindings.get(code);
        if (mappedKey != null) {
            return mappedKey;
        }

        // 2. Extract character signal for printable letters, numbers, and symbols
        String text = event.getText();
        if (text != null && !text.isEmpty()) {
            char c = text.charAt(0);
            return (int) Character.toLowerCase(c);
        }

        // 3. Fallback to Character of KeyCode if event.getText() is empty (e.g. key release)
        String codeChar = code.getChar();
        if (codeChar != null && !codeChar.isEmpty() && !"Undefined".equalsIgnoreCase(codeChar)) {
            char c = codeChar.charAt(0);
            return (int) Character.toLowerCase(c);
        }

        return 0;
    }

    /**
     * Rebinds a JavaFX KeyCode to a custom DOOM key code integer.
     */
    public void rebind(KeyCode code, int doomKey) {
        keyBindings.put(code, doomKey);
    }

    /**
     * Returns the underlying key bindings map.
     */
    public Map<KeyCode, Integer> getKeyBindings() {
        return keyBindings;
    }
}
