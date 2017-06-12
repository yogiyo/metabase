/* @flow */
import React, { Component } from "react";
import ReactDOM from "react-dom";

import Input from "metabase/components/Input.jsx";
import HeaderModal from "metabase/components/HeaderModal.jsx";
import TitleAndDescription from "metabase/components/TitleAndDescription.jsx";
import EditBar from "metabase/components/EditBar.jsx";
import { getScrollY } from "metabase/lib/dom";

import type SyntheticInputEvent from 'react/types'

type Props = {
    headerButtons: [],
    editingButtons: [],
    editingTitle: string,
    editingSubtitle: string,
    item: {
        creator: { common_name: string },
        name: string,
        description: string
    },
    objectType: string,
    isEditing: boolean,
    isEditingInfo: boolean,
    setItemAttributeFn: () => void,
    children: {},
    headerClassName: string,
    headerModalMessage: string,
    onHeaderModalCancel: () => void,
    onHeaderModalDone: () => void
}

type State = {
    headerHeight: number
}

export default class Header extends Component {
    static defaultProps = {
        headerButtons: [],
        editingTitle: "",
        editingSubtitle: "",
        editingButtons: [],
        headerClassName: "py1 lg-py2 xl-py3 wrapper"
    };

    props: Props
    state: State = {
        headerHeight: 0
    }

    componentDidMount() {
        this.updateHeaderHeight();
    }

    componentWillUpdate() {
        const modalIsOpen = !!this.props.headerModalMessage;
        if (modalIsOpen) {
            this.updateHeaderHeight()
        }
    }

    updateHeaderHeight() {
        if (!this.refs.header) return;

        const rect = ReactDOM.findDOMNode(this.refs.header).getBoundingClientRect();
        const headerHeight = rect.top + getScrollY();
        if (this.state.headerHeight !== headerHeight) {
            this.setState({ headerHeight });
        }
    }

    setItemAttribute (attribute: string, { target: SyntheticInputEvent }): void {
        this.props.setItemAttributeFn(attribute, target.value);
    }

    renderHeaderModal() {
        return (
            <HeaderModal
                isOpen={!!this.props.headerModalMessage}
                height={this.state.headerHeight}
                title={this.props.headerModalMessage}
                onDone={this.props.onHeaderModalDone}
                onCancel={this.props.onHeaderModalCancel}
            />
        );
    }

    renderTitleAndDescription = () => {
        if (this.props.isEditingInfo) {
            return (
                <div className="Header-title flex flex-column flex-full bordered rounded my1">
                    <Input
                        className="AdminInput text-bold border-bottom rounded-top h3"
                        type="text"
                        value={this.props.item.name}
                        onChange={this.setItemAttribute.bind(this, "name")}
                    />
                    <Input
                        className="AdminInput rounded-bottom h4"
                        type="text"
                        value={this.props.item.description}
                        onChange={this.setItemAttribute.bind(this, "description")}
                        placeholder="No description yet"
                    />
                </div>
            );
        } else {
            return (
                <TitleAndDescription
                    title={
                        this.props.item.id != null
                            ? this.props.item.name
                            : `New ${this.props.objectType}`
                    }
                    description={this.props.item.description}
                />
            );
        }

    }

    render() {
        const { headerButtons, item, isEditing, isEditingInfo } = this.props

        return (
            <div>
                { isEditing && (
                    <EditBar
                        title={this.props.editingTitle}
                        subtitle={this.props.editingSubtitle}
                        buttons={this.props.editingButtons}
                    />
                )}
                {this.renderHeaderModal()}
                <div
                    lassName={`QueryBuilder-section flex align-center ${this.props.headerClassName}`}
                    ref="header"
                >
                    <div className="Entity py3">
                        {this.renderTitleAndDescription()}

                        { item && item.creator && (
                            <div className="Header-attribution">
                                Asked by {item.creator.common_name}
                            </div>
                        )}

                    </div>

                    <ol className="flex align-center flex-align-right">
                        { headerButtons.map((section, sectionIndex) =>
                            section && section.length > 0 && (
                                <li key={sectionIndex} className="Header-buttonSection flex align-center">
                                    <ol>
                                        {section.map((button, buttonIndex) =>
                                            <li key={buttonIndex} cjassName="Header-button">
                                                {button}
                                            </li>
                                        )}
                                    </ol>
                                    }
                                </li>
                            )
                        )}
                    </ol>
                </div>
                {this.props.children}
            </div>
        );
    }
}
