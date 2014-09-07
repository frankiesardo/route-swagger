(ns pedestal.swagger.doc
  (:require [pedestal.swagger.schema :as schema]
            [io.pedestal.http.route :as route]
            [ring.swagger.core :as swagger]))


; setup swagger-ui?

;;

(defn- relative-path [parent child]
  (.substring child (count parent)))

(defn list-resources [doc-spec route-table]
  (let [url-for (route/url-for-routes route-table)
        api-docs-url (url-for ::api-docs)]
    (merge
     swagger/swagger-defaults
     (select-keys doc-spec [:apiVersion])
     {:info (select-keys doc-spec swagger/api-declaration-keys)
      :apis (for [{:keys [route-name description]} (:apis doc-spec)]
              {:path (relative-path api-docs-url (url-for route-name))
               :description (or description "")})})))

;;;;


(defn- expand-op [{:keys [route-name method interceptors] :as op-spec}]
  (let [returns-schema (last (keep (comp ::post meta) interceptors))
        params-schema (apply merge-with merge (keep (comp ::pre meta) interceptors))
        responses-schema []]
    (merge
     (schema/convert-returns returns-schema)
     (select-keys op-spec [:summary :notes])
     {:method (-> method name clojure.string/upper-case)
      :nickname route-name
      :responseMessages (schema/convert-responses responses-schema)
      :parameters (schema/convert-parameters params-schema)})))

(defn- extract-models [interceptors]
  (let [body-schemas (keep (comp :body ::pre meta) interceptors)
        returns-schemas (keep (comp ::post meta) interceptors)
        models-schemas (concat body-schemas returns-schemas)]
    models-schemas))

(defn- base-path [{:keys [scheme host port] :or {scheme :http host "localhost"}}]
  (let [port (when (#'route/non-standard-port? scheme port) (str ":" port))]
    (str (name scheme) "://" host port)))

(defn- extract-op [route-table {:keys [route-name] :as route-spec}]
  (let [route (first (filter #(= route-name (:route-name %)) route-table))
        base-path (base-path route)]
    (merge
     route-spec
     (select-keys route [:method :path :interceptors])
     {:base-path base-path})))

(defn declare-api [api-spec route-table]
  (let [ops-spec (->> api-spec :ops (map (partial extract-op route-table)))
        base-path (first (map :base-path ops-spec)) ;; throw if different
        interceptors (mapcat :interceptors ops-spec)
        models (extract-models interceptors)
        ops-by-path (group-by :path ops-spec)]
    (merge
     swagger/swagger-defaults
     swagger/resource-defaults
     (select-keys api-spec [:apiVersion :produces :consumes])
     {:basePath base-path
      :resourcePath "/"
      :models (schema/convert-models models)
      :apis (for [[path ops-spec] ops-by-path]
              {:path (swagger/swagger-path path)
               :operations (map expand-op ops-spec)})})))

;; Could potentially group by app-name
(defn expand-docs [doc-spec route-table]
  (apply merge
         {::api-docs (list-resources doc-spec route-table)}
         (for [{:keys [route-name] :as api-spec} (:apis doc-spec)
               :let [api-spec (merge (select-keys doc-spec [:apiVersion]) api-spec)]]
           {route-name (declare-api api-spec route-table)})))
