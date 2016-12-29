(ns pedestal.swagger.schema
  (:require [schema.coerce :as c]
            [schema.core :as s]
            [schema.utils :as u]
            [io.pedestal.interceptor.chain :refer [terminate]]))

(defn- coerce! [schema value coercions]
  (let [result ((c/coercer schema coercions) value)]
    (when (u/error? result)
      (throw (ex-info (format "Value does not match schema: %s" (pr-str result)) {:schema schema :value value :error result})))
    result))

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

(defn- coerce-request [schema value coercions]
  (coerce! (->request-schema schema)
           (with-request-defaults value)
           coercions))

(defn make-coerce-request
  "Builds a coerce-request fn used in a swagger interceptor.
  Accepts custom coercions as an optional parameter."
  ([] (make-coerce-request c/string-coercion-matcher))
  ([coercions]
   (fn [schema request]
     (coerce-request schema request coercions))))

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

(defn- validate-response [schema value coercions]
  (coerce! (->response-schema schema)
           (with-response-defaults value)
           coercions))

(defn make-validate-response
  "Builds a validate-response fn used in a swagger interceptor.
  Accepts custom coercions as an optional parameter."
  ([] (make-validate-response c/string-coercion-matcher))
  ([coercions]
   (fn [schema response]
     (validate-response schema response coercions))))
