package com.sequenceiq.datalake.service.sdx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sequenceiq.cloudbreak.common.mappable.CloudPlatform;
import com.sequenceiq.cloudbreak.exception.BadRequestException;
import com.sequenceiq.datalake.entity.SdxCluster;
import com.sequenceiq.datalake.repository.SdxClusterRepository;
import com.sequenceiq.datalake.service.sdx.database.DatabaseConfig;
import com.sequenceiq.datalake.service.sdx.database.DatabaseConfigKey;
import com.sequenceiq.datalake.service.sdx.database.DatabaseServerParameterSetter;
import com.sequenceiq.datalake.service.sdx.database.DatabaseService;
import com.sequenceiq.datalake.service.sdx.status.SdxStatusService;
import com.sequenceiq.environment.api.v1.environment.model.response.DetailedEnvironmentResponse;
import com.sequenceiq.redbeams.api.endpoint.v4.databaseserver.DatabaseServerV4Endpoint;
import com.sequenceiq.redbeams.api.endpoint.v4.databaseserver.requests.AllocateDatabaseServerV4Request;
import com.sequenceiq.redbeams.api.endpoint.v4.stacks.DatabaseServerV4StackRequest;
import com.sequenceiq.redbeams.api.endpoint.v4.stacks.aws.AwsDatabaseServerV4Parameters;
import com.sequenceiq.redbeams.client.RedbeamsServiceCrnClient;
import com.sequenceiq.sdx.api.model.SdxClusterShape;

@ExtendWith(MockitoExtension.class)
public class DatabaseServiceTest {

    @Captor
    public ArgumentCaptor<AllocateDatabaseServerV4Request> captor = ArgumentCaptor.forClass(AllocateDatabaseServerV4Request.class);

    @Mock
    private SdxClusterRepository sdxClusterRepository;

    @Mock
    private RedbeamsServiceCrnClient redbeamsClient;

    @Mock
    private SdxNotificationService notificationService;

    @Mock
    private SdxStatusService sdxStatusService;

    @Mock
    private Map<DatabaseConfigKey, DatabaseConfig> dbConfigs;

    @Mock
    private DatabaseServerV4Endpoint databaseServerV4Endpoint;

    @Mock
    private Map<CloudPlatform, DatabaseServerParameterSetter> databaseParameterSetterMap;

    @InjectMocks
    private DatabaseService underTest;

    @Test
    public void shouldSetDbConfigBasedOnClusterShape() {
        SdxCluster cluster = new SdxCluster();
        cluster.setClusterName("NAME");
        cluster.setClusterShape(SdxClusterShape.LIGHT_DUTY);
        DetailedEnvironmentResponse env = new DetailedEnvironmentResponse();
        env.setName("ENV");
        env.setCloudPlatform("aws");
        DatabaseConfig databaseConfig = getDatabaseConfig();

        when(databaseServerV4Endpoint.create(any())).thenThrow(BadRequestException.class);
        DatabaseConfigKey dbConfigKey = new DatabaseConfigKey(CloudPlatform.AWS, SdxClusterShape.LIGHT_DUTY);
        when(dbConfigs.get(dbConfigKey)).thenReturn(databaseConfig);
        when(databaseParameterSetterMap.get(CloudPlatform.AWS)).thenReturn(getDatabaseParameterSetter());

        Assertions.assertThrows(BadRequestException.class, () -> {
            underTest.create(cluster, env);
        });

        verify(databaseServerV4Endpoint).create(captor.capture());
        AllocateDatabaseServerV4Request dbRequest = captor.getValue();
        assertThat(dbRequest.getDatabaseServer().getInstanceType(), is("instanceType"));
        assertThat(dbRequest.getDatabaseServer().getDatabaseVendor(), is("vendor"));
        assertThat(dbRequest.getDatabaseServer().getStorageSize(), is(100L));
        assertNotNull(dbRequest.getDatabaseServer().getAws());
        verifyZeroInteractions(sdxClusterRepository);
        verifyZeroInteractions(sdxStatusService);
        verifyZeroInteractions(notificationService);
    }

    private DatabaseConfig getDatabaseConfig() {
        return new DatabaseConfig("instanceType", "vendor", 100);
    }

    private DatabaseServerParameterSetter getDatabaseParameterSetter() {
        return new DatabaseServerParameterSetter() {
            @Override
            public void setParameters(DatabaseServerV4StackRequest request) {
                request.setAws(new AwsDatabaseServerV4Parameters());
            }

            @Override
            public CloudPlatform getCloudPlatform() {
                return CloudPlatform.AWS;
            }
        };
    }
}
