(ns pedestal.swagger.schema
  (:require [schema.coerce :as c]
            [schema.core :as s]
            [schema.utils :as u]))

; explain? fn

(def ^:dynamic *matcher* c/json-coercion-matcher)

(defn coerce [schema value]
  ((c/coercer schema *matcher*) value))

(defn validate [schema value]
  true)

(def error? u/error?)

(def schema-name s/schema-name)

(defn coerce-params [params request]
  (apply merge-with merge
         (for [[key schema] params
               :let [result (coerce schema (get request key))]]
           (if (error? result)
             {:errors {key result}}
             {key result}))))
