package com.sequenceiq.cloudbreak.cloud.aws.client;

import com.amazonaws.services.servicequotas.AWSServiceQuotasClient;
import com.amazonaws.services.servicequotas.model.GetServiceQuotaRequest;
import com.amazonaws.services.servicequotas.model.GetServiceQuotaResult;
import com.sequenceiq.cloudbreak.service.Retry;

public class AmazonServiceQuotaRetryClient extends AmazonRetryClient {

    private final AWSServiceQuotasClient client;

    private final Retry retry;

    public AmazonServiceQuotaRetryClient(AWSServiceQuotasClient client, Retry retry) {
        this.client = client;
        this.retry = retry;
    }

    public GetServiceQuotaResult getServiceQuotaResult(GetServiceQuotaRequest request) {
        return retry.testWith2SecDelayMax15Times(() -> mapThrottlingError(() -> client.getServiceQuota(request)));
    }
}
