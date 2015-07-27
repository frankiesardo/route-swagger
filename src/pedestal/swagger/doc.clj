 (ns pedestal.swagger.doc
   (:require [ring.swagger.swagger2-schema :as spec]
             [schema.core :as s]))

(s/defn annotate
  "Attaches swagger documentation to an object"
  [doc :- spec/Operation, obj]
  (vary-meta obj assoc ::doc doc))

(def annotation
  "Gets documentation from an annotated object"
  (comp ::doc meta))

;;

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

;;

(defn- swagger-doc-route? [route]
  (when (= ::swagger-doc (:route-name route)) route))

(defn- find-docs [{:keys [interceptors]}]
  (keep annotation interceptors))

(defn- inject-swagger-into-routes [route-table swagger-object]
  (for [route route-table]
    (as-> route route
          (if (swagger-doc-route? route)
            (vary-meta route assoc ::swagger-doc swagger-object)
            route)
          (if-let [docs (seq (find-docs route))]
            (annotate (apply deep-merge docs) route)
            route))))

(defn- documented-handler? [route]
  (-> route :interceptors last annotation))

(s/defschema Paths
  {s/Str {s/Keyword spec/Operation}})

(s/defn gen-paths :- Paths
  "Generates swagger paths from an expanded route table.
  This function can also be used to generate documentation
  offline or for easy debugging (turning schema checks on)."
  [route-table]
  (apply merge-with merge
         (for [{:keys [path method] :as route} route-table
               :let [docs (find-docs route)]
               :when (documented-handler? route)]
           {path {method (apply deep-merge docs)}})))


(s/defn inject-docs
  "Attaches swagger information as a meta key to each documented
  route. The context passed to each interceptor has a reference to the
  selected route, so information like request and response schemas and
  the swagger object can be retrieved from its meta."
  [docs route-table]
  (let [swagger-object (deep-merge {:swagger "2.0"
                                    :info {:title "Swagger API"
                                           :version "0.0.1"}
                                    :paths (gen-paths route-table)}
                                   docs)]
    (inject-swagger-into-routes route-table swagger-object)))
