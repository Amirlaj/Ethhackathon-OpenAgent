package mesh.app.config;

import mesh.core.ens.AgentEnsResolver;
import mesh.core.ens.AgentRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Value("${AGENT_PARENT_DOMAIN:agentmesh-dev.eth}")
    private String parentDomain;

    @Value("${AGENT_DEV_MODE:true}")
    private boolean devMode;

    @Bean
    public AgentEnsResolver agentEnsResolver() {
        return new AgentEnsResolver();
    }

    @Bean
    public AgentRegistry agentRegistry(AgentEnsResolver resolver) {
        return new AgentRegistry(resolver, parentDomain, devMode);
    }
}
