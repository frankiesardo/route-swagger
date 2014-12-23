(ns pedestal.swagger.doc
  (:require [io.pedestal.http.route :as route]))

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

(def ^:private deep-merge
  "Deep merge where the last colliding value overrides the others."
  (partial deep-merge-with (fn [& args] (last args))))

(defn- swagger-doc-route? [route]
  (when (= ::swagger-doc (:route-name route)) route))

(defn- find-docs [{:keys [interceptors] :as route}]
  (keep (comp ::doc meta) interceptors))

(defn- merge-docs [route docs]
  (apply deep-merge (select-keys route [:path :method :route-name]) docs))

(defn- inject-swagger-into-routes [route-table swagger-object]
  (for [route route-table]
    (as-> route route
          (if (swagger-doc-route? route)
            (vary-meta route assoc ::swagger-doc swagger-object)
            route)
          (if-let [docs (seq (find-docs route))]
            (vary-meta route assoc ::doc (merge-docs route docs))
            route))))

(defn- documented-handler? [route]
  (-> route :interceptors last meta ::doc))

(defn generate-paths
  "Generates swagger paths from an expanded route table. This
  function can also be used to generate a documentation offline or for
  easy debugging."
  [route-table]
  (group-by :path
            (for [route route-table
                  :let [docs (find-docs route)]
                  :when (documented-handler? route)]
              (merge-docs route docs))))


(defn inject-docs
  "Attaches swagger information as a meta key to each documented
  route. The context passed to each interceptor has a reference to the
  selected route, so information like request and response schemas and
  the swagger object can be retrieved from its meta."
  [doc route-table]
  (let [swagger-object {:info doc
                        :paths (generate-paths route-table)}]
    (inject-swagger-into-routes route-table swagger-object)))
