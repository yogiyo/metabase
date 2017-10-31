import React, { Component } from "react";
import { connect } from "react-redux";
import { getQuestionAlerts } from "metabase/query_builder/selectors";
import { getUser } from "metabase/selectors/user";
import { deleteAlert, unsubscribeFromAlert } from "metabase/alert/alert";
import { AM_PM_OPTIONS, DAY_OF_WEEK_OPTIONS, HOUR_OPTIONS } from "metabase/components/SchedulePicker"
import Icon from "metabase/components/Icon";
import Modal from "metabase/components/Modal";
import { CreateAlertModalContent, UpdateAlertModalContent } from "metabase/query_builder/components/AlertModals";
import _ from "underscore"
import cx from "classnames";

@connect((state) => ({ questionAlerts: getQuestionAlerts(state), user: getUser(state) }), null)
export class AlertListPopoverContent extends Component {
    state = {
        adding: false,
        ownAlertRemovedAsNonAdmin: false
    }

    onAdd = () => {
        this.props.setMenuFreeze(true)
        this.setState({ adding: true })
    }

    onEndAdding = () => {
        this.props.setMenuFreeze(false)
        this.setState({ adding: false })
    }

    onRemovedOwnAlert = () => {
        this.setState( { ownAlertRemovedAsNonAdmin: true })
    }

    render() {
        const { questionAlerts, setMenuFreeze, user } = this.props;
        const { adding, ownAlertRemovedAsNonAdmin } = this.state

        // user's own alert should be shown first if it exists
        const sortedQuestionAlerts = _.sortBy(questionAlerts, (alert) => alert.creator.id !== user.id)
        const hasOwnAlert = _.any(questionAlerts, (alert) => alert.creator.id === user.id)

        return (
            <div style={{ minWidth: 340 }}>
                <ul>
                    { ownAlertRemovedAsNonAdmin && <UnsubscribedListItem /> }
                    { Object.values(sortedQuestionAlerts).map((alert) =>
                        <AlertListItem
                            alert={alert}
                            setMenuFreeze={setMenuFreeze}
                            onRemovedOwnAlert={this.onRemovedOwnAlert}
                        />)
                    }
                </ul>
                { !hasOwnAlert &&
                    <div className="border-top p2">
                        <a className="link" onClick={this.onAdd}>
                            <Icon name="add" /> {t`Set up your own alert`}
                        </a>
                    </div>
                }
                { adding && <Modal full onClose={this.onEndAdding}>
                    <CreateAlertModalContent onClose={this.onEndAdding} />
                </Modal> }
            </div>
        )
    }
}

@connect((state) => ({ user: getUser(state) }), { unsubscribeFromAlert, deleteAlert })
export class AlertListItem extends Component {
    props: {
        alert: any,
        user: any,
        setMenuFreeze: (boolean) => void,
        onRemovedOwnAlert: (boolean) => void
    }

    state = {
        unsubscribed: false,
        editing: false
    }

