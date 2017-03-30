import React, {Component} from "react";
import {connect} from "react-redux";
import {Container, Loader} from "semantic-ui-react";
import {Link} from "react-router";
import * as actions from "./actions";
import {getProcessWizardState} from "../../reducers";
import {getError} from "./reducers";
import ErrorMessage from "../../components/ErrorMessage";
import {getProcessPath} from "../../routes";

class VisibleProcessWizard extends Component {

    componentDidMount() {
        const {startFn, processInstanceId} = this.props;
        startFn(processInstanceId);
    }

    render() {
        const {error, processInstanceId} = this.props;
        if (error) {
            return <ErrorMessage message={error}/>;
        }
        return <div>
            <Container textAlign="center">
                Please wait until the current process completes or <Link to={getProcessPath(processInstanceId)}>click
                here</Link> to check the status.
            </Container>
            <div>
                <Loader active/>
            </div>
        </div>;
    }
}

const mapStateToProps = (state, {params}) => ({
    processInstanceId: params.processId,
    error: getError(getProcessWizardState(state))
});

const mapDispatchToProps = (dispatch) => ({
    startFn: (processInstanceId) => dispatch(actions.showNextForm(processInstanceId))
});

export default connect(mapStateToProps, mapDispatchToProps)(VisibleProcessWizard);
