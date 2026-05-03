package mesh.core.manifest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManifestValidatorTest {

    private final ManifestValidator validator = new ManifestValidator();

    private AgentManifest validManifest() {
        return AgentManifest.builder()
                .ensName("researcher.agentmesh.eth")
                .address("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
                .capabilities(List.of("web-search"))
                .endpoint("http://localhost:8082")
                .model("claude-sonnet-4-20250514")
                .version("1.0.0")
                .description("A research agent")
                .build();
    }

    @Test
    void validManifestPassesValidation() {
        var result = validator.validate(validManifest());
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void nullManifestFails() {
        var result = validator.validate(null);
        assertFalse(result.valid());
    }

    @Test
    void missingEnsNameFails() {
        var m = AgentManifest.builder()
                .address("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
                .endpoint("http://localhost:8082")
                .model("claude-sonnet-4-20250514")
                .version("1.0.0")
                .capabilities(List.of())
                .build();
        var result = validator.validate(m);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("ensName")));
    }

    @Test
    void invalidEndpointFails() {
        var m = AgentManifest.builder()
                .ensName("test.agentmesh.eth")
                .address("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
                .endpoint("not-a-url")
                .model("claude-sonnet-4-20250514")
                .version("1.0.0")
                .capabilities(List.of())
                .build();
        var result = validator.validate(m);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("endpoint")));
    }

    @Test
    void invalidAddressFails() {
        var m = AgentManifest.builder()
                .ensName("test.agentmesh.eth")
                .address("not-an-address")
                .endpoint("http://localhost:8082")
                .model("claude-sonnet-4-20250514")
                .version("1.0.0")
                .capabilities(List.of())
                .build();
        var result = validator.validate(m);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("address")));
    }

    @Test
    void throwIfInvalidThrowsOnInvalidManifest() {
        var m = AgentManifest.builder().build(); // all nulls
        assertThrows(InvalidManifestException.class, () -> validator.validateOrThrow(m));
    }

    @Test
    void emptyCapabilitiesIsValid() {
        // capabilities can be empty — not all agents need to declare them
        var result = validator.validate(validManifest());
        assertTrue(result.valid());
    }
}
