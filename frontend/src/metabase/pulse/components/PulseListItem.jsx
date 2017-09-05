/* eslint "react/prop-types": "warn" */
import React, { Component } from "react";
import PropTypes from "prop-types";
import ReactDOM from "react-dom";
import { Link } from "react-router";

import Icon from "metabase/components/Icon";

import * as Urls from "metabase/lib/urls";
import PulseListChannel from "./PulseListChannel.jsx";

const CARD_DISPLAY_LIMIT = 5;

export default class PulseListItem extends Component {
    static propTypes = {
        pulse: PropTypes.object.isRequired,
        formInput: PropTypes.object.isRequired,
        user: PropTypes.object.isRequired,
        scrollTo: PropTypes.bool.isRequired,
        savePulse: PropTypes.func.isRequired
    };

    componentDidMount() {
        if (this.props.scrollTo) {
            const element = ReactDOM.findDOMNode(this.refs.pulseListItem);
            element.scrollIntoView(true);
        }
    }

    render() {
        const { pulse, formInput, user } = this.props;

        const filterIfTooManyCards = (card, index) => {
            if(pulse.cards.length < CARD_DISPLAY_LIMIT || index < CARD_DISPLAY_LIMIT) {
                return card
            }
            return false
        }

        return (
            <div ref="pulseListItem" className="bordered hover-parent hover--visibility transition-visibility rounded shadowed mb2 p4 bg-white">
                <div className="flex mb2">
                    <div>
                        <h2 className="mb1">{pulse.name}</h2>
                        <span>Created by {pulse.creator && pulse.creator.common_name}</span>
                    </div>
                    { !pulse.read_only &&
                        <div className="flex-align-right hover-child">
                            <Link to={`/pulse/${pulse.id}`} className="text-brand-hover">
                                <Icon name="pencil" />
                            </Link>
                        </div>
                    }
                </div>
                <ol>
                    { pulse.cards.filter(filterIfTooManyCards).map((card, index) =>
                        <li key={index} className="inline-block" style={{ marginRight: 4, marginBottom: 4 }}>
                            <Link to={Urls.question(card.id)} className="bg-slate-light block text-slate no-decoration rounded p1 text-bold">
                                {card.name}
                            </Link>
                        </li>
                    ).concat(
                        pulse.cards.length > CARD_DISPLAY_LIMIT && [<li className="bg-slate-light text-slate rounded p1 text-bold inline-block">+{ pulse.cards.length - 5 }</li>]
                    )}
                </ol>
                <ul>
                    {pulse.channels.filter(channel => channel.enabled).map(channel =>
                        <li key={channel.id} className="border-row-divider">
                            <PulseListChannel
                                pulse={pulse}
                                channel={channel}
                                channelSpec={formInput.channels && formInput.channels[channel.channel_type]}
                                user={user}
                                savePulse={this.props.savePulse}
                            />
                        </li>
                    )}
                </ul>
            </div>
        );
    }
}
