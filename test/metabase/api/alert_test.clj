(ns metabase.api.alert-test
  (:require [expectations :refer :all]
            [metabase
             [http-client :as http]
             [middleware :as middleware]]
            [metabase.models
             [card :refer [Card]]
             [pulse :as pulse :refer [Pulse]]]
            [metabase.test.data.users :refer :all]
            [metabase.test.mock.util :refer [pulse-channel-defaults]]
            [metabase.test.util :as tu]
            [toucan.db :as db]
            [toucan.util.test :as tt]))


(defn- user-details [user]
  (select-keys user [:email :first_name :last_login :is_qbnewb :is_superuser :id :last_name :date_joined :common_name]))

(defn- pulse-card-details [card]
  (-> (select-keys card [:id :name :description :display])
      (update :display name)))

(defn- pulse-channel-details [channel]
  (select-keys channel [:schedule_type :schedule_details :channel_type :updated_at :details :pulse_id :id :enabled :created_at]))

(defn- pulse-details [pulse]
  (tu/match-$ pulse
    {:id                $
     :name              $
     :created_at        $
     :updated_at        $
     :creator_id        $
     :alert_description $
     :alert_condition   $
     :alert_above_goal  nil
     :alert_first_only  $
     :creator           (user-details (db/select-one 'User :id (:creator_id pulse)))
     :cards             (map pulse-card-details (:cards pulse))
     :channels          (map pulse-channel-details (:channels pulse))
     :skip_if_empty     $}))

(defn- pulse-response [{:keys [created_at updated_at], :as pulse}]
  (-> pulse
      (dissoc :id)
      (assoc :created_at (not (nil? created_at))
             :updated_at (not (nil? updated_at)))))



;; ## /api/alert/* AUTHENTICATION Tests
;; We assume that all endpoints for a given context are enforced by the same middleware, so we don't run the same
;; authentication test on every single individual endpoint

(expect (get middleware/response-unauthentic :body) (http/client :get 401 "alert"))
(expect (get middleware/response-unauthentic :body) (http/client :put 401 "alert/13"))


;; ## POST /api/alert

(expect
  {:errors {:name "value must be a non-blank string."}}
  ((user->client :rasta) :post 400 "alert" {}))

(expect
  {:errors {:alert_condition "value must be one of: `goal`, `rows`."}}
  ((user->client :rasta) :post 400 "alert" {:name "abc"}))

(expect
  {:errors {:alert_condition "value must be one of: `goal`, `rows`."}}
  ((user->client :rasta) :post 400 "alert" {:name            "abc"
                                            :alert_condition "not rows"
                                            :card            "foobar"}))

(expect
  {:errors {:alert_description "value must be a non-blank string."}}
  ((user->client :rasta) :post 400 "alert" {:name            "abc"
                                            :alert_condition "rows"}))

(expect
  {:errors {:alert_first_only "value must be a boolean."}}
  ((user->client :rasta) :post 400 "alert" {:name              "abc"
                                            :alert_condition   "rows"
                                            :alert_description "foo"}))

(expect
  {:errors {:card "value must be a map."}}
  ((user->client :rasta) :post 400 "alert" {:name              "abc"
                                            :alert_condition   "rows"
                                            :alert_description "foo"
                                            :alert_first_only  false}))

(expect
  {:errors {:channels "value must be an array. Each value must be a map. The array cannot be empty."}}
  ((user->client :rasta) :post 400 "alert" {:name              "abc"
                                            :alert_condition   "rows"
                                            :alert_description "foo"
                                            :alert_first_only  false
                                            :card              {:id 100}}))

(expect
  {:errors {:channels "value must be an array. Each value must be a map. The array cannot be empty."}}
  ((user->client :rasta) :post 400 "alert" {:name              "abc"
                                            :alert_condition   "rows"
                                            :alert_description "foo"
                                            :alert_first_only  false
                                            :card              {:id 100}
                                            :channels          "foobar"}))

(expect
  {:errors {:channels "value must be an array. Each value must be a map. The array cannot be empty."}}
  ((user->client :rasta) :post 400 "alert" {:name              "abc"
                                            :alert_condition   "rows"
                                            :alert_description "foo"
                                            :alert_first_only  false
                                            :card              {:id 100}
                                            :channels          ["abc"]}))

(defn- remove-extra-channels-fields [channels]
  (for [channel channels]
    (dissoc channel :id :pulse_id :created_at :updated_at)))

(tt/expect-with-temp [Card [card1]]
  {:name              "A Pulse"
   :creator_id        (user->id :rasta)
   :creator           (user-details (fetch-user :rasta))
   :created_at        true
   :updated_at        true
   :card              (pulse-card-details card1)
   :alert_description "foo"
   :alert_condition   "rows"
   :alert_first_only  false
   :alert_above_goal  nil
   :channels          [(merge pulse-channel-defaults
                              {:channel_type  "email"
                               :schedule_type "daily"
                               :schedule_hour 12
                               :recipients    []})]
   :skip_if_empty     true}
  (-> (pulse-response ((user->client :rasta) :post 200 "alert" {:name              "A Pulse"
                                                                :card              {:id (:id card1)}
                                                                :alert_description "foo"
                                                                :alert_condition   "rows"
                                                                :alert_first_only  false
                                                                :alert_above_goal  nil
                                                                :channels          [{:enabled       true
                                                                                     :channel_type  "email"
                                                                                     :schedule_type "daily"
                                                                                     :schedule_hour 12
                                                                                     :schedule_day  nil
                                                                                     :recipients    []}]}))
      (update :channels remove-extra-channels-fields)))

;; ## PUT /api/alert

(expect
  {:errors {:name "value must be a non-blank string."}}
  ((user->client :rasta) :put 400 "alert/1" {}))

(expect
  {:errors {:alert_condition "value must be one of: `goal`, `rows`."}}
  ((user->client :rasta) :put 400 "alert/1" {:name "abc"}))

(expect
  {:errors {:alert_condition "value must be one of: `goal`, `rows`."}}
  ((user->client :rasta) :put 400 "alert/1" {:name            "abc"
                                             :alert_condition "not rows"}))

(expect
  {:errors {:alert_description "value must be a non-blank string."}}
  ((user->client :rasta) :put 400 "alert/1" {:name            "abc"
                                             :alert_condition "rows"}))

(expect
  {:errors {:alert_first_only "value must be a boolean."}}
  ((user->client :rasta) :put 400 "alert/1" {:name              "abc"
                                             :alert_condition   "rows"
                                             :alert_description "foo"}))
(expect
  {:errors {:card "value must be a map."}}
  ((user->client :rasta) :put 400 "alert/1" {:name              "abc"
                                             :alert_condition   "rows"
                                             :alert_description "foo"
                                             :alert_first_only  false}))

(expect
  {:errors {:card "value must be a map."}}
  ((user->client :rasta) :put 400 "alert/1" {:name              "abc"
                                             :alert_condition   "rows"
                                             :alert_description "foo"
                                             :alert_first_only  false
                                             :card              "foobar"}))

(expect
  {:errors {:channels "value must be an array. Each value must be a map. The array cannot be empty."}}
  ((user->client :rasta) :put 400 "alert/1" {:name              "abc"
                                             :alert_condition   "rows"
                                             :alert_description "foo"
                                             :alert_first_only  false
                                             :card              {:id 100}}))

(expect
  {:errors {:channels "value must be an array. Each value must be a map. The array cannot be empty."}}
  ((user->client :rasta) :put 400 "alert/1" {:name              "abc"
                                             :alert_condition   "rows"
                                             :alert_description "foo"
                                             :alert_first_only  false
                                             :card              {:id 100}
                                             :channels          "foobar"}))

(expect
  {:errors {:channels "value must be an array. Each value must be a map. The array cannot be empty."}}
  ((user->client :rasta) :put 400 "alert/1" {:name              "abc"
                                             :alert_condition   "rows"
                                             :alert_description "foo"
                                             :alert_first_only  false
                                             :card              {:id 100}
                                             :channels          ["abc"]}))

(tt/expect-with-temp [Pulse [pulse {:alert_description "Foo"
                                    :alert_condition   "rows"
                                    :alert_first_only  false
                                    :creator_id        (user->id :rasta)
                                    :name              "Original Alert Name"
                                    }]
                      Card  [card]]
  {:name              "Updated Pulse"
   :creator_id        (user->id :rasta)
   :creator           (user-details (fetch-user :rasta))
   :created_at        true
   :updated_at        true
   :alert_description "Foo"
   :alert_condition   "rows"
   :alert_first_only  false
   :alert_above_goal  nil
   :card              (pulse-card-details card)
   :channels          [(merge pulse-channel-defaults
                              {:channel_type  "slack"
                               :schedule_type "hourly"
                               :details       {:channels "#general"}
                               :recipients    []})]
   :skip_if_empty     true}

  ;; Need to move this extra alert stuff to the pulse table, pulse_channels makes no sense here

  (-> (pulse-response ((user->client :rasta) :put 200 (format "alert/%d" (:id pulse))
                              {:name              "Updated Pulse"
                               :card              {:id (:id card)}
                               :alert_description "Foo"
                               :alert_condition   "rows"
                               :alert_first_only  false
                               :channels          [{:enabled       true
                                                    :channel_type  "slack"
                                                    :schedule_type "hourly"
                                                    :schedule_hour 12
                                                    :schedule_day  "mon"
                                                    :recipients    []
                                                    :details       {:channels "#general"}}]
                               :skip_if_empty     false}))
      (update :channels remove-extra-channels-fields)))
