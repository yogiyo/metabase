(ns metabase.api.weird-kyle-bug
  (:require [compojure.core :refer [GET]]
            [metabase.api.common :as api]
            [metabase.models
             [segment :refer [Segment]]]))

(api/defendpoint GET "/test1/:id1/:id2"
  [id1 id2]
  (map (partial api/read-check Segment) [id1 id2])
  "ok")

(api/defendpoint GET "/test2/:id1"
  [id1]
  (api/read-check Segment id1)
  "ok")

(api/defendpoint GET "/test3/:id1"
  [id1]
  (Segment id1)
  "ok")

(api/defendpoint GET "/test4/:id1/:id2"
  [id1 id2]
  [(Segment id1) (Segment id2)]
  "ok")

(api/define-routes)
