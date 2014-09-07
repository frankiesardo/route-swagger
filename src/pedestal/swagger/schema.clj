(ns pedestal.swagger.schema
  (:require [ring.swagger.core :as swagger]
            [ring.swagger.json-schema :as json]
            [schema.coerce :as c]
            [schema.core :as s]
            [schema.utils :as u]))

(def ^:dynamic *matcher* c/+string-coercions+)

(defn coerce [schema value]
  ((c/coercer schema *matcher*) value))

(def validate s/validate)

(def error? u/error?)

(def ^:private schema-key->request-key
  {:path :path-params
   :query :query-params
   :body :json-params
   :header :headers
   :form :form-params})

(defn coerce-params [params request]
  (apply merge-with merge
         (for [[key schema] params
               :let [request-key (schema-key->request-key key)
                     result (coerce schema (get request request-key))]]
           (if (error? result)
             {:errors {key result}}
             {key result}))))
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
