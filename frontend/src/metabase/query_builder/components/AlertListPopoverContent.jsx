import React, { Component } from "react";
import { connect } from "react-redux";
import { getQuestionAlerts } from "metabase/query_builder/selectors";
import { getUser } from "metabase/selectors/user";
import { unsubscribeFromAlert } from "metabase/alert/alert";
import Modal from "metabase/components/Modal";
import { CreateAlertModalContent, UpdateAlertModalContent } from "metabase/query_builder/components/AlertModals";
import _ from "underscore"

@connect((state) => ({ questionAlerts: getQuestionAlerts(state), user: getUser(state) }), null)
export class AlertListPopoverContent extends Component {
    state = {
        adding: false
    }

    onAdd = () => {
        this.props.setMenuFreeze(true)
        this.setState({ adding: true })
    }

    onEndAdding = () => {
        this.setState({ adding: false })
    }

    render() {
        const { questionAlerts, setMenuFreeze, user } = this.props;
        const { adding } = this.state

        // user's own alert should be shown first if it exists
        const sortedQuestionAlerts = _.sortBy(questionAlerts, (alert) => alert.creator.id !== user.id)

        return (
            <div className="p2" style={{ minWidth: 340 }}>
                <ul>
                    { Object.values(sortedQuestionAlerts).map((alert) =>
                        <AlertListItem alert={alert} setMenuFreeze={setMenuFreeze} />)
                    }
                    <li>
                        <a onClick={this.onAdd}>
                            Add new alert (this button isn't in the design)
                        </a>
                    </li>
                </ul>
                { adding && <Modal full onClose={this.onEndAdding}>
                    <CreateAlertModalContent onClose={this.onEndAdding} />
                </Modal> }
            </div>
        )
    }
}

@connect((state) => ({ user: getUser(state) }), { unsubscribeFromAlert })
export class AlertListItem extends Component {
    props: {
        alert: any,
        setMenuFreeze: (boolean) => void
    }

    state = {
        unsubscribed: false,
        editing: false
    }

    onUnsubscribe = async () => {
        await this.props.unsubscribeFromAlert(this.props.alert)
        this.setState({ unsubscribed: true })
    }

    onEdit = () => {
        this.props.setMenuFreeze(true)
        this.setState({ editing: true })
    }

    onEndEditing = () => {
        this.props.setMenuFreeze(false)
        this.setState({ editing: false })
    }

    render() {
        const { user, alert } = this.props
        const { editing, unsubscribed } = this.state

        const isAdmin = user.is_superuser
        const isCurrentUser = alert.creator.id === user.id

        if (unsubscribed) {
            return <li>Okay, you're unsubscribed</li>
        }

        return (
            <li>
                <AlertCreatorTitle alert={alert} user={user} />
                { !isAdmin && <a onClick={this.onUnsubscribe}>Unsubscribe</a> }
                { (isAdmin || isCurrentUser) && <a onClick={this.onEdit}>Edit</a> }
                <hr />

                { editing && <Modal full onClose={this.onEndEditing}>
                    <UpdateAlertModalContent alert={alert} onClose={this.onEndEditing} />
                </Modal> }
            </li>
        )
    }
}

export class AlertCreatorTitle extends Component {
    render () {
        const { alert, user } = this.props

        const isAdmin = user.is_superuser
        const isCurrentUser = alert.creator.id === user.id
        const creator = alert.creator.id === user.id ? "You" : alert.creator.first_name
        const text = (!isCurrentUser && !isAdmin)
            ? `You're receiving ${creator}'s alerts`
            : `${creator} set up an alert`

        return <h2>{text}</h2>
    }
}