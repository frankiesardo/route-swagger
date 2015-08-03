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

(defn coerce-request [schema value coercions]
  ((c/coercer (->request-schema schema) coercions)
   (with-request-defaults value)))

(defn make-coerce-request
  "Builds a coerce-request fn used in a swagger interceptor. A custom coercer can be built out of a coercer, validator and explainer"
  [& {:keys [coercions explainer coercer]
      :or {coercions c/+string-coercions+
           explainer explain
           coercer coerce-request}}]
  (fn [schema {:keys [request] :as context}]
    (let [result (coercer schema request coercions)]
      (if (u/error? result)
        (assoc (terminate context) :response
               {:status 400 :headers {} :body (explainer result)})
        (assoc context :request result)))))

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

(defn validate-response [schema value coercions]
  ((c/coercer (->response-schema schema) coercions)
   (with-response-defaults value)))

(defn make-validate-response
  "Builds a validate-response fn used in a swagger interceptor. A custom validator can be built out of coercions, validator and explainer"
  [& {:keys [coercions explainer validator]
      :or {coercions {}
           explainer explain
           validator validate-response}}]
  (fn [schema {:keys [response] :as context}]
    (let [result (validator schema response coercions)]
      (if (u/error? result)
        (assoc context :response
               {:status 500 :headers {} :body (explainer result)})
        (assoc context :response result)))))
