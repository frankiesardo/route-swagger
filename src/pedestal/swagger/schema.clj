(ns pedestal.swagger.schema
  (:require [ring.swagger.core :as swagger]
            [ring.swagger.json-schema :as json]
            [schema.coerce :as c]
            [schema.core :as s]
            [schema.utils :as u]))

(def ^:private  default-matcher c/+string-coercions+)

(def ^:private schema->param
  {:body    :body-params
   :form    :form-params
   :path    :path-params
   :query   :query-params
   :headers :headers})

(defn- coerce [schema matcher value]
  ((c/coercer schema matcher) value))

(defn- ->request-keys [params-schema]
  (into {} (map (fn [[k v]] [(schema->param k) v]) params-schema)))

(defn coerce-params [params-schema request]
  (let [params-schema (assoc (->request-keys params-schema) s/Any s/Any)
        result (coerce params-schema default-matcher request)]
    (if (u/error? result)
      (assoc request :errors result)
      (merge request result))))

(defn validate-response [responses-schema {:keys [status body] :as response}]
  (if-let [{:keys [schema]} (get-in responses-schema [status :model])]
    (s/validate schema body)
    (s/validate (get responses-schema :default s/Any) body)))
