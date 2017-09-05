import React, { Component } from "react";
import ReactDOM from "react-dom";

import cx from "classnames";

export default class PulseEditName extends Component {
    state = {
        valid: true
    };

    setName = (event) => {
        const { pulse, setPulse } = this.props;
        setPulse({ ...pulse, name: event.target.value });
    }

    validate = () => {
        this.setState({ valid: !!ReactDOM.findDOMNode(this.refs.name).value });
    }

    render() {
        const { pulse } = this.props;
        return (
            <div className="py1">
                <h2>Name your pulse</h2>
                <p className="mt1 h4 text-grey-3">Give your pulse a name to help others understand what it's about.</p>
                <div className="my3">
                    <input
                        ref="name"
                        className={cx("input text-bold", { "border-error": !this.state.valid })}
                        style={{"width":"400px"}}
                        value={pulse.name || ""}
                        onChange={this.setName}
                        onBlur={this.refs.name && this.validate}
                        placeholder="Important metrics"
                        autoFocus
                    />
                </div>
            </div>
        );
    }
}
