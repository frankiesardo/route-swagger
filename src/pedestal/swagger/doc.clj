(ns pedestal.swagger.doc
  (:require [pedestal.swagger.schema :as schema]
            [io.pedestal.http.route :as route]
            [ring.swagger.core :as swagger]))

(defn- routes->operations [route-table]
  (for [{:keys [interceptors] :as route} route-table
        :let [docs (first (keep (comp ::handler meta) interceptors))]
        :when docs]
    (merge route docs)))

(defn- routes->swagger-object [route-table]
  (->> route-table
       (filter #(= ::swagger-object (:route-name %)))
       first
       :interceptors
       (keep (comp ::swagger-object meta))
       first))

(defn- prepare-swagger-object [swagger-object]
  swagger-object)

(defn generate-docs [route-table]
  (let [operations (routes->operations route-table)
        swagger-object (assoc (routes->swagger-object route-table)
                         :operations operations)]
    (prepare-swagger-object swagger-object)))
