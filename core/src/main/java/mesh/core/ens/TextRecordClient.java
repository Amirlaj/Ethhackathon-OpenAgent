package mesh.core.ens;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.logging.Logger;

public class TextRecordClient {

    private static final Logger log = Logger.getLogger(TextRecordClient.class.getName());

    // ENS Registry — same address on all networks
    private static final String ENS_REGISTRY = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e";

    // ENS Public Resolver on Sepolia
    private static final String ENS_RESOLVER_SEPOLIA = "0x8FADE66B79cC9f707aB26799354482EB93a5B7dD";

    // Namestone API base URL
    private static final String NAMESTONE_API = "https://namestone.xyz/api/public_v1";

    // Function selector for: text(bytes32 node, string key) → string
    private static final String TEXT_SELECTOR = "0x59d1d43c";

    private final String rpcUrl;
    private final String namestoneApiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    public TextRecordClient(String rpcUrl) {
        this(rpcUrl, System.getenv("NAMESTONE_API_KEY"));
    }

    public TextRecordClient(String rpcUrl, String namestoneApiKey) {
        this.rpcUrl = rpcUrl;
        this.namestoneApiKey = namestoneApiKey;
        this.httpClient = new OkHttpClient();
        this.mapper = new ObjectMapper();
    }

    /**
     * Read a text record for the given ENS name and key.
     * Looks up the resolver via ENS registry first, then calls text() on it.
     */
    public String getText(String ensName, String key) throws IOException {
        log.fine("[ENS] getText(" + ensName + ", " + key + ")");
        String resolverAddress = getResolver(ensName);
        String namehash = EnsNamehash.namehash(ensName);
        String calldata = encodeTextCall(namehash, key);

        String jsonBody = """
                {
                  "jsonrpc": "2.0",
                  "method": "eth_call",
                  "params": [{"to": "%s", "data": "%s"}, "latest"],
                  "id": 1
                }
                """.formatted(resolverAddress, calldata);

        Request request = new Request.Builder()
                .url(rpcUrl)
                .post(RequestBody.create(jsonBody, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("RPC call failed: " + response.code());
            }
            JsonNode json = mapper.readTree(response.body().string());
            return decodeAbiString(json.path("result").asText(""));
        }
    }

