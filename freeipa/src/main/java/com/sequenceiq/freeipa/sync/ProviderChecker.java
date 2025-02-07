package com.sequenceiq.freeipa.sync;

import static com.sequenceiq.cloudbreak.cloud.model.AvailabilityZone.availabilityZone;
import static com.sequenceiq.cloudbreak.cloud.model.Location.location;
import static com.sequenceiq.cloudbreak.cloud.model.Region.region;
import static com.sequenceiq.cloudbreak.util.Benchmark.checkedMeasure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.handler.InstanceStateQuery;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.cloud.model.CloudInstance;
import com.sequenceiq.cloudbreak.cloud.model.CloudVmInstanceStatus;
import com.sequenceiq.cloudbreak.cloud.model.Location;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.instance.InstanceStatus;
import com.sequenceiq.freeipa.converter.cloud.CredentialToCloudCredentialConverter;
import com.sequenceiq.freeipa.converter.cloud.InstanceMetaDataToCloudInstanceConverter;
import com.sequenceiq.freeipa.converter.cloud.StackToCloudStackConverter;
import com.sequenceiq.freeipa.entity.InstanceMetaData;
import com.sequenceiq.freeipa.entity.Stack;
import com.sequenceiq.freeipa.service.CredentialService;
import com.sequenceiq.freeipa.service.GatewayConfigService;
import com.sequenceiq.freeipa.service.stack.StackUpdater;
import com.sequenceiq.freeipa.service.stack.instance.InstanceMetaDataService;

@Component
public class ProviderChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderChecker.class);

    @Inject
    private InstanceStateQuery instanceStateQuery;

    @Inject
    private GatewayConfigService gatewayConfigService;

    @Inject
    private CredentialService credentialService;

    @Inject
    private StackToCloudStackConverter cloudStackConverter;

    @Inject
    private CredentialToCloudCredentialConverter credentialConverter;

    @Inject
    private InstanceMetaDataToCloudInstanceConverter metadataConverter;

    @Inject
    private InstanceMetaDataService instanceMetaDataService;

    @Inject
    private StackUpdater stackUpdater;

    @Value("${freeipa.autosync.update.status:false}")
    private boolean updateStatus;

    public List<ProviderSyncResult> updateAndGetStatuses(Stack stack) {
        return checkedMeasure(() -> {
            Set<InstanceMetaData> notTerminatedForStack = instanceMetaDataService.findNotTerminatedForStack(stack.getId());
            Set<InstanceMetaData> checkableInstances = notTerminatedForStack.stream().filter(i -> !i.isDeletedOnProvider()).collect(Collectors.toSet());

            int alreadyDeletedCount = notTerminatedForStack.size() - checkableInstances.size();
            if (alreadyDeletedCount > 0) {
                LOGGER.info(":::Auto sync updater::: Count of already in deleted on provider side state: {}", alreadyDeletedCount);
            }
            List<ProviderSyncResult> results = new ArrayList<>();
            List<CloudVmInstanceStatus> statuses = checkStatus(stack, checkableInstances);
            statuses.forEach(s -> {
                Optional<InstanceMetaData> instanceMetaData = checkableInstances.stream()
                        .filter(i -> s.getCloudInstance().getInstanceId().equals(i.getInstanceId()))
                        .findFirst();
                if (instanceMetaData.isPresent()) {
                    InstanceStatus instanceStatus = updateStatuses(s, instanceMetaData.get());
                    if (instanceStatus != null) {
                        results.add(new ProviderSyncResult("", instanceStatus, false, s.getCloudInstance().getInstanceId()));
                    }
                } else {
                    LOGGER.info(":::Auto sync updater::: Cannot find");
                }
            });
            checkableInstances.forEach(instanceMetaData -> {
                if (statuses.stream().noneMatch(s -> s.getCloudInstance().getInstanceId().equals(instanceMetaData.getInstanceId()))) {
                    if (updateStatus) {
                        setStatusIfNotTheSame(instanceMetaData, InstanceStatus.DELETED_ON_PROVIDER_SIDE);
                        instanceMetaDataService.save(instanceMetaData);
                    }
                }
            });
            return results;
        }, LOGGER, ":::Auto sync measure::: provider is checked in {}ms");
    }

    private InstanceStatus updateStatuses(CloudVmInstanceStatus vmInstanceStatus, InstanceMetaData instanceMetaData) {
        LOGGER.info(":::Auto sync updater::: {} instance metadata status update in progress, new status: {}",
                instanceMetaData.getShortHostname(), vmInstanceStatus);
        InstanceStatus status = null;
        switch (vmInstanceStatus.getStatus()) {
            case STARTED:
                setStatusIfNotTheSame(instanceMetaData, InstanceStatus.CREATED);
                status = InstanceStatus.CREATED;
                break;
            case STOPPED:
                setStatusIfNotTheSame(instanceMetaData, InstanceStatus.STOPPED);
                status = InstanceStatus.STOPPED;
                break;
            case FAILED:
                setStatusIfNotTheSame(instanceMetaData, InstanceStatus.FAILED);
                status = InstanceStatus.FAILED;
                break;
            case TERMINATED:
                setStatusIfNotTheSame(instanceMetaData, InstanceStatus.DELETED_ON_PROVIDER_SIDE);
                status = InstanceStatus.DELETED_ON_PROVIDER_SIDE;
                break;
            default:
                LOGGER.info(":::Auto sync updater::: the '{}' status is not converted", vmInstanceStatus.getStatus());
        }
        if (updateStatus) {
            instanceMetaDataService.save(instanceMetaData);
        }
        return status;
    }

    private void setStatusIfNotTheSame(InstanceMetaData instanceMetaData, InstanceStatus newStatus) {
        if (instanceMetaData.getInstanceStatus() != newStatus) {
            if (updateStatus) {
                instanceMetaData.setInstanceStatus(newStatus);
            } else {
                LOGGER.info(":::Auto sync updater::: The instance status would be had to update from {} to {}",
                        instanceMetaData.getInstanceStatus(), InstanceStatus.DELETED_ON_PROVIDER_SIDE);
            }
        }
    }

    private List<CloudVmInstanceStatus> checkStatus(Stack stack, Set<InstanceMetaData> notTerminatedForStack) {
        Location location = location(region(stack.getRegion()), availabilityZone(stack.getAvailabilityZone()));
        CloudContext cloudContext = new CloudContext(stack.getId(), stack.getName(), stack.getCloudPlatform(), stack.getCloudPlatform(),
                location, stack.getOwner(), stack.getOwner(), stack.getAccountId());
        CloudCredential cloudCredential = credentialConverter.convert(credentialService.getCredentialByEnvCrn(stack.getEnvironmentCrn()));
        List<CloudInstance> instances = metadataConverter.convert(notTerminatedForStack);
        try {
            return checkedMeasure(() -> instanceStateQuery.getCloudVmInstanceStatuses(cloudCredential, cloudContext, instances), LOGGER,
                    ":::Auto sync measure::: get instance statuses in {}ms");
        } catch (Exception e) {
            LOGGER.info(":::Auto sync updater::: Could not fetch vm statuses" + e.getMessage(), e);
            throw e;
        }
    }
}
