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

import { Action } from 'redux';

import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import {
    PaginationFilters,
    ProcessFilters,
    RestoreProcessResponse,
    StartProcessResponse
} from '../../../api/org/process';
import { ProcessEntry } from '../../../api/process';
import { RequestState } from '../common';
import { State as LogState } from './logs/types';
import { State as PollState } from './poll/types';
import { State as HistoryState } from './history/types';
import { State as AttachmentState } from './attachments/types';
import { State as ChildrenState } from './children/types';
import { State as EventsState } from './events/types';
import { State as AnsibleState } from './ansible/types';

export interface GetProcessRequest extends Action {
    instanceId: ConcordId;
}

export interface ListProcessesRequest extends Action {
    orgName?: ConcordKey;
    projectName?: ConcordKey;
    filters?: ProcessFilters;
    pagination?: PaginationFilters;
}

export interface PaginatedProcessDataResponse extends Action {
    error?: RequestError;
    items?: ProcessEntry[];
    prev?: number;
    next?: number;
}

export interface ProcessDataResponse extends Action {
    // TODO replace with RequestState<ProcessDataResponse>
    error?: RequestError;
    items?: ProcessEntry[];
}

export interface StartProcessRequest extends Action {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
    entryPoint: string;
}

export interface RestoreProcessRequest extends Action {
    instanceId: ConcordKey;
    checkpointId: ConcordKey;
}

export interface CancelProcessRequest extends Action {
    instanceId: ConcordId;
}
export interface CancelBulkProcessRequest extends Action {
    instanceIds: ConcordId[];
}

export interface PaginatedProcesses {
    processes: Processes;
    next?: number;
    prev?: number;
}

export interface Processes {
    [id: string]: ProcessEntry;
}

export type StartProcessState = RequestState<StartProcessResponse>;
export type CancelProcessState = RequestState<boolean>;
export type RestoreProcessState = RequestState<RestoreProcessResponse>;
export type CancelBullkProcessState = RequestState<boolean>;

export interface State {
    processesById: Processes;
    paginatedProcessesById: PaginatedProcesses;

    // TODO use RequestState
    loading: boolean;
    error: RequestError;

    startProcess: StartProcessState;
    cancelProcess: CancelProcessState;
    cancelBulkProcess: CancelBullkProcessState;
    restoreProcess: RestoreProcessState;

    ansible: AnsibleState;
    log: LogState;
    poll: PollState;
    history: HistoryState;
    attachments: AttachmentState;
    children: ChildrenState;
    events: EventsState;
}
