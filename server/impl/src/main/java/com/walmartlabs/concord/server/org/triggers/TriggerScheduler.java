package com.walmartlabs.concord.server.org.triggers;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadBuilder;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.process.ProcessSecurityContext;
import org.eclipse.sisu.EagerSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.xml.bind.DatatypeConverter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Named
@EagerSingleton
public class TriggerScheduler {

    private static final Logger log = LoggerFactory.getLogger(TriggerScheduler.class);

    private static final String INITIATOR = "cron";
    private static final String REALM = "cron";

    private static final long ERROR_DELAY = TimeUnit.MINUTES.toMillis(5);

    @Inject
    public TriggerScheduler(TriggerScheduleDao schedulerDao, ProcessManager processManager,
                            ProcessSecurityContext processSecurityContext) {
        new Thread(new SchedulerWorker(schedulerDao, processManager, processSecurityContext),
                "cron-trigger-worker").start();
    }

    private static final class SchedulerWorker implements Runnable {

        private final TriggerScheduleDao schedulerDao;
        private final ProcessManager processManager;
        private final ProcessSecurityContext processSecurityContext;
        private final Date startedAt;

        private SchedulerWorker(TriggerScheduleDao schedulerDao, ProcessManager processManager, ProcessSecurityContext processSecurityContext) {
            this.schedulerDao = schedulerDao;
            this.processManager = processManager;
            this.processSecurityContext = processSecurityContext;
            this.startedAt = new Date();
        }

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                try {
                    TriggerSchedulerEntry e = schedulerDao.findNext();

                    if (e != null && e.getFireAt().after(startedAt)) {
                        log.info("run -> starting {}...", e);
                        startProcess(e);
                    } else {
                        long now = System.currentTimeMillis();
                        long millisWithinMinute = now % 60000;
                        sleep(60000 - millisWithinMinute);
                    }
                } catch (Exception e) {
                    log.error("run -> error", e);
                    sleep(ERROR_DELAY);
                }
            }
        }

        private void startProcess(TriggerSchedulerEntry t) {
            Map<String, Object> args = new HashMap<>();
            if (t.getArguments() != null) {
                args.putAll(t.getArguments());
            }
            args.put("event", makeEvent(t));

            startProcess(t.getTriggerId(), t.getOrgId(), t.getProjectId(), t.getRepoId(), t.getEntryPoint(), args);
        }

        private void startProcess(UUID triggerId, UUID orgId, UUID projectId, UUID repoId, String flowName, Map<String, Object> args) {
            UUID instanceId = UUID.randomUUID();

            Map<String, Object> request = new HashMap<>();
            request.put(Constants.Request.ARGUMENTS_KEY, args);

            Payload payload;
            try {
                payload = new PayloadBuilder(instanceId)
                        .initiator(INITIATOR)
                        .organization(orgId)
                        .project(projectId)
                        .repository(repoId)
                        .entryPoint(flowName)
                        .configuration(request)
                        .build();
            } catch (Exception e) {
                log.error("startProcess ['{}', '{}', '{}', '{}', '{}'] -> error creating a payload",
                        triggerId, orgId, projectId, repoId, flowName, e);
                return;
            }

            try {
                processSecurityContext.runAs(REALM, INITIATOR,
                        () -> processManager.start(payload, false));
            } catch (Exception e) {
                log.error("startProcess ['{}', '{}', '{}', '{}', '{}'] -> error starting process",
                        triggerId, orgId, projectId, repoId, flowName, e);
            }

            log.info("startProcess ['{}', '{}', '{}', '{}', '{}'] -> process '{}' started", triggerId, orgId, projectId, repoId, flowName, instanceId);
        }

        private void sleep(long t) {
            try {
                Thread.sleep(t);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Map<String, Object> makeEvent(TriggerSchedulerEntry t) {
        Map<String, Object> m = new HashMap<>();
        m.put("spec", t.getCronSpec());

        Calendar c = Calendar.getInstance();
        c.setTime(t.getFireAt());
        m.put("fireAt", DatatypeConverter.printDateTime(c));

        return m;
    }
}