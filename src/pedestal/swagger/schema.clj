(ns pedestal.swagger.schema
  (:require [ring.swagger.core :as swagger]
            [ring.swagger.json-schema :as json]
            [schema.coerce :as c]
            [schema.core :as s]
            [schema.utils :as u]))

(defn- deep-merge-with
  "Like merge-with, but merges maps recursively, applying the given fn
  only when there's a non-map at a particular level.

  (deep-merge-with + {:a {:b {:c 1 :d {:x 1 :y 2}} :e 3} :f 4}
                     {:a {:b {:c 2 :d {:z 9} :z 3} :e 100}})
  -> {:a {:b {:z 3, :c 3, :d {:z 9, :x 1, :y 2}}, :e 103}, :f 4}"
  [f & maps]
  (apply
   (fn m [& maps]
     (if (every? map? maps)
       (apply merge-with m maps)
       (apply f maps)))
   maps))

(def deep-merge (partial deep-merge-with (fn [& args] (last args))))

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
  (let [params-schema (->request-keys params-schema)
        result (coerce params-schema default-matcher request)]
    (if (u/error? result)
      (assoc request :errors result)
      (deep-merge request result))))

(defn validate-response [responses-schema {:keys [status body] :as response}]
  (if-let [{:keys [schema]} (get responses-schema status)]
    (s/validate schema body)
    (s/validate (get responses-schema :default s/Any) body)))
