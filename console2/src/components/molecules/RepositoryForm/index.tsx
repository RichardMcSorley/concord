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

import { InjectedFormikProps, withFormik } from 'formik';
import * as React from 'react';
import { Button, Divider, Form, Popup, Segment } from 'semantic-ui-react';
import { Error } from 'tslint/lib/error';

import { ConcordId, ConcordKey } from '../../../api/common';
import { isRepositoryExists } from '../../../api/service/console';
import { notEmpty } from '../../../utils';
import { repository as validation, repositoryAlreadyExistsError } from '../../../validation';
import { FormikCheckbox, FormikDropdown, FormikInput } from '../../atoms';
import { SecretDropdown } from '../../organisms';

export enum RepositorySourceType {
    BRANCH_OR_TAG = 'branchOrTag',
    COMMIT_ID = 'commitId'
}

interface FormValues {
    id?: ConcordId;
    name: string;
    url: string;
    sourceType: RepositorySourceType;
    branch?: string;
    commitId?: string;
    path?: string;
    withSecret?: boolean;
    secretId?: string;
}

export type RepositoryFormValues = FormValues;

interface Props {
    orgName: ConcordKey;
    projectName: ConcordKey;
    initial: FormValues;
    submitting: boolean;
    editMode?: boolean;
    onSubmit: (values: FormValues) => void;
    testRepository: (values: FormValues) => Promise<void>;
}

interface State {
    testRunning: boolean;
    testSuccess: boolean;
    testError?: string;
    testWarning?: string;
}

const sourceOptions = [
    {
        text: 'Branch/tag',
        value: RepositorySourceType.BRANCH_OR_TAG
    },
    {
        text: 'Commit ID',
        value: RepositorySourceType.COMMIT_ID
    }
];

const sanitize = (data: FormValues): FormValues => {
    const v = { ...data };

    if (v.path === '') {
        v.path = undefined;
    }

    if (v.branch === '') {
        v.branch = undefined;
    }

    if (v.commitId === '') {
        v.commitId = undefined;
    }

    if (v.withSecret === false) {
        v.secretId = undefined;
    }

    return v;
};

class RepositoryForm extends React.Component<InjectedFormikProps<Props, FormValues>, State> {
    constructor(props: InjectedFormikProps<Props, FormValues>) {
        super(props);
        this.state = { testRunning: false, testSuccess: false };
    }

    handleTestConnection() {
        const { values, testRepository } = this.props;
        this.setState({ testRunning: true, testSuccess: false, testError: '', testWarning: '' });

        testRepository(sanitize(values))
            .then(() => {
                this.setState({
                    testSuccess: true,
                    testRunning: false
                });
            })
            .catch((e) => {
                this.setState({
                    testSuccess: false,
                    testRunning: false,
                    testError: e.details && e.level !== 'WARN' ? e.details : e.message,
                    testWarning: e.level === 'WARN' ? e.details : ''
                });
            });
    }

