import React, { Component } from "react";

import PulseListItem from "./PulseListItem.jsx";
import WhatsAPulse from "./WhatsAPulse.jsx";
import SetupModal from "./SetupModal.jsx";

import Icon from "metabase/components/Icon";
import LoadingAndErrorWrapper from "metabase/components/LoadingAndErrorWrapper";
import Modal from "metabase/components/Modal";
import Tooltip from "metabase/components/Tooltip";

import _ from "underscore";

export default class PulseList extends Component {

    state = {
        showSetupModal: false
    };

    componentDidMount() {
        this.props.fetchPulses();
        this.props.fetchPulseFormInput();
    }

    create = () => {
        let hasConfiguredChannel = !this.props.formInput.channels || _.some(Object.values(this.props.formInput.channels), (c) => c.configured);
        if (hasConfiguredChannel) {
            this.props.onChangeLocation("/pulse/create");
        } else {
            this.setState({ showSetupModal: true });
        }
    }

    render() {
        const { pulses, user } = this.props;
        return (
            <div className="bg-slate-extra-light full-height">
                <div>
                    <div className="wrapper flex align-center py3">
                        <h2>Pulses</h2>
                        <a onClick={this.create} className="ml-auto text-brand-hover">
                            <Tooltip tooltip="Create a pulse">
                                <Icon name="add" size={20} />
                            </Tooltip>
                        </a>
                    </div>
                </div>
                <div className="wrapper full">
                    <LoadingAndErrorWrapper loading={!pulses}>
                        { () => pulses.length > 0 ?
                            <ul className="Grid Grid--1of3 Grid--gutters Grid--fit full">
                                {pulses.slice().sort((a,b) => b.created_at - a.created_at).map(pulse =>
                                    <li className="Grid-cell" key={pulse.id}>
                                        <PulseListItem
                                            scrollTo={pulse.id === this.props.pulseId}
                                            pulse={pulse}
                                            user={user}
                                            formInput={this.props.formInput}
                                            savePulse={this.props.savePulse}
                                        />
                                    </li>
                                )}
                            </ul>
                        :
                            <div className="mt4 ml-auto mr-auto">
                                <WhatsAPulse
                                    button={<a onClick={this.create} className="Button Button--primary">Create a pulse</a>}
                                />
                            </div>
                        }
                    </LoadingAndErrorWrapper>
                </div>
                <Modal isOpen={this.state.showSetupModal}>
                    <SetupModal
                        user={user}
                        onClose={() => this.setState({ showSetupModal: false })}
                        onChangeLocation={this.props.onChangeLocation}
                    />
                </Modal>
            </div>
        );
    }
}
