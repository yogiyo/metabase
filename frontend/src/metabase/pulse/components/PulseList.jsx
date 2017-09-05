import React, { Component } from "react";

import PulseListItem from "./PulseListItem.jsx";
import WhatsAPulse from "./WhatsAPulse.jsx";
import SetupModal from "./SetupModal.jsx";

import LoadingAndErrorWrapper from "metabase/components/LoadingAndErrorWrapper.jsx";
import Modal from "metabase/components/Modal.jsx";

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
                <div className="mb2">
                    <div className="wrapper flex align-center py2">
                        <h2>Pulses</h2>
                        <a onClick={this.create} className="Button ml-auto">Create a pulse</a>
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
