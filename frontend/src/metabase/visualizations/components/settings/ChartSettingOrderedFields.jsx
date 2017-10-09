import React, { Component } from "react";

import CheckBox from "metabase/components/CheckBox.jsx";
import Icon from "metabase/components/Icon.jsx";
import {
    SortableContainer,
    SortableElement,
    SortableHandle,
    arrayMove
} from "react-sortable-hoc";

import cx from "classnames";

const FieldListHandle = SortableHandle(() =>
<Icon
    className="flex-align-right text-grey-2 mr1 cursor-pointer"
    name="grabber"
    width={14}
    height={14}
/>
)

const FieldListItem = SortableElement(({
    item,
    index,
    columnNames,
    setEnabled
}) => (
    <li
        className={cx("flex align-center p1", {
            "text-grey-2": !item.enabled
        })}
    >
        <CheckBox
            checked={item.enabled}
            onChange={e => setEnabled(index, e.target.checked)}
        />
        <span className="ml1 h4">
            {columnNames[item.name]}
        </span>
        <FieldListHandle />
    </li>
));

const FieldListContainer = SortableContainer(({ items, columnNames }) => {
    return (
        <ul>
            {items.map((item, index) => (
                <FieldListItem
                    key={`item-${index}`}
                    index={index}
                    item={item}
                    columnNames={columnNames}
                />
            ))}
        </ul>
    );
});

export default class ChartSettingOrderedFields extends Component {
    constructor(props) {
        super(props);
        this.state = {
            items: [...this.props.value]
        };
    }

    componentWillReceiveProps(nextProps) {
        this.setState({
            items: [...nextProps.value]
        });
    }
    onSortEnd = ({ oldIndex, newIndex }) => {
        this.setState({
            items: arrayMove(this.state.items, oldIndex, newIndex)
        }, () =>
            this.props.onChange(this.state.items)
        );
    };
    render() {
        const { columnNames } = this.props;
        return (
            <FieldListContainer
                items={this.state.items}
                onSortEnd={this.onSortEnd}
                columnNames={columnNames}
                useDragHandle
            />
        );
    }
}
