package mesh.core.manifest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * The structured identity of an AI agent, stored across ENS text records.
 *
 * ENS text record keys:
 *   com.agentmesh.capabilities  → JSON array of strings
 *   com.agentmesh.endpoint      → HTTPS URL of the agent's /invoke endpoint
 *   com.agentmesh.pubkey        → Ed25519 public key (base64)
 *   com.agentmesh.model         → e.g. "claude-sonnet-4-20250514"
 *   com.agentmesh.version       → semver e.g. "1.0.0"
 *   com.agentmesh.description   → plain text description
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentManifest(
        String ensName,
        String address,
        List<String> capabilities,
        String endpoint,
        String pubkey,
        String model,
        String version,
        String description
) {
    // Text record key constants — single source of truth
    public static final String KEY_CAPABILITIES = "com.agentmesh.capabilities";
    public static final String KEY_ENDPOINT      = "com.agentmesh.endpoint";
    public static final String KEY_PUBKEY        = "com.agentmesh.pubkey";
    public static final String KEY_MODEL         = "com.agentmesh.model";
    public static final String KEY_VERSION       = "com.agentmesh.version";
    public static final String KEY_DESCRIPTION   = "com.agentmesh.description";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Parse the capabilities JSON array from the raw text record value.
     * e.g. ["web-search", "summarise"] → List.of("web-search", "summarise")
     */
    public static List<String> parseCapabilities(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return List.of();
        try {
            return MAPPER.readValue(rawJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new ManifestParseException("Invalid capabilities JSON: " + rawJson, e);
        }
    }

    /**
     * Serialise capabilities list back to JSON for writing to ENS text record.
     */
    public static String serialiseCapabilities(List<String> capabilities) {
        try {
            return MAPPER.writeValueAsString(capabilities);
        } catch (Exception e) {
            throw new ManifestParseException("Failed to serialise capabilities", e);
        }
    }

    /**
     * True if this agent declares the given capability.
     */
    public boolean hasCapability(String capability) {
        return capabilities != null && capabilities.contains(capability);
    }

    /**
     * Builder — used by EnsResolver to construct manifests from raw text records.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String ensName;
        private String address;
        private List<String> capabilities = List.of();
        private String endpoint;
        private String pubkey;
        private String model;
        private String version;
        private String description;

        public Builder ensName(String ensName)             { this.ensName = ensName; return this; }
        public Builder address(String address)             { this.address = address; return this; }
        public Builder capabilities(List<String> caps)     { this.capabilities = caps; return this; }
        public Builder capabilitiesJson(String json)       { this.capabilities = parseCapabilities(json); return this; }
        public Builder endpoint(String endpoint)           { this.endpoint = endpoint; return this; }
        public Builder pubkey(String pubkey)               { this.pubkey = pubkey; return this; }
        public Builder model(String model)                 { this.model = model; return this; }
        public Builder version(String version)             { this.version = version; return this; }
        public Builder description(String description)     { this.description = description; return this; }

        public AgentManifest build() {
            return new AgentManifest(ensName, address, capabilities, endpoint, pubkey, model, version, description);
        }
    }
}
