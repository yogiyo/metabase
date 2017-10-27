import React, { Component } from "react";
import { connect } from "react-redux";
import { getQuestionAlerts } from "metabase/query_builder/selectors";

@connect((state) => ({ questionAlerts: getQuestionAlerts(state) }), null)
export class AlertListPopoverContent extends Component {
    render() {
        const { questionAlerts } = this.props;

        return (
            <div className="p2" style={{ minWidth: 340 }}>
                <ul>
                    { Object.values(questionAlerts).map((alert) => <AlertListItem alert={alert} />) }
                </ul>
            </div>
        )
    }
}

export class AlertListItem extends Component {
    render() {
        const { alert } = this.props
        return (
            <li>
                <h2>You set up an alert</h2>
                { JSON.stringify(alert) }
            </li>
        )
    }
}