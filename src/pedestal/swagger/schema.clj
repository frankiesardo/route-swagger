(ns pedestal.swagger.schema
  (:require [ring.swagger.core :as swagger]
            [ring.swagger.json-schema :as json]
            [schema.coerce :as c]
            [schema.core :as s]
            [schema.utils :as u]))

; explain? fn

(def ^:dynamic *matcher* c/json-coercion-matcher)

(defn coerce [schema value]
  ((c/coercer schema *matcher*) value))

(def validate s/validate)

(def error? u/error?)

(defn coerce-params [params request]
  (apply merge-with merge
         (for [[key schema] params
               :let [result (coerce schema (get request key))]]
           (if (error? result)
             {:errors {key result}}
             {key result}))))


;;

(defn convert-returns [returns-schema]
  (json/->json returns-schema :top true))

(def ^:private param-type
  {:path-params :path
   :query-params :query
   :json-body :body
   :headers :header
   :form-params :form})

(defn convert-parameters [params-schema]
  (swagger/convert-parameters
   (for [[type model] params-schema]
     {:type (param-type type) :model model})))

(defn convert-responses [responses-schema]
  [])

;

(defn convert-models [models-schemas]
  (->> models-schemas
       (map swagger/with-named-sub-schemas)
       (map (juxt s/schema-name identity))
       (into {})
       swagger/transform-models))
