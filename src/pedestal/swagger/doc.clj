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

(defn document-route [{:keys [interceptors] :as route}]
  (if-let [route-docs (seq (keep (comp ::doc meta) interceptors))]
    (with-meta route
      {::doc (apply deep-merge
                      (select-keys route [:path :method :route-name])
                      route-docs)})
    route))

(defn generate-swagger [route-table]
  (let [swagger-doc-route (first
                           (filter #(= ::swagger-object (:route-name %)) route-table))
        paths (->> route-table
                   (keep (comp ::doc meta))
                   (group-by :path))
        swagger-object (->> swagger-doc-route
                            :interceptors
                            (filter #(= ::swagger-object (:name %)))
                            first
                            meta
                            (merge {:paths paths}))]
    (for [{:keys [route-name] :as route} route-table]
      (if (= route-name ::swagger-object)
        (with-meta route swagger-object)
        route))))

(defn inject-docs
  [route-table]
  (->> route-table
       (map document-route)
       generate-swagger))

(defn swagger-object
  [injected-route-table]
  (first
   (for [{:keys [route-name] :as route} injected-route-table
         :when (= route-name ::swagger-object)]
     (meta route))))
