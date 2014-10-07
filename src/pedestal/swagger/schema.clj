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
   :header  :headers})

(defn- loosen-schema [[k v]]
  (if (#{:query :header} k)
    [k (assoc v s/Any s/Any)]
    [k v]))

(defn- ->request-schema [params-schema]
  (->> params-schema
       (map loosen-schema)
       (map (fn [[k v]] [(schema->param k) v]))
       (into {})
       (merge {s/Any s/Any})))

(defn- coerce [schema matcher value]
  ((c/coercer schema matcher) value))

(defn coerce-params [params-schema request]
  (let [request-schema (->request-schema params-schema)
        result (coerce request-schema default-matcher request)]
    (if (u/error? result)
      (assoc request :errors result)
      (merge request result))))

(defn validate-response [responses-schema {:keys [status body] :as response}]
  (assoc response :body
         (if-let [{:keys [schema]} (get responses-schema status)]
           (if schema (s/validate schema body) body)
           (s/validate (get responses-schema :default s/Any) body))))
