package com.sequenceiq.environment.environment.validation;

import static com.sequenceiq.cloudbreak.common.mappable.CloudPlatform.AWS;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.cloud.model.CloudRegions;
import com.sequenceiq.cloudbreak.cloud.model.CloudSubnet;
import com.sequenceiq.cloudbreak.common.mappable.CloudPlatform;
import com.sequenceiq.cloudbreak.validation.ValidationResult;
import com.sequenceiq.cloudbreak.validation.ValidationResult.ValidationResultBuilder;
import com.sequenceiq.environment.api.v1.environment.model.request.EnvironmentRequest;
import com.sequenceiq.environment.api.v1.environment.model.request.aws.AwsEnvironmentParameters;
import com.sequenceiq.environment.api.v1.environment.model.request.aws.S3GuardRequestParameters;
import com.sequenceiq.environment.environment.domain.Environment;
import com.sequenceiq.environment.environment.dto.EnvironmentDto;
import com.sequenceiq.environment.environment.validation.validators.EnvironmentRegionValidator;
import com.sequenceiq.environment.environment.validation.validators.NetworkCreationValidator;
import com.sequenceiq.environment.network.dto.NetworkDto;

@Component
public class EnvironmentValidatorService {

    private final EnvironmentRegionValidator environmentRegionValidator;

    private final NetworkCreationValidator networkCreationValidator;

    public EnvironmentValidatorService(EnvironmentRegionValidator environmentRegionValidator, NetworkCreationValidator networkCreationValidator) {
        this.environmentRegionValidator = environmentRegionValidator;
        this.networkCreationValidator = networkCreationValidator;
    }

    public ValidationResultBuilder validateRegionsAndLocation(String location, Set<String> requestedRegions,
        Environment environment, CloudRegions cloudRegions) {
        String cloudPlatform = environment.getCloudPlatform();
        ValidationResultBuilder regionValidationResult
                = environmentRegionValidator.validateRegions(requestedRegions, cloudRegions, cloudPlatform);
        ValidationResultBuilder locationValidationResult
                = environmentRegionValidator.validateLocation(location, requestedRegions, cloudRegions, cloudPlatform);
        return regionValidationResult.merge(locationValidationResult.build());
    }

    public ValidationResultBuilder validateNetworkCreation(Environment environment, NetworkDto network, Map<String, CloudSubnet> subnetMetas) {
        return networkCreationValidator.validateNetworkCreation(environment, network, subnetMetas);
    }

    public ValidationResult validateAwsEnvironmentRequest(EnvironmentRequest environmentRequest, String cloudPlatform) {
        ValidationResultBuilder resultBuilder = new ValidationResultBuilder();
        resultBuilder.ifError(() -> !CloudPlatform.AWS.name().equalsIgnoreCase(cloudPlatform),
                "Environment request is not for AWS.");

        resultBuilder.ifError(() -> StringUtils.isBlank(Optional.ofNullable(environmentRequest.getAws())
                .map(AwsEnvironmentParameters::getS3guard)
                .map(S3GuardRequestParameters::getDynamoDbTableName)
                .orElse(null)), "S3Guard Dynamo DB table name is not found in environment request.");
        return resultBuilder.build();
    }

    public ValidationResult validateAwsEnvironmentRequest(EnvironmentDto environmentDto) {
        ValidationResultBuilder resultBuilder = new ValidationResultBuilder();
        resultBuilder.ifError(() -> !AWS.name().equalsIgnoreCase(environmentDto.getCloudPlatform()),
                "Environment is not in AWS.");
        return resultBuilder.build();
    }
}
