(ns metabase.api.alert-test
  (:require [expectations :refer :all]
            [metabase
             [http-client :as http]
             [middleware :as middleware]]
            [metabase.models
             [card :refer [Card]]
             [pulse :as pulse :refer [Pulse]]
             [pulse-card :refer [PulseCard]]
             [pulse-channel :refer [PulseChannel]]
             [pulse-channel-recipient :refer [PulseChannelRecipient]]]
            [metabase.test
             [data :as data]
             [util :as tu]]
            [metabase.test.data
             [dataset-definitions :as defs]
             [users :refer :all]]
            [metabase.test.mock.util :refer [pulse-channel-defaults]]
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

(defn- basic-alert-query []
  {:name "Foo"
   :dataset_query {:database (data/id)
                   :type     :query
                   :query {:source_table (data/id :checkins)
                           :aggregation [["count"]]
                           :breakout [["datetime-field" (data/id :checkins :date) "hour"]]}}})

;; Basic test covering the /alert/question/:id call for a user
(expect
  [{:alert_condition "rows",
    :id true
    :name "Alert Name",
    :creator_id true
    :updated_at true,
    :alert_first_only false,
    :card {:name "Foo", :description nil, :display "table", :id true},
    :skip_if_empty false,
    :alert_description "Alert when above goal",
    :created_at true,
    :alert_above_goal true
    :creator (assoc (user-details (fetch-user :rasta)) :id true)
    :read_only false
    :channels
    [{:schedule_type "daily",
      :schedule_hour 15,
      :channel_type "email",
      :schedule_frame nil,
      :recipients [{:id true, :email "rasta@metabase.com", :first_name "Rasta", :last_name "Toucan", :common_name "Rasta Toucan"}],
      :schedule_day nil,
      :enabled true
      :updated_at true,
      :pulse_id true,
      :id true,
      :created_at true}]}]
  (data/with-db (data/get-or-create-database! defs/test-data)
    (tt/with-temp* [Card                 [{card-id :id}  (basic-alert-query)]
                    Pulse                [{pulse-id :id} {:name              "Alert Name"
                                                          :alert_condition   "rows"
                                                          :alert_description "Alert when above goal"
                                                          :alert_first_only  false
                                                          :alert_above_goal  true}]
                    PulseCard             [_             {:pulse_id pulse-id
                                                          :card_id  card-id
                                                          :position 0}]
                    PulseChannel          [{pc-id :id}   {:pulse_id pulse-id}]
                    PulseChannelRecipient [_             {:user_id          (user->id :rasta)
                                                          :pulse_channel_id pc-id}]]
      (tu/boolean-ids-and-timestamps ((user->client :rasta) :get 200 (format "alert/question/%d" card-id))))))

;; Non-admin users shouldn't see alerts they created if they're no longer recipients
(expect
  [1 0]
  (data/with-db (data/get-or-create-database! defs/test-data)
    (tt/with-temp* [Card                 [{card-id :id}  (basic-alert-query)]
                    Pulse                [{pulse-id :id} {:name              "Alert Name"
                                                          :alert_condition   "rows"
                                                          :alert_description "Alert when above goal"
                                                          :alert_first_only  false
                                                          :alert_above_goal  true
                                                          :creator_id        (user->id :rasta)}]
                    PulseCard             [_             {:pulse_id pulse-id
                                                          :card_id  card-id
                                                          :position 0}]
                    PulseChannel          [{pc-id :id}   {:pulse_id pulse-id}]
                    PulseChannelRecipient [{pcr-id :id}  {:user_id          (user->id :rasta)
                                                          :pulse_channel_id pc-id}]
                    PulseChannelRecipient [_             {:user_id          (user->id :crowberto)
                                                          :pulse_channel_id pc-id}]]

      [(count (tu/boolean-ids-and-timestamps ((user->client :rasta) :get 200 (format "alert/question/%d" card-id))))
       (do
         (db/delete! PulseChannelRecipient :id pcr-id)
         (count (tu/boolean-ids-and-timestamps ((user->client :rasta) :get 200 (format "alert/question/%d" card-id)))))])))

;; Non-admin users should not see others alerts, admins see all alerts
(expect
  [1 2]
  (data/with-db (data/get-or-create-database! defs/test-data)
    (tt/with-temp* [Card                 [{card-id :id}  (basic-alert-query)]
                    Pulse                [{pulse-id-1 :id} {:name              "Alert Name"
                                                            :alert_condition   "rows"
                                                            :alert_description "My rows alert"
                                                            :alert_first_only  false
                                                            :alert_above_goal  false
                                                            :creator_id        (user->id :rasta)}]
                    PulseCard             [_               {:pulse_id pulse-id-1
                                                            :card_id  card-id
                                                            :position 0}]
                    PulseChannel          [{pc-id-1 :id}   {:pulse_id pulse-id-1}]
                    PulseChannelRecipient [_               {:user_id          (user->id :rasta)
                                                            :pulse_channel_id pc-id-1}]
                    ;; A separate admin created alert
                    Pulse                [{pulse-id-2 :id} {:name              "Second alert"
                                                            :alert_condition   "rows"
                                                            :alert_description "This is a group alert, created by an admin"
                                                            :alert_first_only  false
                                                            :alert_above_goal  false
                                                            :creator_id        (user->id :crowberto)}]
                    PulseCard             [_               {:pulse_id pulse-id-2
                                                            :card_id  card-id
                                                            :position 0}]
                    PulseChannel          [{pc-id-2 :id}   {:pulse_id pulse-id-2}]
                    PulseChannelRecipient [_               {:user_id          (user->id :crowberto)
                                                            :pulse_channel_id pc-id-2}]
                    PulseChannel          [{pc-id-3 :id}   {:pulse_id     pulse-id-2
                                                            :channel_type "slack"}]]
      [(count (tu/boolean-ids-and-timestamps ((user->client :rasta) :get 200 (format "alert/question/%d" card-id))))
       (count (tu/boolean-ids-and-timestamps ((user->client :crowberto) :get 200 (format "alert/question/%d" card-id))))])))

(defn- recipient-emails [results]
  (->> results
       first
       :channels
       first
       :recipients
       (map :email)
       set))

;; Alert has two recipients, remove one
(expect
  [#{"crowberto@metabase.com" "rasta@metabase.com"} #{"rasta@metabase.com"}]
  (data/with-db (data/get-or-create-database! defs/test-data)
    (tt/with-temp* [Card                 [{card-id :id}  (basic-alert-query)]
                    Pulse                [{pulse-id :id} {:name              "Alert Name"
                                                          :alert_condition   "rows"
                                                          :alert_description "Alert on a thing"
                                                          :alert_first_only  false}]
                    PulseCard             [_             {:pulse_id pulse-id
                                                          :card_id  card-id
                                                          :position 0}]
                    PulseChannel          [{pc-id :id}   {:pulse_id pulse-id}]
                    PulseChannelRecipient [_             {:user_id          (user->id :rasta)
                                                          :pulse_channel_id pc-id}]
                    PulseChannelRecipient [_             {:user_id          (user->id :crowberto)
                                                          :pulse_channel_id pc-id}]]

      [(recipient-emails ((user->client :rasta) :get 200 (format "alert/question/%d" card-id)))
       (do
         ((user->client :crowberto) :put 204 (format "alert/%d/unsubscribe" pulse-id))
         (recipient-emails ((user->client :rasta) :get 200 (format "alert/question/%d" card-id))))])))

;; Testing delete of pulse by it's creator
(expect
  [1 nil 0]
  (data/with-db (data/get-or-create-database! defs/test-data)
    (tt/with-temp* [Card                 [{card-id :id}  (basic-alert-query)]
                    Pulse                [{pulse-id :id} {:name              "Alert Name"
                                                          :alert_condition   "rows"
                                                          :alert_description "Alert on a thing"
                                                          :alert_first_only  false
                                                          :creator_id        (user->id :rasta)}]
                    PulseCard             [_             {:pulse_id pulse-id
                                                          :card_id  card-id
                                                          :position 0}]
                    PulseChannel          [{pc-id :id}   {:pulse_id pulse-id}]
                    PulseChannelRecipient [_             {:user_id          (user->id :rasta)
                                                          :pulse_channel_id pc-id}]]

      [(count ((user->client :rasta) :get 200 (format "alert/question/%d" card-id)))
       ((user->client :rasta) :delete 204 (format "alert/%d" pulse-id))
       (count ((user->client :rasta) :get 200 (format "alert/question/%d" card-id)))])))

;; Testing a user can't delete an admin's alert
(expect
  [1 nil 0]
  (data/with-db (data/get-or-create-database! defs/test-data)
    (tt/with-temp* [Card                 [{card-id :id}  (basic-alert-query)]
                    Pulse                [{pulse-id :id} {:name              "Alert Name"
                                                          :alert_condition   "rows"
                                                          :alert_description "Alert on a thing"
                                                          :alert_first_only  false
                                                          :creator_id        (user->id :crowberto)}]
                    PulseCard             [_             {:pulse_id pulse-id
                                                          :card_id  card-id
                                                          :position 0}]
                    PulseChannel          [{pc-id :id}   {:pulse_id pulse-id}]
                    PulseChannelRecipient [_             {:user_id          (user->id :rasta)
                                                          :pulse_channel_id pc-id}]]
      (let [original-alert-response ((user->client :crowberto) :get 200 (format "alert/question/%d" card-id))]

        ;; A user can't delete an admin's alert
        ((user->client :rasta) :delete 403 (format "alert/%d" pulse-id))

        [(count original-alert-response)
         ((user->client :crowberto) :delete 204 (format "alert/%d" pulse-id))
         (count ((user->client :rasta) :get 200 (format "alert/question/%d" card-id)))]))))

;; An admin can delete a user's alert
(expect
  [1 nil 0]
  (data/with-db (data/get-or-create-database! defs/test-data)
    (tt/with-temp* [Card                 [{card-id :id}  (basic-alert-query)]
                    Pulse                [{pulse-id :id} {:name              "Alert Name"
                                                          :alert_condition   "rows"
                                                          :alert_description "Alert on a thing"
                                                          :alert_first_only  false
                                                          :creator_id        (user->id :rasta)}]
                    PulseCard             [_             {:pulse_id pulse-id
                                                          :card_id  card-id
                                                          :position 0}]
                    PulseChannel          [{pc-id :id}   {:pulse_id pulse-id}]
                    PulseChannelRecipient [_             {:user_id          (user->id :rasta)
                                                          :pulse_channel_id pc-id}]]
      [(count ((user->client :rasta) :get 200 (format "alert/question/%d" card-id)))
       ((user->client :crowberto) :delete 204 (format "alert/%d" pulse-id))
       (count ((user->client :rasta) :get 200 (format "alert/question/%d" card-id)))])))
