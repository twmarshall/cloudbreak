package com.sequenceiq.cloudbreak.cloud.event.resource;

import java.util.List;

import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.cloud.model.CloudInstance;
import com.sequenceiq.cloudbreak.cloud.model.CloudStack;

public class UpscaleStackValidationRequest<T> extends CloudStackRequest<T> {

    private final List<CloudInstance> newInstances;

    private final String instanceGroupName;

    public UpscaleStackValidationRequest(CloudContext cloudContext, CloudCredential cloudCredential, CloudStack stack, List<CloudInstance> newInstances, String instanceGroupName) {
        super(cloudContext, cloudCredential, stack);
        this.newInstances = newInstances;
        this.instanceGroupName = instanceGroupName;
    }

    public List<CloudInstance> getNewInstances() {
        return newInstances;
    }

    public String getInstanceGroupName() {
        return instanceGroupName;
    }
}
