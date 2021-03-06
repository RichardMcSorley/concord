package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Named
public class ForkCleanupProcessor implements PayloadProcessor {

    @Override
    public Payload process(Chain chain, Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);

        try {
            Path markerDir = workspace.resolve(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME)
                    .resolve(InternalConstants.Files.JOB_STATE_DIR_NAME);

            String[] markers = {InternalConstants.Files.SUSPEND_MARKER_FILE_NAME, InternalConstants.Files.RESUME_MARKER_FILE_NAME};
            for (String m : markers) {
                Path suspendMarker = markerDir.resolve(m);
                Files.deleteIfExists(suspendMarker);
            }
        } catch (IOException e) {
            throw new ProcessException(payload.getProcessKey(), "Error while preparing a fork's data", e);
        }

        return chain.process(payload);
    }
}
