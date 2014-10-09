(ns pedestal.swagger.schema
  (:require [schema.coerce :as c]
            [schema.core :as s]
            [schema.utils :as u]
            [io.pedestal.impl.interceptor :refer [terminate]]))

(defn explain
  "Tries to give a meaningful explanation for each error key. It
   works better if each schema is a named schema, otherwise defaults
   to the technical validation-error explanation."
  [e]
  (cond
   (instance? schema.utils.NamedError e)
   (let [error (.-error e)
         name (.-name e)]
     (if (map? error)
       (explain error)
       name))

   (map? e)
   (into {} (for [[k v] e]
              [k (explain v)]))

   :default
   (pr-str e)))

(defn- loosen [schema]
  (assoc schema s/Any s/Any))

(def ^:private schema->param
  {:body    :body-params
   :form    :form-params
   :path    :path-params
   :query   :query-params
   :header  :headers})

(def ^:private loose-schema?
  #{:query :header})

(defn- ->request-schema [schema]
  (->> (for [[k v] schema]
         [(schema->param k)
          (if (loose-schema? k)
            (loosen v)
            v)])
       (into {})
       (loosen)))

(defn- coerce [schema value]
  ((c/coercer schema c/+string-coercions+) value))

(defn coerce-params [schema {:keys [request] :as context}]
  (let [request-schema (->request-schema schema)
        result (coerce request-schema request)]
    (if (u/error? result)
      (assoc (terminate context)
        :response {:status 400 :headers {} :body (explain result)})
      (assoc context :request result))))

;;

(defn- ->response-schema [{:keys [headers schema]}]
  (->> (for [h headers]
         [(s/required-key h) s/Any])
       (into {})
       (loosen)
       (list :body schema :headers)
       (apply array-map)
       (loosen)))

(defn- validate [schema value]
  ((c/coercer schema {}) value))

(defn validate-response [schema {:keys [response] :as context}]
  (let [response-schema (->response-schema schema)
        result (validate response-schema response)]
    (if (u/error? result)
      (assoc context :response {:status 500 :headers {} :body (explain result)})
      context)))
