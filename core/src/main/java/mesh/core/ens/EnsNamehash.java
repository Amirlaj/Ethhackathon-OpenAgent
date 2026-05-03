package mesh.core.ens;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Pure Java implementation of the ENS namehash algorithm.
 * Spec: https://eips.ethereum.org/EIPS/eip-137
 *
 * namehash("researcher.agentmesh.eth") → bytes32 node used in resolver calls
 */
public class EnsNamehash {

    private EnsNamehash() {}

    /**
     * Compute the ENS namehash of a fully-qualified ENS name.
     * Returns a 0x-prefixed 32-byte hex string.
     */
    public static String namehash(String name) {
        byte[] node = new byte[32]; // starts as 0x000...000

        if (name == null || name.isBlank()) {
            return "0x" + bytesToHex(node);
        }

        String[] labels = name.split("\\.");

        // Process labels right-to-left
        for (int i = labels.length - 1; i >= 0; i--) {
            byte[] labelHash = keccak256(labels[i].getBytes(StandardCharsets.UTF_8));
            node = keccak256(concat(node, labelHash));
        }

        return "0x" + bytesToHex(node);
    }

    /**
     * Keccak-256 hash. Uses BouncyCastle if available, otherwise throws.
     * web3j bundles BouncyCastle so this will work in the full project.
     */
    public static byte[] keccak256(byte[] input) {
        try {
            // web3j's bundled Keccak
            return org.web3j.crypto.Hash.sha3(input);
        } catch (Exception e) {
            throw new RuntimeException("Keccak-256 failed", e);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
