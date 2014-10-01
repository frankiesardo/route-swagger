(ns pedestal.swagger.doc
  (:require [pedestal.swagger.schema :as schema]
            [io.pedestal.http.route :as route]
            [ring.swagger.core :as swagger]))

(defn- routes->operations [route-table]
  (for [{:keys [interceptors] :as route} route-table
        :let [handler-docs (first (keep (comp ::handler meta) interceptors))
              middleware (keep (comp ::middleware meta) interceptors)]
        :when handler-docs]
    (if-let [middleware-docs (apply schema/deep-merge middleware)]
      (schema/deep-merge route middleware-docs handler-docs)
      (schema/deep-merge route handler-docs))))

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
    (-> (zipmap (map :route-name operations) operations)
        (assoc ::swagger-object swagger-object))
;    (prepare-swagger-object swagger-object)
    ))
