package mesh.agent.config;

import mesh.core.ens.AgentEnsResolver;
import mesh.core.ens.AgentRegistry;
import mesh.core.manifest.ManifestValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    /**
     * The ENS name this agent instance represents.
     * Set via ENS_NAME env var, e.g. "researcher.agentmesh.eth"
     */
    @Value("${ENS_NAME}")
    private String ensName;

    /**
     * The parent ENS domain used for agent discovery.
     * Default: agentmesh.eth
     */
    @Value("${AGENT_PARENT_DOMAIN:agentmesh.eth}")
    private String parentDomain;

    @Bean
    public String ensName() {
        return ensName;
    }

    @Bean
    public AgentEnsResolver agentEnsResolver() {
        return new AgentEnsResolver();
    }

    @Bean
    public AgentRegistry agentRegistry(AgentEnsResolver resolver) {
        return new AgentRegistry(resolver, parentDomain);
    }

    @Bean
    public ManifestValidator manifestValidator() {
        return new ManifestValidator();
    }
}
