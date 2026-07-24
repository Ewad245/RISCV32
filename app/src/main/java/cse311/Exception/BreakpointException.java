package cse311.Exception;

@SuppressWarnings("PMD.MissingSerialVersionUID")
public class BreakpointException extends RuntimeException {
    public BreakpointException(String message) {
        super(message);
    }
}
