/// code to "apply" chart tooltips. (How does one apply a tooltip?)

import _ from "underscore";
import d3 from "d3";

import { formatValue } from "metabase/lib/formatting";
import type { ClickObject } from "metabase/meta/types/Visualization"

import { isNormalized, isStacked } from "./renderer_utils";
import { determineSeriesIndexFromElement } from "./tooltip";
import { getFriendlyName } from "./utils";

function getHoverDataForScatterChart(d, cols) {
    if (d.key._origin) {
        return d.key._origin.row.map((value, index) => {
            const col = d.key._origin.cols[index];
            return { key: getFriendlyName(col), value: value, col };
        });
    }
    return d.key.map((value, index) => (
        { key: getFriendlyName(cols[index]), value: value, col: cols[index] }
    ));
}

function getHoverDataForLineAreaBarChart(d, i, thisSeries, cols, isNormalized) {
    const data = [
        {
            key: getFriendlyName(cols[0]),
            value: d.data.key,
            col: cols[0]
        },
        {
            key: getFriendlyName(cols[1]),
            value: isNormalized
                 ? `${formatValue(d.data.value) * 100}%`
                 : d.data.value,
            col: cols[1]
        }
    ];

    // now add entries to the tooltip for columns that aren't the X or Y axis. These aren't in the normal `cols` array,
    // which is just the cols used in the graph axes; look in `_rawCols` for any other columns. If we find them, add them
    // at the end of the `data` array
    const rawCols = thisSeries.data._rawCols;
    const thisRow = thisSeries.data.rows[i]._origin.row; // wtf?
    for (let colIndex = 0; colIndex < rawCols.length; colIndex++) {
        const col = rawCols[colIndex];
        if (col === cols[0] || col === cols[1]) continue;
        data.push({
            key: getFriendlyName(col),
            value: thisRow[colIndex],
            col: col
        });
    }

    return data;
}

function setHoverFunctions(chart, series, isStacked, isNormalized, onHoverChange) {
    let [{ data: { cols } }] = series;
    chart.selectAll(".bar, .dot, .area, .line, .bubble")
         .on("mousemove", function(d, i) {
             const seriesIndex       = determineSeriesIndexFromElement(this, isStacked);
             const thisSeries        = series[seriesIndex];
             const card              = thisSeries.card;
             const isSingleSeriesBar = this.classList.contains("bar") && series.length === 1;
             const isArea            = this.classList.contains("area");
             const isScatter         = Array.isArray(d.key);

             let data = [];
             if (isScatter) {
                 data = getHoverDataForScatterChart(d, cols);
             }
             // line, area, bar
             else if (d.data) {
                 if (!isSingleSeriesBar) {
                     cols = thisSeries.data.cols;
                 }
                 // !!!! for some reason i starts at 1 instead of 0 !!!!
                 data = getHoverDataForLineAreaBarChart(d, i - 1, thisSeries, cols, isNormalized);
             }

             // not sure what we're doing here or why
             if (data && series.length > 1) {
                 if (card._breakoutColumn) {
                     data.unshift({
                         key: getFriendlyName(card._breakoutColumn),
                         value: card._breakoutValue,
                         col: card._breakoutColumn
                     });
                 }
             }

             // remove any duplicate columns
             data = _.uniq(data, (d) => d.col);

             onHoverChange({
                 // for single series bar charts, fade the series and highlght the hovered element with CSS
                 index: isSingleSeriesBar ? -1 : seriesIndex,
                 // for area charts, use the mouse location rather than the DOM element
                 element: isArea ? null : this,
                 event: isArea ? d3.event : null,
                 data: data.length > 0 ? data : null,
             });
         })
         .on("mouseleave", function() {
             onHoverChange(null);
         });
}

function setClickFunctions(chart, series, isStacked, isNormalized, isScalarSeries, onVisualizationClick) {
    let [{ data: { cols } }] = series;
    const onClick = function(d) {
        const seriesIndex       = determineSeriesIndexFromElement(this, isStacked);
        const thisSeries        = series[seriesIndex];
        const card              = thisSeries.card;
        const isSingleSeriesBar = this.classList.contains("bar") && series.length === 1;

        let clicked: ?ClickObject;
        if (Array.isArray(d.key)) { // scatter
            clicked = {
                value: d.key[2],
                column: cols[2],
                dimensions: [
                    { value: d.key[0], column: cols[0] },
                    { value: d.key[1], column: cols[1] }
                ].filter(({ column }) =>
                    // don't include aggregations since we can't filter on them
                    column.source !== "aggregation"
                ),
                origin: d.key._origin
            }
        } else if (isScalarSeries) {
            // special case for multi-series scalar series, which should be treated as scalars
            clicked = {
                value: d.data.value,
                column: thisSeries.data.cols[1]
            };
        } else if (d.data) { // line, area, bar
            if (!isSingleSeriesBar) {
                cols = thisSeries.data.cols;
            }
            clicked = {
                value: d.data.value,
                column: cols[1],
                dimensions: [
                    { value: d.data.key, column: cols[0] }
                ]
            }
        } else {
            clicked = {
                dimensions: []
            };
        }

        // handle multiseries
        if (clicked && series.length > 1) {
            if (card._breakoutColumn) {
                // $FlowFixMe
                clicked.dimensions.push({
                    value: card._breakoutValue,
                    column: card._breakoutColumn
                });
            }
        }

        if (card._seriesIndex != null) {
            // $FlowFixMe
            clicked.seriesIndex = card._seriesIndex;
        }

        if (clicked) {
            const isLine = this.classList.contains("dot");
            onVisualizationClick({
                ...clicked,
                element: isLine ? this : null,
                event: isLine ? null : d3.event,
            });
        }
    }

    // for some reason interaction with brush requires we use click for .dot and .bubble but mousedown for bar
    chart.selectAll(".dot, .bubble")
         .style({ "cursor": "pointer" })
         .on("click", onClick);
    chart.selectAll(".bar")
         .style({ "cursor": "pointer" })
         .on("mousedown", onClick);
}

function applyChartTooltips(chart, series, isStacked, isNormalized, isScalarSeries, onHoverChange, onVisualizationClick) {
    chart.on("renderlet.tooltips", function(chart) {
        chart.selectAll("title").remove();

        if (onHoverChange)        setHoverFunctions(chart, series, isStacked, isNormalized, onHoverChange);
        if (onVisualizationClick) setClickFunctions(chart, series, isStacked, isNormalized, isScalarSeries, onVisualizationClick);
    });
}


export function setupTooltips({ settings, series, isScalarSeries, onHoverChange, onVisualizationClick }, datas, parent, { isBrushing }) {
    applyChartTooltips(parent, series, isStacked(settings, datas), isNormalized(settings, datas), isScalarSeries, (hovered) => {
        // disable tooltips while brushing
        if (onHoverChange && !isBrushing()) {
            // disable tooltips on lines
            if (hovered && hovered.element && hovered.element.classList.contains("line")) {
                delete hovered.element;
            }
            onHoverChange(hovered);
        }
    }, onVisualizationClick);
}
