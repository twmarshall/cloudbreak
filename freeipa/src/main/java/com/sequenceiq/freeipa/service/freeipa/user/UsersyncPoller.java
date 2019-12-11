package com.sequenceiq.freeipa.service.freeipa.user;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.google.common.annotations.VisibleForTesting;
import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;
import com.sequenceiq.cloudbreak.auth.altus.Crn;
import com.sequenceiq.cloudbreak.auth.altus.EntitlementService;
import com.sequenceiq.cloudbreak.auth.security.InternalCrnBuilder;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.Status;
import com.sequenceiq.freeipa.api.v1.freeipa.user.model.SyncOperationStatus;
import com.sequenceiq.freeipa.entity.Stack;
import com.sequenceiq.freeipa.entity.UserSyncStatus;
import com.sequenceiq.freeipa.service.freeipa.user.model.UmsEventGenerationIds;
import com.sequenceiq.freeipa.service.stack.StackService;

@Service
@ConditionalOnProperty(
        value = "freeipa.usersync.poller.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class UsersyncPoller {
    @VisibleForTesting
    static final String INTERNAL_ACTOR_CRN = new InternalCrnBuilder(Crn.Service.IAM).getInternalCrnForServiceAsString();

    private static final Logger LOGGER = LoggerFactory.getLogger(UsersyncPoller.class);

    @VisibleForTesting
    @Value("${freeipa.usersync.poller.cooldown-millis}")
    long cooldownMillis;

    @Inject
    private StackService stackService;

    @Inject
    private UserSyncStatusService userSyncStatusService;

    @Inject
    private UserSyncService userSyncService;

    @Inject
    private UmsEventGenerationIdsProvider umsEventGenerationIdsProvider;

    @Inject
    private EntitlementService entitlementService;

    @Scheduled(fixedDelayString = "${freeipa.usersync.poller.fixed-delay-millis}",
            initialDelayString = "${freeipa.usersync.poller.initial-delay-millis}")
    public void pollUms() {
        try {
            LOGGER.debug("Polling for stale stacks");
            syncFreeIpaStacks();
        } catch (Exception e) {
            LOGGER.error("Failed to automatically sync users to FreeIPA stacks", e);
        }
    }

    @VisibleForTesting
    void syncFreeIpaStacks() {
        try {
            Optional<String> requestId = Optional.of(UUID.randomUUID().toString());
            MDCBuilder.addRequestId(requestId.get());
            LOGGER.debug("Setting request id = {} for this poll", requestId);

            ThreadBasedUserCrnProvider.doAs(INTERNAL_ACTOR_CRN, () -> {
                LOGGER.debug("Attempting to sync users to FreeIPA stacks");
                List<Stack> stackList = stackService.findAllWithStatuses(Status.AVAILABLE_STATUSES);
                LOGGER.debug("Found {} active stacks", stackList.size());

                stackList.stream()
                        .collect(Collectors.groupingBy(Stack::getAccountId))
                        .entrySet().stream()
                        .filter(stringListEntry -> isAccountEntitled(stringListEntry.getKey()))
                        .forEach(stringListEntry -> {
                            String accountId = stringListEntry.getKey();
                            LOGGER.debug("Usersync polling is entitled in account {}", accountId);
                            syncFreeIpaStacksInAccount(requestId, accountId, stringListEntry.getValue());
                        });
            });
        } finally {
            MDCBuilder.cleanupMdc();
        }
    }

    @VisibleForTesting
    boolean isStale(Stack stack, UserSyncStatus userSyncStatus, UmsEventGenerationIds currentGeneration) {
        try {
            boolean stale = userSyncStatus == null ||
                    userSyncStatus.getUmsEventGenerationIds() == null;
            if (!stale) {
                try {
                    UmsEventGenerationIds lastUmsEventGenerationIds = userSyncStatus.getUmsEventGenerationIds().get(UmsEventGenerationIds.class);
                    stale = !currentGeneration.equals(lastUmsEventGenerationIds);
                } catch (IOException e) {
                    LOGGER.warn("Failed to retrieve UmsEventGenerationIds for Environment {} in Account {}. Assuming stale",
                            stack.getEnvironmentCrn(), stack.getAccountId());
                    stale = true;
                }
            }
            LOGGER.debug("Environment {} in Account {} {} stale", stack.getEnvironmentCrn(), stack.getAccountId(), stale ? "is" : "is not");
            return stale;
        } catch (Exception e) {
            LOGGER.warn("Unable to calculate staleness due to exception.", e);
            return false;
        }
    }

    @VisibleForTesting
    boolean isCooldownExpired(Stack stack, UserSyncStatus userSyncStatus, long cooldownThresholdTime) {
        try {
            boolean cool = userSyncStatus == null ||
                    userSyncStatus.getLastFullSyncStartTime() == null ||
                    userSyncStatus.getLastFullSyncStartTime() < cooldownThresholdTime;
            LOGGER.debug("Environment {} in Account {} {} cool", stack.getEnvironmentCrn(), stack.getAccountId(), cool ? "is" : "is not");
            return cool;
        } catch (Exception e) {
            LOGGER.warn("Unable to determine if cooldown expired due to exception.", e);
            return false;
        }
    }

    private boolean isAccountEntitled(String accountId) {
        boolean entitled = entitlementService.automaticUsersyncPollerEnabled(INTERNAL_ACTOR_CRN, accountId);
        if (!entitled) {
            LOGGER.debug("Usersync polling is not entitled in accout {}. skipping", accountId);
        }
        return entitled;
    }

    private void syncFreeIpaStacksInAccount(Optional<String> requestId, String accountId, List<Stack> stacks) {
        long cooldownThresholdTime = System.currentTimeMillis() - cooldownMillis;

        UmsEventGenerationIds currentGeneration =
                umsEventGenerationIdsProvider.getEventGenerationIds(accountId, requestId);
        stacks.stream()
                .forEach(stack -> {
                    UserSyncStatus userSyncStatus = userSyncStatusService.getOrCreateForStack(stack);
                    if (isStale(stack, userSyncStatus, currentGeneration) && isCooldownExpired(stack, userSyncStatus, cooldownThresholdTime)) {
                        LOGGER.debug("Environment {} in Account {} is both stale and cool.",
                                stack.getEnvironmentCrn(), stack.getAccountId());
                        SyncOperationStatus operation = userSyncService.synchronizeUsers(stack.getAccountId(), INTERNAL_ACTOR_CRN,
                                Set.of(stack.getEnvironmentCrn()), Set.of(), Set.of());
                        LOGGER.debug("User Sync request resulted in operation {}", operation);
                    } else {
                        LOGGER.debug("Environment {} in Account {} is up-to-date.", stack.getEnvironmentCrn(), stack.getAccountId());
                    }
                });
    }
}
