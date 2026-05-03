package mesh.core.manifest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentManifestTest {

    @Test
    void builderCreatesManifestCorrectly() {
        var manifest = AgentManifest.builder()
                .ensName("researcher.agentmesh.eth")
                .address("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
                .capabilities(List.of("web-search", "summarise"))
                .endpoint("http://localhost:8082")
                .model("claude-sonnet-4-20250514")
                .version("1.0.0")
                .description("A research agent")
                .build();

        assertEquals("researcher.agentmesh.eth", manifest.ensName());
        assertEquals(2, manifest.capabilities().size());
        assertTrue(manifest.hasCapability("web-search"));
        assertFalse(manifest.hasCapability("code-execution"));
    }

    @Test
    void parsesCapabilitiesJsonCorrectly() {
        var caps = AgentManifest.parseCapabilities("[\"web-search\", \"summarise\"]");
        assertEquals(List.of("web-search", "summarise"), caps);
    }

    @Test
    void parseCapabilitiesReturnsEmptyListForBlank() {
        assertEquals(List.of(), AgentManifest.parseCapabilities(null));
        assertEquals(List.of(), AgentManifest.parseCapabilities(""));
        assertEquals(List.of(), AgentManifest.parseCapabilities("  "));
    }

    @Test
    void parseCapabilitiesThrowsOnInvalidJson() {
        assertThrows(ManifestParseException.class,
                () -> AgentManifest.parseCapabilities("not-json"));
    }

    @Test
    void serialisesCapabilitiesBackToJson() throws Exception {
        var json = AgentManifest.serialiseCapabilities(List.of("web-search", "summarise"));
        // Round-trip check
        var parsed = AgentManifest.parseCapabilities(json);
        assertEquals(List.of("web-search", "summarise"), parsed);
    }

    @Test
    void builderWithCapabilitiesJsonParsesCorrectly() {
        var manifest = AgentManifest.builder()
                .ensName("test.agentmesh.eth")
                .address("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
                .capabilitiesJson("[\"code-execution\"]")
                .endpoint("http://localhost:8083")
                .model("claude-sonnet-4-20250514")
                .version("1.0.0")
                .build();

        assertTrue(manifest.hasCapability("code-execution"));
    }
}
