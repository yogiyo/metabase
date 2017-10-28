import React, { Component } from "react";
import { connect } from "react-redux";
import { getQuestionAlerts } from "metabase/query_builder/selectors";
import { getUser } from "metabase/selectors/user";
import { unsubscribeFromAlert } from "metabase/alert/alert";

@connect((state) => ({ questionAlerts: getQuestionAlerts(state) }), null)
export class AlertListPopoverContent extends Component {
    render() {
        const { questionAlerts } = this.props;

        return (
            <div className="p2" style={{ minWidth: 340 }}>
                <ul>
                    { Object.values(questionAlerts).map((alert) =>
                        <AlertListItem alert={alert} />)
                    }
                </ul>
            </div>
        )
    }
}

@connect((state) => ({ user: getUser(state) }), { unsubscribeFromAlert })
export class AlertListItem extends Component {
    state = {
        unsubscribed: false
    }

    onUnsubscribe = async () => {
        await this.props.unsubscribeFromAlert(this.props.alert)
        this.setState({ unsubscribed: true })
    }

    onEdit() {
        // wtf how to switch to edit dialog from here
    }

    render() {
        const { user, alert } = this.props
        const { unsubscribed } = this.state

        const isAdmin = alert.is_superuser
        const isCurrentUser = alert.creator.id === user.id

        if (unsubscribed) {
            return <li>Okay, you're unsubscribed</li>
        }

        return (
            <li>
                <AlertCreatorTitle alert={alert} user={user} />
                { !isAdmin && <a onClick={this.onUnsubscribe}>Unsubscribe</a> }
                { (isAdmin || isCurrentUser) && <a onClick={this.onEdit}>Edit</a> }
                {/*{ JSON.stringify(alert) }*/}
            </li>
        )
    }
}

export class AlertCreatorTitle extends Component {
    render () {
        const { alert, user } = this.props

        const isAdmin = alert.is_superuser
        const isCurrentUser = alert.creator.id === user.id
        const creator = alert.creator.id === user.id ? "You" : alert.creator.first_name
        const text = (!isCurrentUser && !isAdmin)
            ? `You're receiving ${creator}'s alerts`
            : `${creator} set up an alert`

        return <h2>{text}</h2>
    }
}