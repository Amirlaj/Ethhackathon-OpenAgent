package mesh.core.manifest;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates an AgentManifest before it can be used for invocation.
 * Always call validate() before invoking or delegating to an agent.
 */
public class ManifestValidator {

    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult fail(List<String> errors) {
            return new ValidationResult(false, errors);
        }

        public void throwIfInvalid() {
            if (!valid) {
                throw new InvalidManifestException("Manifest validation failed: " + String.join(", ", errors));
            }
        }
    }

    public ValidationResult validate(AgentManifest manifest) {
        List<String> errors = new ArrayList<>();

        if (manifest == null) {
            return ValidationResult.fail(List.of("Manifest is null"));
        }

        // Required fields
        if (isBlank(manifest.ensName()))    errors.add("ensName is required");
        if (isBlank(manifest.address()))    errors.add("address is required");
        if (isBlank(manifest.endpoint()))   errors.add("endpoint is required");
        if (isBlank(manifest.model()))      errors.add("model is required");
        if (isBlank(manifest.version()))    errors.add("version is required");

        // capabilities must not be null (empty list is fine)
        if (manifest.capabilities() == null) errors.add("capabilities must not be null");

        // endpoint must be a valid URL
        if (!isBlank(manifest.endpoint())) {
            try {
                URI uri = new URI(manifest.endpoint());
                if (uri.getScheme() == null) {
                    errors.add("endpoint must include scheme (http:// or https://)");
                } else if (!uri.getScheme().equals("http") && !uri.getScheme().equals("https")) {
                    errors.add("endpoint scheme must be http or https, got: " + uri.getScheme());
                }
            } catch (Exception e) {
                errors.add("endpoint is not a valid URL: " + manifest.endpoint());
            }
        }

        // address should look like an Ethereum address
        if (!isBlank(manifest.address()) && !manifest.address().matches("0x[0-9a-fA-F]{40}")) {
            errors.add("address does not look like an Ethereum address: " + manifest.address());
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }

    /**
     * Validate and throw if invalid. Use before any invocation.
     */
    public void validateOrThrow(AgentManifest manifest) {
        validate(manifest).throwIfInvalid();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
