(ns pedestal.swagger.schema
  (:require [ring.swagger.core :as swagger]
            [ring.swagger.json-schema :as json]
            [schema.coerce :as c]
            [schema.core :as s]
            [schema.utils :as u]))

(def ^:dynamic *matcher* c/+string-coercions+)

(defn- coerce [schema value]
  ((c/coercer schema *matcher*) value))

(defn coerce-params [preconditions request]
  (let [request-schema (assoc preconditions s/Any s/Any)
        result (coerce request-schema request)]
    (if (u/error? result)
      (assoc request :errors result)
      (merge request result))))

(defn validate-responses [postconditions {:keys [status body] :as response}]
  (if-let [{:keys [schema]} (get postconditions status)]
    (assert (s/validate schema body))
    (assert (s/validate (get postconditions :default s/Any) body))))

;;

(defn convert-returns [returns-schema]
  (json/->json returns-schema :top true))

(defn convert-parameters [params-schema]
  (swagger/convert-parameters
   (for [[type model] params-schema]
     {:type type :model model})))

(defn convert-responses [responses-schema]
  [])

(defn convert-models [models-schemas]
  (->> models-schemas
       (map swagger/with-named-sub-schemas)
       (map (juxt s/schema-name identity))
       (into {})
       swagger/transform-models))
