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

(defn- assoc-meta
  "Assoc a new key under the object meta rather than replacing the
  whole map."
  [obj key val]
  (with-meta obj (assoc (meta obj) key val)))

(defn- swagger-route? [route]
  (when (= ::swagger-object (:route-name route)) route))

(defn- swagger-interceptor? [interceptor]
  (when (= ::swagger-object (:name interceptor)) interceptor))

(defn- find-route-docs [{:keys [interceptors] :as route}]
  (keep (comp ::doc meta) interceptors))

(defn- merge-route-docs [route docs]
  (apply deep-merge (select-keys route [:path :method :route-name]) docs))

(defn- inject-swagger-into-routes [route-table swagger-object]
  (for [route route-table]
    (as-> route route
          (if (swagger-route? route)
            (assoc-meta route ::swagger-object swagger-object)
            route)
          (if-let [docs (find-route-docs route)]
            (assoc-meta route ::doc (merge-route-docs route docs))
            route))))

(defn swagger-object
  "Generates an edn swagger spec from an expanded route table. This
  function can also be used to generate a documentation offline or for
  easy debugging."
  [route-table]
  (let [info (->> route-table
                  (some swagger-route?)
                  :interceptors
                  (some swagger-interceptor?)
                  meta)
        paths (group-by :path
                        (for [route route-table
                              :let [docs (find-route-docs route)]
                              :when (seq docs)]
                          (merge-route-docs route docs)))]
    {:info info
     :paths paths}))


(defn inject-docs
  "Attaches swagger information as a meta key to each documented
  route. The context passed to each interceptor has a reference to the
  selected route, so information like request and response schemas and
  the swagger object can be retrieved from its meta."
  [route-table]
  (let [swagger-object (swagger-object route-table)]
    (inject-swagger-into-routes route-table swagger-object)))