    render() {
        const {
            orgName,
            handleSubmit,
            values,
            errors,
            dirty,
            editMode = false,
            isValid
        } = this.props;

        const hasErrors = notEmpty(errors);
        const testConnectionDisabled = dirty && (!isValid || hasErrors);

        return (
            <>
                <Form onSubmit={handleSubmit}>
                    <FormikInput
                        name="name"
                        label="Name"
                        placeholder="Repository name"
                        required={true}
                    />

                    <Segment>
                        <Popup
                            trigger={
                                <FormikCheckbox
                                    name="withSecret"
                                    label="Custom authentication"
                                    toggle={true}
                                    inline={true}
                                />
                            }>
                            <Popup.Content>
                                Personal repositories require additional authentication - a SSH key
                                or a username/password pair.
                            </Popup.Content>
                        </Popup>

                        {values.withSecret && (
                            <SecretDropdown
                                orgName={orgName}
                                name="secretId"
                                label="Credentials"
                                required={false}
                                fluid={true}
                                disabled={!values.withSecret}
                            />
                        )}

                        <FormikInput name="url" label="URL" placeholder="Git URL" required={true} />
                    </Segment>

                    <Form.Group widths="equal">
                        <FormikDropdown
                            name="sourceType"
                            label="Source"
                            selection={true}
                            options={sourceOptions}
                        />

                        {values.sourceType === RepositorySourceType.BRANCH_OR_TAG && (
                            <FormikInput name="branch" label="Branch/Tag" fluid={true} />
                        )}

                        {values.sourceType === RepositorySourceType.COMMIT_ID && (
                            <FormikInput name="commitId" label="Commit ID" fluid={true} />
                        )}
                    </Form.Group>

                    <Popup
                        trigger={
                            <FormikInput name="path" label="Path" placeholder="Repository path" />
                        }>
                        <Popup.Content>
                            (Optional) Path in the repository that will be used as the root
                            directory.
                        </Popup.Content>
                    </Popup>

                    <Divider />

                    <Button
                        primary={true}
                        type="submit"
                        disabled={!dirty || hasErrors || this.state.testRunning}>
                        {editMode ? 'Save' : 'Add'}
                    </Button>

                    <Popup
                        trigger={
                            <Button
                                basic={true}
                                positive={this.state.testSuccess}
                                negative={!!this.state.testError}
                                floated="right"
                                loading={this.state.testRunning}
                                disabled={testConnectionDisabled}
                                onClick={(ev) => {
                                    ev.preventDefault();
                                    this.handleTestConnection();
                                }}>
                                Test connection
                            </Button>
                        }
                        open={
                            (!!this.state.testError || !!this.state.testWarning) &&
                            !this.props.isSubmitting
                        }
                        wide={true}>
                        {!!this.state.testWarning && (
                            <Popup.Content>
                                <p style={{ color: 'orange' }}>Warning: {this.state.testWarning}</p>
                            </Popup.Content>
                        )}
                        {!!this.state.testError && (
                            <Popup.Content>
                                <p style={{ color: 'red' }}>Error: {this.state.testError}</p>
                            </Popup.Content>
                        )}
                    </Popup>
                </Form>
            </>
        );
    }
}

const validator = async (values: FormValues, props: Props) => {
    let e;

    e = validation.name(values.name);
    if (e) {
        throw { name: e };
    }

    if (values.name !== props.initial.name) {
        const exists = await isRepositoryExists(props.orgName, props.projectName, values.name);
        if (exists) {
            throw { name: repositoryAlreadyExistsError(values.name) };
        }
    }

    e = validation.url(values.url);
    if (e) {
        throw { url: e };
    }

    switch (values.sourceType) {
        case RepositorySourceType.BRANCH_OR_TAG:
            e = validation.branch(values.branch);
            if (e) {
                throw { branch: e };
            }
            break;
        case RepositorySourceType.COMMIT_ID:
            e = validation.commitId(values.commitId);
            if (e) {
                throw { commitId: e };
            }
            break;
        default:
            throw new Error(`Unknown repository source type: ${values.sourceType}`);
    }

    e = validation.path(values.path);
    if (e) {
        throw { path: e };
    }

    if (!values.withSecret) {
        if (!values.url.startsWith('https://')) {
            throw {
                url:
                    "Invalid repository URL: must begin with 'https://'. SSH repository URLs require additional credentials to be specified."
            };
        }
    } else {
        e = validation.secretId(values.secretId);
        if (e) {
            throw { secret: e };
        }
    }

    return {};
};

export default withFormik<Props, FormValues>({
    handleSubmit: (values, bag) => {
        bag.props.onSubmit(sanitize(values));
    },
    mapPropsToValues: (props) => ({
        ...props.initial,
        withSecret: !!props.initial.secretId
    }),
    validate: validator,
    enableReinitialize: true
})(RepositoryForm);
