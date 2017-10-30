import {
    createSavedQuestion,
    createTestStore, useSharedAdminLogin,
    useSharedNormalLogin
} from "__support__/integrated_tests";
import {
    click, clickButton
} from "__support__/enzyme_utils"

import { mount } from "enzyme";
import { CardApi } from "metabase/services";
import Question from "metabase-lib/lib/Question";
import * as Urls from "metabase/lib/urls";
import { INITIALIZE_QB, QUERY_COMPLETED } from "metabase/query_builder/actions";
import QueryHeader from "metabase/query_builder/components/QueryHeader";
import EntityMenu from "metabase/components/EntityMenu";
import { delay } from "metabase/lib/promise";
import Icon from "metabase/components/Icon";
import {
    AlertEducationalScreen,
    AlertSettingToggle,
    CreateAlertModalContent,
    RawDataAlertTip
} from "metabase/query_builder/components/AlertModals";
import Button from "metabase/components/Button";
import { CREATE_ALERT, FETCH_ALERTS_FOR_QUESTION } from "metabase/alert/alert";

describe("Alerts", () => {
    let rawDataQuestion = null;
    let timeSeriesQuestion = null;
    let timeSeriesQuestionWithGoal = null;
    let progressBarQuestion = null;

    beforeAll(async () => {
        useSharedAdminLogin()

        rawDataQuestion = await createSavedQuestion(
            Question.create({databaseId: 1, tableId: 1, metadata: null})
                .setDisplayName("Just raw, untamed data")
        )

        timeSeriesQuestion = await createSavedQuestion(
            Question.create({databaseId: 1, tableId: 1, metadata: null})
                .query()
                .addAggregation(["count"])
                .addBreakout(["datetime-field", ["field-id", 1], "day"])
                .question()
                .setDisplay("line")
                .setDisplayName("Time series line")
        )

        timeSeriesQuestionWithGoal = await createSavedQuestion(
            Question.create({databaseId: 1, tableId: 1, metadata: null})
                .query()
                .addAggregation(["count"])
                .addBreakout(["datetime-field", ["field-id", 1], "day"])
                .question()
                .setDisplay("line")
                .setVisualizationSettings({ "graph.show_goal": true, "graph.goal_value": 10 })
                .setDisplayName("Time series line with goal")
        )

        progressBarQuestion = await createSavedQuestion(
            Question.create({databaseId: 1, tableId: 1, metadata: null})
                .query()
                .addAggregation(["count"])
                .question()
                .setDisplay("progress")
                .setVisualizationSettings({ "progress.goal": 50 })
                .setDisplayName("Progress bar question")
        )
    })

    afterAll(async () => {
        await CardApi.delete({cardId: rawDataQuestion.id()})
        await CardApi.delete({cardId: timeSeriesQuestion.id()})
        await CardApi.delete({cardId: timeSeriesQuestionWithGoal.id()})
        await CardApi.delete({cardId: progressBarQuestion.id()})
    })

    describe("alert creation", async () => {
        beforeAll(() => {
            useSharedNormalLogin()
        })

        it("should work for raw data questions", async () => {
            const store = await createTestStore()
            store.pushPath(Urls.question(rawDataQuestion.id()))
            const app = mount(store.getAppContainer());

            await store.waitForActions([INITIALIZE_QB, QUERY_COMPLETED, FETCH_ALERTS_FOR_QUESTION])

            const actionsMenu = app.find(QueryHeader).find(EntityMenu)
            click(actionsMenu.childAt(0))

            const alertsMenuItem = actionsMenu.find(Icon).filterWhere(i => i.prop("name") === "alert")
            click(alertsMenuItem)

            // not sore how to handle this first-time screen in tests properly
            const alertModal = app.find(QueryHeader).find(".test-modal")
            const educationalScreen = alertModal.find(AlertEducationalScreen)

            clickButton(educationalScreen.find(Button))

            expect(alertModal.find(AlertEducationalScreen).length).toBe(0)

            const creationScreen = alertModal.find(CreateAlertModalContent)
            expect(creationScreen.find(RawDataAlertTip).length).toBe(1)
            // no toggleable settings for raw data questions
            expect(creationScreen.find(AlertSettingToggle).length).toBe(0)

            click(creationScreen.find(".Button.Button--primary"))
            await store.waitForActions([CREATE_ALERT])
        })

        it("should work for timeseries questions with a set goal", async () => {
            const store = await createTestStore()
            store.pushPath(Urls.question(timeSeriesQuestionWithGoal.id()))
            const app = mount(store.getAppContainer());

            await store.waitForActions([INITIALIZE_QB, QUERY_COMPLETED, FETCH_ALERTS_FOR_QUESTION])
            await delay(500);

            const actionsMenu = app.find(QueryHeader).find(EntityMenu)
            click(actionsMenu.childAt(0))

            const alertsMenuItem = actionsMenu.find(Icon).filterWhere(i => i.prop("name") === "alert")
            click(alertsMenuItem)

            // not sore how to handle this first-time screen in tests properly
            const alertModal = app.find(QueryHeader).find(".test-modal")
            const educationalScreen = alertModal.find(AlertEducationalScreen)

            clickButton(educationalScreen.find(Button))

            expect(alertModal.find(AlertEducationalScreen).length).toBe(0)

            const creationScreen = alertModal.find(CreateAlertModalContent)
            expect(creationScreen.find(RawDataAlertTip).length).toBe(0)

            // no toggleable settings for raw data questions
            expect(creationScreen.find(AlertSettingToggle).length).toBe(2)

            click(creationScreen.find(".Button.Button--primary"))
            await store.waitForActions([CREATE_ALERT])
        })
    })
})