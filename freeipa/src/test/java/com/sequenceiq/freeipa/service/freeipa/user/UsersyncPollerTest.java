package com.sequenceiq.freeipa.service.freeipa.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sequenceiq.cloudbreak.auth.altus.EntitlementService;
import com.sequenceiq.cloudbreak.common.json.Json;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.Status;
import com.sequenceiq.freeipa.entity.Stack;
import com.sequenceiq.freeipa.entity.UserSyncStatus;
import com.sequenceiq.freeipa.service.freeipa.user.model.UmsEventGenerationIds;
import com.sequenceiq.freeipa.service.stack.StackService;

@ExtendWith(MockitoExtension.class)
class UsersyncPollerTest {

    private static final String ACCOUNT_ID = UUID.randomUUID().toString();

    private static final String ENVIRONMENT_CRN = "crn:cdp:environments:us-west-1:" + ACCOUNT_ID + ":environment:" + UUID.randomUUID().toString();

    private static final long COOLDOWN_MILLIS = 600000L;

    @Mock
    StackService stackService;

    @Mock
    UserSyncService userSyncService;

    @Mock
    UserSyncStatusService userSyncStatusService;

    @Mock
    UmsEventGenerationIdsProvider umsEventGenerationIdsProvider;

    @Mock
    EntitlementService entitlementService;

    @InjectMocks
    UsersyncPoller underTest;

    @BeforeEach
    void setUp() {
        underTest.cooldownMillis = COOLDOWN_MILLIS;
    }

    @Test
    void testIsStale() throws Exception {
        Stack stack = createStack();
        UmsEventGenerationIds currentEventGenerationIds = createUniqueUmsEventGenerationIds();
        UserSyncStatus userSyncStatus = createUserSyncStatus(stack);
        userSyncStatus.setUmsEventGenerationIds(new Json(createUniqueUmsEventGenerationIds()));

        assertTrue(underTest.isStale(stack, userSyncStatus, currentEventGenerationIds));
    }

    @Test
    void testIsNotStale() throws Exception {
        Stack stack = createStack();
        UmsEventGenerationIds currentEventGenerationIds = createUniqueUmsEventGenerationIds();
        UserSyncStatus userSyncStatus = createUserSyncStatus(stack);
        userSyncStatus.setUmsEventGenerationIds(new Json(currentEventGenerationIds));

        assertFalse(underTest.isStale(stack, userSyncStatus, currentEventGenerationIds));
    }

    @Test
    void testIsCool() throws Exception {
        Stack stack = createStack();
        UserSyncStatus userSyncStatus = createUserSyncStatus(stack);
        long cooldownExpiration = System.currentTimeMillis();
        long lastStartTime = cooldownExpiration - 1L;
        userSyncStatus.setLastFullSyncStartTime(lastStartTime);


        assertTrue(underTest.isCooldownExpired(stack, userSyncStatus, cooldownExpiration));
    }

    @Test
    void testIsNotCool() throws Exception {
        Stack stack = createStack();
        UserSyncStatus userSyncStatus = createUserSyncStatus(stack);
        long cooldownExpiration = System.currentTimeMillis();
        long lastStartTime = cooldownExpiration + 1L;
        userSyncStatus.setLastFullSyncStartTime(lastStartTime);

        assertFalse(underTest.isCooldownExpired(stack, userSyncStatus, cooldownExpiration));
    }

    @Test
    void testSyncStackWhenStale() throws Exception {
        Stack stack = createStack();
        setupMockStackService(stack);
        setupEntitlement(true);
        UserSyncStatus userSyncStatus = createUserSyncStatus(stack);
        setupEventGenerationIds(userSyncStatus, true);
        setupMockUserSyncStatusService(userSyncStatus);

        underTest.syncFreeIpaStacks();

        verify(userSyncService).synchronizeUsers(ACCOUNT_ID, UsersyncPoller.INTERNAL_ACTOR_CRN,
                Set.of(ENVIRONMENT_CRN), Set.of(), Set.of());
    }

