(ns pedestal.swagger.doc
  (:require [pedestal.swagger.schema :as schema]
            [io.pedestal.http.route :as route]
            [ring.swagger.core :as swagger]))


; setup swagger-ui?

;;

(defn- relative-path [parent child]
  (.substring child (count parent)))

(defn list-resources [{:keys [apis] :as docs} route-table]
  (let [url-for (route/url-for-routes route-table)
        api-docs-path (url-for ::api-docs)]
    (merge
     swagger/swagger-defaults
     (select-keys docs [:apiVersion])
     {:info (select-keys docs swagger/api-declaration-keys)
      :apis (for [[name details] apis]
              {:path (relative-path api-docs-path (url-for name))
               :description (or (:description details) "")})})))

;;;;

(defn- expand-op [{:keys [route-name method] :as op-spec}]
  (merge
   ;; data type
   {:type "void"}
   (select-keys op-spec [:summary :notes])
   {:method (-> method name .toUpperCase)
    :nickname route-name
    :responseMessages []
    :parameters []}))

(defn- extract-models [interceptors]
  (let [body-schemas (keep (comp :json-body ::params meta) interceptors)
        returns-schemas (keep (comp ::returns meta) interceptors)
        _ (println "&&&" (map meta interceptors))
        _ (println "***" body-schemas "***" returns-schemas)
        all-models (->> (concat body-schemas returns-schemas)
                        flatten
                        (map swagger/with-named-sub-schemas))]
    (into {} (map (juxt schema/schema-name identity) all-models))))

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
      :models (swagger/transform-models models)
      :apis (for [[path ops-spec] ops-by-path]
              {:path (swagger/swagger-path path)
               :operations (map expand-op ops-spec)})})))

;; Could potentially group by app-name
(defn expand-docs [doc-spec route-table]
  (apply merge
         {::api-docs (list-resources doc-spec route-table)}
         (for [[route-name api-spec] (:apis doc-spec)
               :let [api-spec (merge (select-keys doc-spec [:apiVersion]) api-spec)]]
           {route-name (declare-api api-spec route-table)})))