    /**
     * Resolve an ENS name to its ETH address.
     * Step 1: get resolver from ENS registry.
     * Step 2: call addr(bytes32) on that resolver.
     */
    public String resolveAddressViaEthCall(String ensName) throws IOException {
        String namehash = EnsNamehash.namehash(ensName);
        String nodeHex = namehash.substring(2);

        // Step 1: get resolver address from registry
        String resolverAddress = getResolver(ensName);
        log.info("[ENS] Resolver for " + ensName + ": " + resolverAddress);

        // Step 2: call addr(bytes32) on the resolver
        String addrCalldata = "0x3b3b57de" + nodeHex;
        String addrJsonBody = """
                {
                  "jsonrpc": "2.0",
                  "method": "eth_call",
                  "params": [{"to": "%s", "data": "%s"}, "latest"],
                  "id": 2
                }
                """.formatted(resolverAddress, addrCalldata);

        Request addrRequest = new Request.Builder()
                .url(rpcUrl)
                .post(RequestBody.create(addrJsonBody, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(addrRequest).execute()) {
            JsonNode json = mapper.readTree(response.body().string());
            String result = json.path("result").asText("");
            if (result.equals("0x") || result.length() < 42) {
                throw new IOException("No address found for: " + ensName);
            }
            return "0x" + result.substring(result.length() - 40);
        }
    }

    /**
     * Look up the resolver for an ENS name via the registry.
     * resolver(bytes32 node) selector = 0x0178b8bf
     */
    private String getResolver(String ensName) throws IOException {
        String namehash = EnsNamehash.namehash(ensName);
        String nodeHex = namehash.substring(2);
        String calldata = "0x0178b8bf" + nodeHex;

        String jsonBody = """
                {
                  "jsonrpc": "2.0",
                  "method": "eth_call",
                  "params": [{"to": "%s", "data": "%s"}, "latest"],
                  "id": 1
                }
                """.formatted(ENS_REGISTRY, calldata);

        Request request = new Request.Builder()
                .url(rpcUrl)
                .post(RequestBody.create(jsonBody, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            JsonNode json = mapper.readTree(response.body().string());
            String result = json.path("result").asText("");
            if (result.equals("0x") || result.length() < 42) {
                throw new IOException("No resolver found for: " + ensName);
            }
            String resolverAddr = "0x" + result.substring(result.length() - 40);
            // If resolver is zero address, fall back to known Sepolia resolver
            if (resolverAddr.equals("0x0000000000000000000000000000000000000000")) {
                log.warning("[ENS] Zero resolver from registry, falling back to Sepolia public resolver");
                return ENS_RESOLVER_SEPOLIA;
            }
            return resolverAddr;
        }
    }

    /**
     * Set a text record via Namestone API.
     */
    public void setText(String ensName, String key, String value, String privateKey) throws IOException {
        if (namestoneApiKey == null || namestoneApiKey.isBlank()) {
            throw new IllegalStateException("NAMESTONE_API_KEY env var not set");
        }

        String jsonBody = """
                {
                  "domain": "%s",
                  "key": "%s",
                  "value": "%s"
                }
                """.formatted(ensName, key, value);

        Request request = new Request.Builder()
                .url(NAMESTONE_API + "/set-text-record")
                .addHeader("Authorization", "Bearer " + namestoneApiKey)
                .post(RequestBody.create(jsonBody, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Namestone setText failed for key=" + key + ": "
                        + response.code() + " " + response.body().string());
            }
            log.info("[Namestone] Set " + key + " on " + ensName);
        }
    }

    /**
     * Register a new subname via Namestone.
     */
    public void registerSubname(String label, String parentDomain, String ownerAddress) throws IOException {
        if (namestoneApiKey == null || namestoneApiKey.isBlank()) {
            throw new IllegalStateException("NAMESTONE_API_KEY env var not set");
        }

        String jsonBody = """
                {
                  "domain": "%s.%s",
                  "address": "%s"
                }
                """.formatted(label, parentDomain, ownerAddress);

        Request request = new Request.Builder()
                .url(NAMESTONE_API + "/claim-name")
                .addHeader("Authorization", "Bearer " + namestoneApiKey)
                .post(RequestBody.create(jsonBody, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Namestone registerSubname failed: "
                        + response.code() + " " + response.body().string());
            }
            log.info("[Namestone] Registered " + label + "." + parentDomain + " → " + ownerAddress);
        }
    }

    // --- ABI encoding helpers ---

    private String encodeTextCall(String namehash, String key) {
        String nodeHex = namehash.startsWith("0x") ? namehash.substring(2) : namehash;
        byte[] keyBytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String keyLen = leftPad(Integer.toHexString(keyBytes.length), 64);
        String keyData = rightPad(bytesToHex(keyBytes), roundUpTo32(keyBytes.length) * 2);
        String offset = leftPad("40", 64);
        return TEXT_SELECTOR + nodeHex + offset + keyLen + keyData;
    }

    private String decodeAbiString(String hex) {
        if (hex == null || hex.equals("0x") || hex.isBlank()) return "";
        hex = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (hex.length() < 128) return "";
        int offsetBytes = Integer.parseInt(hex.substring(0, 64), 16);
        int lengthStart = offsetBytes * 2;
        if (lengthStart + 64 > hex.length()) return "";
        int strLen = Integer.parseInt(hex.substring(lengthStart, lengthStart + 64), 16);
        int dataStart = lengthStart + 64;
        if (dataStart + strLen * 2 > hex.length()) return "";
        String strHex = hex.substring(dataStart, dataStart + strLen * 2);
        byte[] bytes = hexToBytes(strHex);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String leftPad(String s, int length) {
        return "0".repeat(Math.max(0, length - s.length())) + s;
    }

    private String rightPad(String s, int length) {
        return s + "0".repeat(Math.max(0, length - s.length()));
    }

    private int roundUpTo32(int n) {
        return ((n + 31) / 32) * 32;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}