    @Test
    void testDontSyncStackWhenNotStale() throws Exception {
        Stack stack = createStack();
        setupMockStackService(stack);
        setupEntitlement(true);
        UserSyncStatus userSyncStatus = createUserSyncStatus(stack);
        setupEventGenerationIds(userSyncStatus, false);
        setupMockUserSyncStatusService(userSyncStatus);

        underTest.syncFreeIpaStacks();

        verify(userSyncService, times(0))
                .synchronizeUsers(any(), any(), any(), any(), any());
    }

    @Test
    void testSyncStackWhenCool() throws Exception {
        Stack stack = createStack();
        setupMockStackService(stack);
        setupEntitlement(true);
        UserSyncStatus userSyncStatus = createUserSyncStatus(stack);
        setupEventGenerationIds(userSyncStatus, true);
        userSyncStatus.setLastFullSyncStartTime(System.currentTimeMillis() - underTest.cooldownMillis - 1L);
        setupMockUserSyncStatusService(userSyncStatus);

        underTest.syncFreeIpaStacks();

        verify(userSyncService).synchronizeUsers(ACCOUNT_ID, UsersyncPoller.INTERNAL_ACTOR_CRN,
                Set.of(ENVIRONMENT_CRN), Set.of(), Set.of());
    }

    @Test
    void testDontSyncStackWhenNotCool() throws Exception {
        Stack stack = createStack();
        setupMockStackService(stack);
        setupEntitlement(true);
        UserSyncStatus userSyncStatus = createUserSyncStatus(stack);
        setupEventGenerationIds(userSyncStatus, true);
        userSyncStatus.setLastFullSyncStartTime(System.currentTimeMillis());
        setupMockUserSyncStatusService(userSyncStatus);

        underTest.syncFreeIpaStacks();

        verify(userSyncService, times(0))
                .synchronizeUsers(any(), any(), any(), any(), any());
    }

    @Test
    void testDontSyncStackWhenNotEntitled() throws Exception {
        setupMockStackService(createStack());
        setupEntitlement(false);

        underTest.syncFreeIpaStacks();

        verify(userSyncService, times(0))
                .synchronizeUsers(any(), any(), any(), any(), any());
    }

    private Stack createStack() {
        Stack stack = new Stack();
        stack.setAccountId(ACCOUNT_ID);
        stack.setEnvironmentCrn(ENVIRONMENT_CRN);
        return stack;
    }

    private Stack setupMockStackService(Stack stack) {
        when(stackService.findAllWithStatuses(Status.AVAILABLE_STATUSES)).thenReturn(List.of(stack));
        return stack;
    }

    private UserSyncStatus createUserSyncStatus(Stack stack) {
        UserSyncStatus userSyncStatus = new UserSyncStatus();
        userSyncStatus.setStack(stack);
        return userSyncStatus;
    }

    private void setupMockUserSyncStatusService(UserSyncStatus userSyncStatus) {
        when(userSyncStatusService.getOrCreateForStack(userSyncStatus.getStack())).thenReturn(userSyncStatus);
    }

    private void setupEntitlement(boolean entitled) {
        when(entitlementService.automaticUsersyncPollerEnabled(any(), any())).thenReturn(entitled);
    }

    private UmsEventGenerationIds createUniqueUmsEventGenerationIds() {
        UmsEventGenerationIds umsEventGenerationIds = new UmsEventGenerationIds();
        umsEventGenerationIds.setEventGenerationIds(Map.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        return umsEventGenerationIds;
    }

    private void setupEventGenerationIds(UserSyncStatus userSyncStatus, boolean stale) {
        UmsEventGenerationIds currentEventGenerationIds = createUniqueUmsEventGenerationIds();
        when(umsEventGenerationIdsProvider.getEventGenerationIds(any(), any())).thenReturn(currentEventGenerationIds);

        if (stale) {
            userSyncStatus.setUmsEventGenerationIds(new Json(createUniqueUmsEventGenerationIds()));
        } else {
            userSyncStatus.setUmsEventGenerationIds(new Json(currentEventGenerationIds));
        }
    }
}