(ns route-swagger.schema
  (:require [schema.coerce :as c]
            [schema.core :as s]
            [schema.utils :as u]))

(defn- coerce [schema value coercions]
  (let [result ((c/coercer schema coercions) value)]
    (when (u/error? result)
      (throw (ex-info (format "Value does not match schema: %s" (pr-str result)) {:schema schema :value value :error result})))
    result))

(defn- relax [schema keys]
  (reduce (fn [m k]
            (cond-> m (k m) (update k assoc s/Keyword s/Any)))
          (assoc schema s/Keyword s/Any) keys))

(defn make-coerce-request
  "Builds a coerce-request fn used in a swagger interceptor.
  Accepts custom coercions as an optional parameter."
  ([] (make-coerce-request c/string-coercion-matcher))
  ([coercions]
   (fn [schema request]
     (coerce (relax schema [:headers :query-params]) request coercions))))

(defn make-validate-response
  "Builds a validate-response fn used in a swagger interceptor.
  Accepts custom coercions as an optional parameter."
  ([] (make-validate-response {}))
  ([coercions]
   (fn [schema response]
     (coerce (relax schema [:headers]) response coercions))))
