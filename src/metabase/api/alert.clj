(ns metabase.api.alert
  "/api/alert endpoints"
  (:require [compojure.core :refer [DELETE GET POST PUT]]
            [hiccup.core :refer [html]]
            [metabase
             [driver :as driver]
             [email :as email]
             [events :as events]
             [pulse :as p]
             [query-processor :as qp]
             [util :as u]]
            [metabase.api
             [common :as api]
             [pulse :as pulse-api]]
            [metabase.integrations.slack :as slack]
            [metabase.models
             [card :refer [Card]]
             [interface :as mi]
             [pulse :as pulse :refer [Pulse]]
             [pulse-channel :refer [channel-types]]]
            [metabase.pulse.render :as render]
            [metabase.util.schema :as su]
            [schema.core :as s]
            [toucan.db :as db])
  (:import java.io.ByteArrayInputStream
           java.util.TimeZone))

(api/defendpoint GET "/"
  "Fetch all `Alerts`"
  []
  (for [alert (pulse/retrieve-alerts)
        :let  [can-read?  (mi/can-read? alert)
               can-write? (mi/can-write? alert)]
        :when (or can-read?
                  can-write?)]
    (assoc alert :read_only (not can-write?))))

(api/defendpoint GET "/question/:id"
  [id]
  (for [alert (if api/*is-superuser?*
                (pulse/retrieve-alerts-for-card id)
                (pulse/retrieve-user-alerts-for-card id api/*current-user-id*))
        :let  [can-read?  (mi/can-read? alert)
               can-write? (mi/can-write? alert)]]
    (assoc alert :read_only (not can-write?))))

(def ^:private AlertConditions
  (s/enum "rows" "goal"))

(defn- only-alert-keys [request]
  (select-keys request [:name :alert_condition :alert_description :alert_first_only :alert_above_goal]))

(api/defendpoint POST "/"
  "Create a new `Alert`."
  [:as {{:keys [name alert_condition alert_description card channels alert_first_only alert_above_goal] :as req} :body}]
  {name              su/NonBlankString
   alert_condition   AlertConditions
   alert_description su/NonBlankString
   alert_first_only  s/Bool
   alert_above_goal  (s/maybe s/Bool)
   card              su/Map
   channels          (su/non-empty [su/Map])}
  (pulse-api/check-card-read-permissions [card])
  (api/check-500
   (-> req
       only-alert-keys
       (pulse/create-alert! api/*current-user-id* (u/get-id card) channels))))

(defn- recipient-ids [{:keys [channels] :as alert}]
  (reduce (fn [acc {:keys [channel_type recipients]}]
            (if (= :email channel_type)
              (into acc (map :id recipients))
              acc))
          #{} channels))

(defn- check-alert-update-permissions
  "Admin users can update all alerts. Non-admin users can update alerts that they created as long as they are still a
  recipient of that alert"
  [pulse-id]
  (when-not api/*is-superuser?*
    (let [{:keys [channels] :as alert} (pulse/retrieve-alert pulse-id)]
      (api/write-check alert)
      (api/check-403 (and (= api/*current-user-id* (:creator_id alert))
                          (contains? (recipient-ids alert) api/*current-user-id*))))))

(api/defendpoint PUT "/:id"
  "Update a `Alert` with ID."
  [id :as {{:keys [name alert_condition alert_description card channels alert_first_only alert_above_goal card channels] :as req} :body}]
  {name              su/NonBlankString
   alert_condition   AlertConditions
   alert_description su/NonBlankString
   alert_first_only  s/Bool
   alert_above_goal  (s/maybe s/Bool)
   card              su/Map
   channels          (su/non-empty [su/Map])}
  (check-alert-update-permissions id)
  (-> req
      only-alert-keys
      (assoc :id id :card (u/get-id card) :channels channels)
      pulse/update-alert!))

(api/defendpoint PUT "/:id/unsubscribe"
  [id]
  (assert (integer? id))
  (api/read-check Pulse id)
  (pulse/unsubscribe-from-alert id api/*current-user-id*)
  api/generic-204-no-content)

(api/defendpoint DELETE "/:id"
  [id]
  (api/let-404 [pulse (pulse/retrieve-alert id)]
    (api/check-403
     (or (= api/*current-user-id* (:creator_id pulse))
          api/*is-superuser?*))
    (db/delete! Pulse :id id)
    api/generic-204-no-content))

(api/define-routes)
