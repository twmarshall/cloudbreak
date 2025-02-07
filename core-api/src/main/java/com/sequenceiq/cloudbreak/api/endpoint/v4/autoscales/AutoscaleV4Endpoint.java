package com.sequenceiq.cloudbreak.api.endpoint.v4.autoscales;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import com.sequenceiq.cloudbreak.api.endpoint.v4.autoscales.request.AmbariAddressV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.autoscales.request.ChangedNodesReportV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.autoscales.request.FailureReportV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.autoscales.request.UpdateStackV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.autoscales.response.AuthorizeForAutoscaleV4Response;
import com.sequenceiq.cloudbreak.api.endpoint.v4.autoscales.response.AutoscaleStackV4Responses;
import com.sequenceiq.cloudbreak.api.endpoint.v4.autoscales.response.CertificateV4Response;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.UpdateClusterV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.StackStatusV4Response;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.StackV4Response;
import com.sequenceiq.cloudbreak.doc.ControllerDescription;
import com.sequenceiq.cloudbreak.doc.Notes;
import com.sequenceiq.cloudbreak.doc.OperationDescriptions.ClusterOpDescription;
import com.sequenceiq.cloudbreak.doc.OperationDescriptions.StackOpDescription;
import com.sequenceiq.cloudbreak.jerseyclient.retry.RetryingRestClient;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Path("/autoscale")
@RetryingRestClient
@Consumes(APPLICATION_JSON)
@Api(value = "/autoscale", description = ControllerDescription.AUTOSCALE_DESCRIPTION, protocols = "http,https",
        consumes = APPLICATION_JSON)
public interface AutoscaleV4Endpoint {

    @PUT
    @Path("/stack/crn/{crn}/{userId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = StackOpDescription.PUT_BY_ID, produces = APPLICATION_JSON, notes = Notes.STACK_NOTES, nickname = "putStackForAutoscale")
    void putStack(@PathParam("crn") String crn, @PathParam("userId") String userId, @Valid UpdateStackV4Request updateRequest);

    @PUT
    @Path("/stack/crn/{crn}/{userId}/cluster")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = StackOpDescription.PUT_BY_ID, produces = APPLICATION_JSON, notes = Notes.STACK_NOTES, nickname = "putClusterForAutoscale")
    void putCluster(@PathParam("crn") String crn, @PathParam("userId") String userId, @Valid UpdateClusterV4Request updateRequest);

    @POST
    @Path("ambari")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = StackOpDescription.GET_BY_AMBARI_ADDRESS, produces = APPLICATION_JSON, notes = Notes.STACK_NOTES,
            nickname = "getStackForAmbariForAutoscale")
    StackV4Response getStackForAmbari(@Valid AmbariAddressV4Request json);

    @GET
    @Path("stack/all")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = StackOpDescription.GET_ALL, produces = APPLICATION_JSON, notes = Notes.STACK_NOTES, nickname = "getAllStackForAutoscale")
    AutoscaleStackV4Responses getAllForAutoscale();

    @POST
    @Path("/stack/crn/{crn}/cluster/failurereport")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = ClusterOpDescription.FAILURE_REPORT, produces = APPLICATION_JSON, notes = Notes.FAILURE_REPORT_NOTES,
            nickname = "failureReportClusterForAutoscale")
    void failureReport(@PathParam("crn") String crn, FailureReportV4Request failureReport);

    @POST
    @Path("/stack/crn/{crn}/cluster/changed_nodes_report")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = ClusterOpDescription.CHANGED_NODES_REPORT, produces = APPLICATION_JSON, notes = Notes.CHANGED_NODES_REPORT_NOTES,
            nickname = "nodeStatusChangeReportClusterForAutoscale")
    void changedNodesReport(@PathParam("crn") String crn, ChangedNodesReportV4Request changedNodesReport);

    @GET
    @Path("/stack/crn/{crn}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = StackOpDescription.GET_BY_CRN, produces = APPLICATION_JSON, notes = Notes.STACK_NOTES, nickname = "getStackForAutoscale")
    StackV4Response get(@PathParam("crn") String crn);

    @GET
    @Path("/stack/crn/{crn}/status")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = StackOpDescription.GET_BY_CRN, produces = APPLICATION_JSON, notes = Notes.STACK_NOTES, nickname = "getStackStatusForAutoscale")
    StackStatusV4Response getStatusByCrn(@PathParam("crn") String crn);

    @GET
    @Path("/stack/crn/{crn}/authorize/{userId}/{tenant}/{permission}")
    @Produces(APPLICATION_JSON)
    AuthorizeForAutoscaleV4Response authorizeForAutoscale(@PathParam("crn") String crn, @PathParam("userId") String userId, @PathParam("tenant") String tenant,
            @PathParam("permission") String permission);

    @GET
    @Path("/stack/crn/{crn}/certificate")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = StackOpDescription.GET_STACK_CERT, produces = APPLICATION_JSON, notes = Notes.STACK_NOTES,
            nickname = "getCertificateStackForAutoscale")
    CertificateV4Response getCertificate(@PathParam("crn") String crn);
}
