(ns metabase.query-processor.middleware.format-rows
  "Middleware that formats the results of a query.
   Currently, the only thing this does is convert datetime types to ISO-8601 strings in the appropriate timezone."
  (:require [metabase.util :as u]))

(defn- format-rows* [{:keys [report-timezone]} {:keys [cols rows] :as results}]
  (let [timezone (or report-timezone (System/getProperty "user.timezone"))]
    (assoc results :rows
           (for [row rows]
             (map (fn [col v]
                    (if (u/is-temporal? v)
                      ;; NOTE: if we don't have an explicit report-timezone then use the JVM timezone
                      ;;       this ensures alignment between the way dates are processed by JDBC and our returned data
                      ;;       GH issues: #2282, #2035
                      (if (= :type/Date (:base_type col))
                        (clj-time.format/unparse (clj-time.format/formatters :date) (clj-time.coerce/from-date v))
                        (u/->iso-8601-datetime v timezone))
                      v))
                  cols row)))))

(defn format-rows
  "Format individual query result values as needed.  Ex: format temporal values as iso8601 strings w/ timezone."
  [qp]
  (fn [{:keys [settings] :as query}]
    (let [results (qp query)]
      (if-not (:rows results)
        results
        (format-rows* settings results)))))
