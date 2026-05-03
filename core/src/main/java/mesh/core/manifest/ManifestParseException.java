package mesh.core.manifest;

public class ManifestParseException extends RuntimeException {
    public ManifestParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public ManifestParseException(String message) {
        super(message);
    }
}
