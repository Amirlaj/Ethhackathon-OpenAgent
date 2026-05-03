package mesh.core.ens;

import mesh.core.manifest.AgentManifest;
import org.web3j.ens.EnsResolver;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.util.logging.Logger;

/**
 * Reads and writes AgentManifest data from ENS text records on Base Sepolia.
 *
 * Uses Namestone API for subname issuance (no need to own a parent .eth name).
 * Uses web3j EnsResolver for address resolution.
 * Uses direct RPC calls for text record reads (web3j doesn't expose getText natively).
 */
public class AgentEnsResolver {

    private static final Logger log = Logger.getLogger(AgentEnsResolver.class.getName());

    // Base Sepolia public RPC
    public static final String BASE_SEPOLIA_RPC = "https://ethereum-sepolia-rpc.publicnode.com";

    private final Web3j web3j;
    private final EnsResolver web3jEns;
    private final TextRecordClient textRecordClient;

    public AgentEnsResolver() {
        this(BASE_SEPOLIA_RPC);
    }

    public AgentEnsResolver(String rpcUrl) {
        this.web3j = Web3j.build(new HttpService(rpcUrl));
        this.web3jEns = new EnsResolver(web3j);
        this.textRecordClient = new TextRecordClient(rpcUrl);
    }

    // Package-private for testing
    AgentEnsResolver(Web3j web3j, EnsResolver web3jEns, TextRecordClient textRecordClient) {
        this.web3j = web3j;
        this.web3jEns = web3jEns;
        this.textRecordClient = textRecordClient;
    }

    /**
     * Resolve an ENS name to its Ethereum address.
     * e.g. "researcher.agentmesh.eth" → "0xd8dA6BF..."
     */
    public String resolveAddress(String ensName) throws EnsResolutionException {
        try {
            log.info("[ENS] Resolving address: " + ensName);
            // Bypass web3j sync check — use raw eth_call instead
            String address = textRecordClient.resolveAddressViaEthCall(ensName);
            log.info("[ENS] " + ensName + " → " + address);
            return address;
        } catch (Exception e) {
            throw new EnsResolutionException("Failed to resolve address for: " + ensName, e);
        }
    }

    /**
     * Read all AgentManifest text records for a given ENS name.
     * This is the core lookup operation — every agent invocation starts here.
     */
    public AgentManifest getManifest(String ensName) throws EnsResolutionException {
        log.info("[ENS] Fetching manifest for: " + ensName);

        try {
            String address = resolveAddress(ensName);

            String capabilities = textRecordClient.getText(ensName, AgentManifest.KEY_CAPABILITIES);
            String endpoint     = textRecordClient.getText(ensName, AgentManifest.KEY_ENDPOINT);
            String pubkey       = textRecordClient.getText(ensName, AgentManifest.KEY_PUBKEY);
            String model        = textRecordClient.getText(ensName, AgentManifest.KEY_MODEL);
            String version      = textRecordClient.getText(ensName, AgentManifest.KEY_VERSION);
            String description  = textRecordClient.getText(ensName, AgentManifest.KEY_DESCRIPTION);

            AgentManifest manifest = AgentManifest.builder()
                    .ensName(ensName)
                    .address(address)
                    .capabilitiesJson(capabilities)
                    .endpoint(endpoint)
                    .pubkey(pubkey)
                    .model(model)
                    .version(version)
                    .description(description)
                    .build();

            log.info("[ENS] Manifest resolved for " + ensName +
                    " | capabilities=" + manifest.capabilities() +
                    " | endpoint=" + manifest.endpoint());

            return manifest;

        } catch (EnsResolutionException e) {
            throw e;
        } catch (Exception e) {
            throw new EnsResolutionException("Failed to fetch manifest for: " + ensName, e);
        }
    }

    /**
     * Write an AgentManifest back to ENS text records.
     * Requires a funded wallet private key — used during agent registration.
     * Delegates to Namestone API for subname issuance + text record setting.
     */
    public void setManifest(String ensName, AgentManifest manifest, String privateKey)
            throws EnsResolutionException {
        log.info("[ENS] Writing manifest for: " + ensName);
        try {
            textRecordClient.setText(ensName, AgentManifest.KEY_CAPABILITIES,
                    AgentManifest.serialiseCapabilities(manifest.capabilities()), privateKey);
            textRecordClient.setText(ensName, AgentManifest.KEY_ENDPOINT,    manifest.endpoint(),     privateKey);
            textRecordClient.setText(ensName, AgentManifest.KEY_PUBKEY,      manifest.pubkey(),       privateKey);
            textRecordClient.setText(ensName, AgentManifest.KEY_MODEL,       manifest.model(),        privateKey);
            textRecordClient.setText(ensName, AgentManifest.KEY_VERSION,     manifest.version(),      privateKey);
            textRecordClient.setText(ensName, AgentManifest.KEY_DESCRIPTION, manifest.description(),  privateKey);
            log.info("[ENS] Manifest written for: " + ensName);
        } catch (Exception e) {
            throw new EnsResolutionException("Failed to write manifest for: " + ensName, e);
        }
    }

    public void shutdown() {
        web3j.shutdown();
    }
}
