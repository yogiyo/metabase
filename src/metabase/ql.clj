(ns metabase.query-processor.ql
  (:refer-clojure :exclude [count])
  (:require [clojure.pprint :as pprint]
            [schema.core :as s]
            [metabase.query-processor.expand :as ql]
            [metabase.util :as u]
            [metabase.util.schema :as su]
            [metabase.query-processor.util :as qputil]))

(defprotocol IQueryLanguage
  (explain [this]))

(extend-protocol IQueryLanguage
  nil
  (explain [_] nil)

  Object
  (explain [this] this)

  #_clojure.lang.Sequential
  #_(explain [this] (mapv explain this)))

(defmethod print-method IQueryLanguage [obj, ^java.io.Writer writer]
  (print-method (explain obj) writer))

(defmethod print-dup IQueryLanguage [obj, ^java.io.Writer writer]
  (print-dup (explain obj) writer))

(defmethod pprint/simple-dispatch IQueryLanguage [obj]
  (pprint/write-out (explain obj)))

(doseq [method [print-method print-dup pprint/simple-dispatch]
        klass  [clojure.lang.IRecord java.util.Map clojure.lang.IPersistentMap]]
  (prefer-method method IQueryLanguage klass))

(def ^:private hierarchy (atom (make-hierarchy)))

(defn- ql-derive [tag parent]
  (swap! hierarchy derive tag parent))

(defn- ql-type [obj]
  (if (class? obj)
    obj
    (class obj)))

(defn ql-isa? [obj parent]
  (isa? @hierarchy (ql-type obj) parent))

(defrecord Isa [parent]
  schema.core/Schema
  (spec [this] (schema.spec.leaf/leaf-spec (schema.spec.core/precondition this
                                                                          #(ql-isa? % parent)
                                                                          #(list 'isa? % parent))))
  (explain [this] (list 'isa? parent)))

(defn is [ql-type]
  (s/named (Isa. ql-type)
           (str "Must be a " (name ql-type))))


(defn- parser-dispatch-map [metadata-key]
  (into {} (for [[symb varr] (ns-interns *ns*)
                 :when       (metadata-key (meta varr))]
             {(keyword symb) varr})))

(defn- make-clause-parser [metadata-key]
  (let [dispatch-map (parser-dispatch-map metadata-key)]
    (fn [[k & args]]
      (let [f (dispatch-map (qputil/normalize-token k))]
        (if-not f
          (println "Don't know how to parse:" k)
          (do
            (println `(~f ~@args))
            (apply f args)))))))


;;; +------------------------------------------------------------------------------------------------------------------------+
;;; |                                                  UNITS & OTHER TYPES                                                   |
;;; +------------------------------------------------------------------------------------------------------------------------+

(def ^:const datetime-field-units
  "Valid units for a `DatetimeField`."
  #{:default :minute :minute-of-hour :hour :hour-of-day :day :day-of-week :day-of-month :day-of-year
    :week :week-of-year :month :month-of-year :quarter :quarter-of-year :year})

(def ^:const relative-datetime-value-units
  "Valid units for a `RelativeDatetimeValue`."
  #{:minute :hour :day :week :month :quarter :year})

(def DatetimeUnit
  "Schema for datetime units that are valid for `DatetimeField` or `Datetime` forms."
  (s/named (apply s/enum datetime-field-units)
           "Valid datetime unit for a field"))

(def RelativeDatetimeValueUnit
  "Schema for datetime units that valid for relative datetime values."
  (s/named (apply s/enum relative-datetime-value-units)
           "Valid datetime unit for a relative datetime"))

;;; +------------------------------------------------------------------------------------------------------------------------+
;;; |                                                         FIELDS                                                         |
;;; +------------------------------------------------------------------------------------------------------------------------+

(s/defrecord FieldID [id :- s/Int]
  IQueryLanguage ; no parsing
  (explain [_]
    [:field-id id]))

(ql-derive FieldID :field)

(s/defn ^:field ^:always-validate field-id  :- FieldID
  [id :- s/Int]
  (FieldID. id))


(s/defrecord DatetimeField [field :- (is :field)
                            unit  :- DatetimeUnit]
  IQueryLanguage
  (explain [_]
    [:datetime-field (explain field) unit]))

(ql-derive DatetimeField :field)

(s/defn ^:ql ^:always-validate datetime-field :- DatetimeField
  [field unit]
  (DatetimeField. field (keyword unit)))


(s/defrecord FieldLiteral [])

(ql-derive FieldLiteral :field)


(s/defrecord ForeignField [])

(ql-derive ForeignField :field)

(def ^:private parse-field*
  (make-clause-parser :field))

(s/defn ^:private ^:always-validate parse-field :- (is :field)
  [f]
  (cond
    (ql-isa? f :field) f
    (integer? f)       (recur [:field-id f])
    (vector? f)        (parse-field* f)
    :else              f))


;;; +------------------------------------------------------------------------------------------------------------------------+
;;; |                                                         VALUES                                                         |
;;; +------------------------------------------------------------------------------------------------------------------------+

(ql-derive :orderable-value :value)

(ql-derive String :value)
(ql-derive Number :orderable-value)

(ql-derive :datetime :orderable-value)


(s/defrecord AbsoluteDatetime [value :- java.sql.Timestamp
                               unit  :- DatetimeUnit]
  IQueryLanguage
  (explain [_]
    (cons 'ql/absolute-datetime
          (cons (u/date->iso-8601 value)
                (when unit
                  [unit])))))

(ql-derive AbsoluteDatetime :datetime)

(s/defn ^:ql ^:always-validate absolute-datetime :- AbsoluteDatetime
  ([value]
   (absolute-datetime value :day)) ; default to day
  ([value unit]
   (AbsoluteDatetime. (u/->Timestamp value) (keyword unit))))


(s/defrecord RelativeDatetime [amount :- s/Int
                               unit   :- DatetimeUnit])


;;; +------------------------------------------------------------------------------------------------------------------------+
;;; |                                                      AGGREGATIONS                                                      |
;;; +------------------------------------------------------------------------------------------------------------------------+

(s/defrecord CountAggregation [])

(ql-derive CountAggregation :aggregation)

(s/defn ^:always-validate ^:aggregation count :- CountAggregation
  []
  (CountAggregation.))


(s/defrecord CumulativeCountAggregation [])

(ql-derive CumulativeCountAggregation :aggregation)


(s/defrecord AverageAggregation [field :- (is :field)]
  IQueryLanguage
  (explain [_]
    (println "(explain field):" (explain field)) ; NOCOMMIT
    [:avg (explain field)]))

(ql-derive AverageAggregation :aggregation)

(s/defn ^:always-validate ^:aggregation avg :- AverageAggregation
  [field]
  (AverageAggregation. (parse-field field)))


(s/defrecord CumulativeSumAggregation [])

(ql-derive CumulativeSumAggregation :aggregation)


(s/defrecord DistinctCountAggregation [])

(ql-derive DistinctCountAggregation :aggregation)


(s/defrecord MaxAggregation [])

(ql-derive MaxAggregation :aggregation)


(s/defrecord MinAggregation [])

(ql-derive MinAggregation :aggregation)


(s/defrecord StandardDeviationAggregation [])

(ql-derive StandardDeviationAggregation :aggregation)


(s/defrecord SumAggregation [])

(ql-derive SumAggregation :aggregation)

(def ^:private parse-aggregation-subclause
  (make-clause-parser :aggregation))


#_(s/defrecord Aggregations [aggregations :- [(is :aggregation)]]
  IQueryLanguage
  (explain [_]
    (cons 'ql/aggregation (map explain aggregations))))

(defn ^:ql-top-level aggregation
  [query aggregations]
  (cond
    (empty? aggregations)                    query
    (not (sequential? (first aggregations))) (recur query [aggregations])
    :else                                    (assoc query :aggregation (map parse-aggregation-subclause aggregations))))


;;; +------------------------------------------------------------------------------------------------------------------------+
;;; |                                                        BREAKOUT                                                        |
;;; +------------------------------------------------------------------------------------------------------------------------+

(s/defrecord Breakout [fields :- [(is :field)]]
  IQueryLanguage
  (explain [this]
    (cons 'ql/breakout (map explain fields))))

(defn- ^:ql-top-level breakout [query & fields]
  (assoc query :breakout (s/validate Breakout (Breakout. fields))))


;;; +------------------------------------------------------------------------------------------------------------------------+
;;; |                                                        FILTERS                                                         |
;;; +------------------------------------------------------------------------------------------------------------------------+

(ql-derive :simple-filter :filter)

(ql-derive :equality-filter :simple-filter)

(s/defrecord EqualsFilter [])

(ql-derive EqualsFilter :equality-filter)


(s/defrecord NotEqualsFilter [])

(ql-derive NotEqualsFilter :equality-filter)


(ql-derive :comparison-filter :simple-filter)

(s/defrecord LessThanFilter [])

(ql-derive LessThanFilter :comparison-filter)

(s/defrecord LessThanOrEqualToFilter [])

(ql-derive LessThanOrEqualToFilter :comparison-filter)

(s/defrecord GreaterThanFilter [])

(ql-derive GreaterThanFilter :comparison-filter)

(s/defrecord GreaterThanOrEqualToFilter [])

(ql-derive GreaterThanOrEqualToFilter :comparison-filter)



(ql-derive :string-filter :simple-filter)

(s/defrecord StartsWithFilter [])

(ql-derive StartsWithFilter :string-filter)


(s/defrecord ContainsFilter [])

(ql-derive ContainsFilter :string-filter)


(s/defrecord EndsWithFilter [])

(ql-derive EndsWithFilter :string-filter)


(ql-derive :compound-filter :filter)

(s/defrecord NotFilter [])

(ql-derive NotFilter :compound-filter)


(s/defrecord AndFilter [])

(ql-derive AndFilter :compound-filter)


(s/defrecord OrFilter [])

(ql-derive OrFilter :compound-filter)


;;; +------------------------------------------------------------------------------------------------------------------------+
;;; |                                                        ORDER BY                                                        |
;;; +------------------------------------------------------------------------------------------------------------------------+

(s/defrecord OrderByClause [field     :- (is :field)
                            direction :- (s/enum :ascending :descending)]
  IQueryLanguage
  (explain [_]
    [direction (explain field)]))

(s/defrecord OrderBy [clauses :- [OrderByClause]]
  IQueryLanguage
  (explain [_]
    (vec (cons :order-by (map explain clauses)))))


;;; +------------------------------------------------------------------------------------------------------------------------+
;;; |                                                          PAGE                                                          |
;;; +------------------------------------------------------------------------------------------------------------------------+

(s/defrecord Page [page  :- su/IntGreaterThanZero
                   items :- su/IntGreaterThanZero])


;;; +------------------------------------------------------------------------------------------------------------------------+
;;; |                                                      SOURCE QUERY                                                      |
;;; +------------------------------------------------------------------------------------------------------------------------+

(s/defrecord NativeSourceQuery [native :- s/Any])

(ql-derive NativeSourceQuery :source-query)


;;; +------------------------------------------------------------------------------------------------------------------------+
;;; |                                                         QUERY                                                          |
;;; +------------------------------------------------------------------------------------------------------------------------+

(s/defrecord Query [aggregation  :- (s/maybe [(is :aggregation)])
                    breakout     :- (s/maybe nil)
                    fields       :- (s/maybe nil)
                    filter       :- (s/maybe nil)
                    limit        :- (s/maybe nil)
                    order-by     :- (s/maybe OrderBy)
                    page         :- (s/maybe Page)
                    expressions  :- (s/maybe nil)
                    source-table :- (s/maybe nil)
                    source-query :- (is :source-query)]
  IQueryLanguage
  (explain [this]
    (apply list '-> {} (for [[_ v] this
                             :when v]
                         (explain v)))))

(ql-derive Query :source-query)

(def ^:private top-level-dispatch-map
  (parser-dispatch-map :ql-top-level))

(s/defn ^:always-validate query
  [query] :- Query
  (loop [[[k args] & more] (seq query), parsed {}]
    (println "k args:" k args) ; NOCOMMIT
    (if-not k
      (map->Query parsed)
      (let [f (top-level-dispatch-map (qputil/normalize-token k))]
        (println "f:" f) ; NOCOMMIT
        (when-not f
          (println "Don't know what to do with" f))
        (recur more (if (and f args)
                      (f parsed args)
                      parsed))))))

:ok
