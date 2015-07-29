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
  {:body     :body-params
   :formData :form-params
   :path     :path-params
   :query    :query-params
   :header   :headers})

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

(defn- with-request-defaults [r]
  (merge
   {:body-params  nil
    :form-params  {}
    :path-params  {}
    :query-params {}
    :headers      {}} r))

(defn coerce-request [schema value]
  ((c/coercer (->request-schema schema) c/+string-coercions+)
   (with-request-defaults value)))

(defn ?bad-request
  "Terminates the interceptors chain and returns a 400 response if the
  request doesn't match the schema supplied."
  [schema {:keys [request] :as context}]
  (let [result (coerce-request schema request)]
    (if (u/error? result)
      (assoc (terminate context) :response
             {:status 400 :headers {} :body (explain result)})
      (assoc context :request result))))

(defn- ->response-schema [{:keys [headers schema]}]
  (loosen
   (merge
    (when schema
      {:body schema})
    (when headers
      {:headers (loosen headers)}))))

(defn- with-response-defaults [r]
  (merge
   {:headers {}
    :body nil}
   r))

(defn validate-response [schema value]
  ((c/coercer (->response-schema schema) {})
   (with-response-defaults value)))

(defn ?internal-server-error
  "Changes the response to a 500 if the response doesn't match the
  schema supplied."
  [schema {:keys [response] :as context}]
  (let [result (validate-response schema response)]
    (if (u/error? result)
      (assoc context :response
             {:status 500 :headers {} :body (explain result)})
      context)))
