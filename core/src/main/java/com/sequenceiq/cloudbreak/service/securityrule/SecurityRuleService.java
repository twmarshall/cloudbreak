package com.sequenceiq.cloudbreak.service.securityrule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.api.endpoint.v4.util.responses.SecurityRuleV4Response;
import com.sequenceiq.cloudbreak.api.endpoint.v4.util.responses.SecurityRulesV4Response;
import com.sequenceiq.cloudbreak.domain.SecurityRule;
import com.sequenceiq.cloudbreak.repository.SecurityRuleRepository;

@Service
public class SecurityRuleService {

    private static final String TCP_PROTOCOL = "tcp";

    @Value("${cb.nginx.port}")
    private String gatewayPort;

    @Value("${cb.ssh.port}")
    private String sshPort;

    @Value("${cb.https.port}")
    private String httpsPort;

    @Value("#{'${cb.default.gateway.cidr}'.split(',')}")
    private Set<String> defaultGatewayCidr;

    @Inject
    private SecurityRuleRepository repository;

    public SecurityRulesV4Response getDefaultSecurityRules() {
        SecurityRulesV4Response ret = new SecurityRulesV4Response();
        Set<String> defaultGatewayCidrs = defaultGatewayCidr
                .stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        if (!defaultGatewayCidrs.isEmpty()) {
            defaultGatewayCidr.forEach(cloudbreakCidr -> addCidrAndPort(ret, cloudbreakCidr));
        }
        return ret;
    }

    private void addCidrAndPort(SecurityRulesV4Response ret, String cloudbreakCidr) {
        ret.getGateway().addAll(createSecurityRuleResponse(cloudbreakCidr, gatewayPort, httpsPort));
        ret.getGateway().addAll(createSecurityRuleResponse(cloudbreakCidr, sshPort));
        ret.getCore().addAll(createSecurityRuleResponse(cloudbreakCidr, sshPort));
    }

    private Collection<SecurityRuleV4Response> createSecurityRuleResponse(String cidr, String... ports) {
        Collection<SecurityRuleV4Response> rules = new ArrayList<>();
        for (String port : ports) {
            if (StringUtils.isNotBlank(port)) {
                SecurityRuleV4Response securityRuleV4Response = new SecurityRuleV4Response();
                securityRuleV4Response.setPorts(Collections.singletonList(port));
                securityRuleV4Response.setProtocol(TCP_PROTOCOL);
                securityRuleV4Response.setSubnet(cidr);
                rules.add(securityRuleV4Response);
            }
        }
        return rules;
    }

    public List<SecurityRule> findAllBySecurityGroupId(Long securityGroupId) {
        return repository.findAllBySecurityGroupId(securityGroupId);
    }

}