    onUnsubscribe = async () => {
        const { user, alert, deleteAlert, onRemovedOwnAlert } = this.props

        const isAdmin = user.is_superuser
        const isCurrentUser = alert.creator.id === user.id

        if (isCurrentUser && !isAdmin) {
            // for non-admins, unsubscribing from your own alert means removing it
            await deleteAlert(alert.id)
            // it gets cleared from the list immediately so we have to add the "unsubscribed"
            // list item in the parent container
            onRemovedOwnAlert()
        } else {
            await this.props.unsubscribeFromAlert(alert)
            this.setState({ unsubscribed: true })
        }
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

        const emailChannel = alert.channels.find((c) => c.channel_type === "email")
        const emailEnabled = emailChannel && emailChannel.enabled
        const slackChannel = alert.channels.find((c) => c.channel_type === "slack")
        const slackEnabled = slackChannel && slackChannel.enabled

        if (unsubscribed) {
            return <UnsubscribedListItem />
        }

        return (
            <li className={cx("flex p2 text-grey-4", { "bg-grey-0": isCurrentUser && !isAdmin })}>
                <Icon name="alert" size="22" />
                <div className="full ml2">
                    <div className="flex align-center">
                        <div>
                            <AlertCreatorTitle alert={alert} user={user} />
                        </div>
                        <div className="ml-auto text-bold text-small">
                            { !isAdmin && <a className="link" onClick={this.onUnsubscribe}>{jt`Unsubscribe`}</a> }
                            { (isAdmin || isCurrentUser) && <a className="link ml2" onClick={this.onEdit}>{jt`Edit`}</a> }
                        </div>
                    </div>

                    {
                        // To-do: @kdoh wants to look into overall alignment
                    }
                    <ul className="flex">
                        <li className="flex align-center">
                            <Icon name="clock" /> <AlertScheduleText schedule={alert.channels[0]} verbose={!isAdmin} />
                        </li>
                        { isAdmin && emailEnabled &&
                            <li className="ml1 flex align-center">
                                <Icon name="mail" />
                                { emailChannel.recipients.length }
                            </li>
                        }
                        { isAdmin && slackEnabled &&
                            <li className="ml1 flex align-center">
                                <Icon name="slack" size={16} />
                                { slackChannel.details.channel.replace("#","") }
                            </li>
                        }
                    </ul>
                </div>

                { editing && <Modal full onClose={this.onEndEditing}>
                    <UpdateAlertModalContent alert={alert} onClose={this.onEndEditing} />
                </Modal> }
            </li>
        )
    }
}

export const UnsubscribedListItem = () =>
    <li>{jt`Okay, you're unsubscribed`}<hr /></li>

export class AlertScheduleText extends Component {
    getScheduleText = () => {
        const { schedule, verbose } = this.props
        const scheduleType = schedule.schedule_type

        // these are pretty much copy-pasted from SchedulePicker
        if (scheduleType === "hourly") {
            return verbose ? "hourly" : "Hourly";
        } else if (scheduleType === "daily") {
            const hourOfDay = schedule.schedule_hour;
            const hour = _.find(HOUR_OPTIONS, (opt) => opt.value === hourOfDay % 12).name;
            const amPm = _.find(AM_PM_OPTIONS, (opt) => opt.value === (hourOfDay >= 12 ? 1 : 0)).name;

            return `${verbose ? "daily at " : "Daily, "} ${hour} ${amPm}`
        } else if (scheduleType === "weekly") {
            console.log(schedule)
            const hourOfDay = schedule.schedule_hour;
            const day = _.find(DAY_OF_WEEK_OPTIONS, (o) => o.value === schedule.schedule_day).name
            const hour = _.find(HOUR_OPTIONS, (opt) => opt.value === (hourOfDay % 12)).name;
            const amPm = _.find(AM_PM_OPTIONS, (opt) => opt.value === (hourOfDay >= 12 ? 1 : 0)).name;

            if (verbose) {
                return `weekly on ${day}s at ${hour} ${amPm}`
            } else {
                // omit the minute part of time
                return `${day}s, ${hour.substr(0, hour.indexOf(':'))} ${amPm}`
            }
        }
    }

    render() {
        const { verbose } = this.props

        const scheduleText = this.getScheduleText()

        if (verbose) {
            return <span>Checking <b>{ scheduleText }</b></span>
        } else {
            return <span>{ scheduleText }</span>
        }
    }
}

export class AlertCreatorTitle extends Component {
    render () {
        const { alert, user } = this.props

        const isAdmin = user.is_superuser
        const isCurrentUser = alert.creator.id === user.id
        const creator = alert.creator.id === user.id ? "You" : alert.creator.first_name
        const text = (!isCurrentUser && !isAdmin)
            ? t`You're receiving ${creator}'s alerts`
            : t`${creator} set up an alert`

        return <h3 className="text-dark">{text}</h3>
    }
}
