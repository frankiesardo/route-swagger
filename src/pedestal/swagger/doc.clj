(ns pedestal.swagger.doc
  (:require [ring.swagger.core :as swagger]
            [io.pedestal.http.route :as route]))

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

(def ^:private deep-merge (partial deep-merge-with (fn [& args] (last args))))

(defn- routes->operations [route-table]
  (for [{:keys [interceptors] :as route} route-table
        :let [route (select-keys route [:route-name :path :method])
              route-docs (keep (comp ::route meta) interceptors)]
        :when (seq route-docs)]
    (apply deep-merge route route-docs)))

(defn- routes->swagger-object [route-table]
  (->> route-table
       (filter #(= ::swagger-object (:route-name %)))
       first
       :interceptors
       (keep (comp ::swagger-object meta))
       first))

(defn- prepare-swagger-object [swagger-object operations]
  (->> operations
       (group-by :path)
       (assoc swagger-object :paths)))

(defn generate-docs
  "Given a route table extracts the swagger documentation contained in
  its endpoints and builds a map that can be inspected after
  compilation. The swagger object response is located under
  ::swagger-object and each endpoint will have its own key containing
  the parameters and responses schemas for easy access during
  coercion/validation."
  [route-table]
  (let [operations (routes->operations route-table)
        swagger-object (routes->swagger-object route-table)]
    (prepare-swagger-object swagger-object operations)))
